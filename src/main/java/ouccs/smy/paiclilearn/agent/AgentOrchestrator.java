package ouccs.smy.paiclilearn.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import ouccs.smy.paiclilearn.runtime.CancellationContext;  // [s13 新增]
import java.util.*;
import java.util.concurrent.*;

/**
 * Agent 编排器——Multi-Agent 系统的"指挥中心"。
 * <p>
 * [s12 新增] 采用 Planner→Worker→Reviewer 三角协作模式：
 * <ol>
 *   <li>规划阶段：Planner 将用户目标拆解为 JSON 执行计划</li>
 *   <li>解析阶段：编排器将 JSON 解析为 ExecutionStep 列表</li>
 *   <li>执行阶段：按依赖拓扑序，每轮取 ready 步骤并行执行</li>
 *   <li>审核阶段：Reviewer 检查 Worker 结果，不通过则重试（最多 1 次）</li>
 * </ol>
 * </p>
 * <p>
 * 并行策略：
 * <ul>
 *   <li>同一批次内无依赖关系的步骤并行执行（最多 4 线程）</li>
 *   <li>每轮执行完成后检查依赖，释放新的 ready 步骤</li>
 *   <li>每步最多 60 秒超时</li>
 * </ul>
 * </p>
 *
 * @since s12
 */
public class AgentOrchestrator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PARALLEL = 4;
    private static final int STEP_TIMEOUT_SECONDS = 60;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final SubAgent planner;
    private final List<SubAgent> workers;
    private final SubAgent reviewer;

    /**
     * 构造编排器，初始化一个 Planner、两个 Worker、一个 Reviewer。
     * <p>
     * [s12] 所有 SubAgent 共享同一个 LlmClient 和 ToolRegistry。
     * Planner/Reviewer 不需要工具（SubAgent.execute 内部会根据角色过滤）。
     * </p>
     *
     * @param llmClient    共享的 LLM 客户端
     * @param toolRegistry 共享的工具注册表
     */
    public AgentOrchestrator(LlmClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.planner = new SubAgent("planner", AgentRole.PLANNER, llmClient, toolRegistry);
        this.workers = List.of(
                new SubAgent("worker-1", AgentRole.WORKER, llmClient, toolRegistry),
                new SubAgent("worker-2", AgentRole.WORKER, llmClient, toolRegistry));
        this.reviewer = new SubAgent("reviewer", AgentRole.REVIEWER, llmClient, toolRegistry);
    }

    /**
     * 运行多 Agent 协作任务。
     * <p>
     * [s12 主流程] 规划 → 解析 → 并行执行 + 审核 → 汇总结果。
     * 整个流程是同步的：调用者等待所有步骤完成。
     * </p>
     *
     * @param goal 用户的自然语言目标
     * @return 格式化的执行结果汇总
     */
    public String run(String goal) {
        // [s13 新增] 取消检查——在规划阶段前确认未被 /cancel
        if (CancellationContext.isCancelled()) {
            return "⏹️ 已取消当前多 Agent 任务。";
        }
        // 第一阶段：规划
        AgentMessage planResult = planner.execute(
                AgentMessage.task("orchestrator", "请为以下任务制定执行计划，输出 JSON：\n" + goal));
        planner.clearHistory();

        if (planResult.type() == AgentMessage.Type.ERROR) {
            return "❌ 规划阶段失败: " + planResult.content();
        }

        // 第二阶段：解析计划
        List<ExecutionStep> steps = parseSteps(planResult.content());
        if (steps.isEmpty()) {
            return "❌ 规划未生成有效步骤。原始输出:\n" + planResult.content();
        }

        // 第三阶段：执行 + 审核
        return executeSteps(steps);
    }

    /**
     * 从 Planner 的 JSON 输出中解析出执行步骤列表。
     * <p>
     * [s12] 支持 "tasks" 和 "steps" 两种 JSON 键名。
     * 每个步骤必须有 id/description，type 和 dependencies 可选。
     * </p>
     *
     * @param planJson Planner 输出的 JSON（可能包含 markdown 代码块包裹）
     * @return 解析后的步骤列表；解析失败时返回空列表
     */
    public List<ExecutionStep> parseSteps(String planJson) {
        try {
            // 去掉可能的 markdown 代码块包裹
            String json = planJson.replaceAll("```(?:json)?", "").trim();
            var root = MAPPER.readTree(json);
            var tasks = root.has("tasks") ? root.path("tasks") : root.path("steps");

            // 第一步：收集原始 id → 规范化 id 的映射
            Map<String, String> idMap = new LinkedHashMap<>();
            List<ExecutionStep> steps = new ArrayList<>();
            int idx = 0;
            if (tasks.isArray()) {
                for (var t : tasks) {
                    idx++;
                    idMap.put(t.path("id").asText(String.valueOf(idx)), "step_" + idx);
                }
                // 第二步：构造 ExecutionStep，解析依赖引用
                idx = 0;
                for (var t : tasks) {
                    idx++;
                    List<String> deps = new ArrayList<>();
                    if (t.has("dependencies") && t.path("dependencies").isArray()) {
                        for (var d : t.path("dependencies")) {
                            String mapped = idMap.get(d.asText());
                            if (mapped != null) deps.add(mapped);
                        }
                    }
                    steps.add(new ExecutionStep(
                            "step_" + idx,
                            t.path("description").asText(""),
                            t.path("type").asText("ANALYSIS"),
                            deps));
                }
            }
            return steps;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 按依赖拓扑序并行执行步骤，每步完成后 Reviewer 审核。
     * <p>
     * [s12 核心循环]
     * <ol>
     *   <li>取所有状态为 PENDING 且依赖已满足的步骤作为 ready 批次</li>
     *   <li>用线程池并行执行 ready 步骤（最多 MAX_PARALLEL 线程）</li>
     *   <li>每个 Worker 执行完 → Reviewer 审核 → 不通过则重试 1 次</li>
     *   <li>重复直到所有步骤都不是 PENDING</li>
     * </ol>
     * </p>
     */
    private String executeSteps(List<ExecutionStep> steps) {
        StringBuilder result = new StringBuilder();

        while (true) {
            List<ExecutionStep> ready = getReadySteps(steps);
            if (ready.isEmpty()) break;

            int threads = Math.min(ready.size(), MAX_PARALLEL);
            ExecutorService exec = Executors.newFixedThreadPool(threads);
            try {
                List<Future<StepResult>> futures = new ArrayList<>();
                for (var step : ready) {
                    SubAgent worker = workers.get(futures.size() % workers.size());
                    futures.add(exec.submit(() -> executeOneStep(step, worker)));
                }

                // 收集结果，按原始顺序
                for (int i = 0; i < futures.size(); i++) {
                    try {
                        StepResult sr = futures.get(i).get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        // 更新步骤状态
                        for (var s : steps) {
                            if (s.id.equals(sr.id)) {
                                s.status = sr.status;
                                s.result = sr.result;
                            }
                        }
                        result.append("[").append(sr.id).append("] ")
                                .append(sr.status).append(" ")
                                .append(sr.result).append("\n");
                    } catch (TimeoutException e) {
                        var step = ready.get(i);
                        result.append("[").append(step.id).append("] TIMED_OUT\n");
                        step.status = "FAILED";
                        step.result = "执行超时";
                    } catch (Exception e) {
                        var step = ready.get(i);
                        result.append("[").append(step.id).append("] ERROR: ")
                                .append(e.getMessage()).append("\n");
                    }
                }
            } catch (Exception e) {
                result.append("并行执行异常: ").append(e.getMessage()).append("\n");
            } finally {
                exec.shutdownNow();
            }
        }

        // 标记因依赖未满足而跳过的步骤
        for (var s : steps) {
            if ("PENDING".equals(s.status)) {
                s.status = "SKIPPED";
                s.result = "依赖未满足";
            }
        }
        return result.toString();
    }

    /**
     * 执行单个步骤：Worker 执行 → Reviewer 审核 → 不通过则重试 1 次。
     */
    private StepResult executeOneStep(ExecutionStep step, SubAgent worker) {
        // Worker 执行
        AgentMessage output = worker.execute(
                AgentMessage.task(planner.getName(), step.description));
        worker.clearHistory();

        if (output.type() == AgentMessage.Type.ERROR) {
            return new StepResult(step.id, "FAILED", output.content(), false);
        }

        // Reviewer 审核
        AgentMessage review = reviewer.execute(
                AgentMessage.task("orchestrator",
                        "请审核以下步骤的执行结果，以 JSON 格式输出：{\"approved\": true/false, \"summary\": \"...\"}\n\n"
                                + "步骤: " + step.description + "\n结果: " + output.content()));
        reviewer.clearHistory();

        boolean approved = isApproved(review.content());

        if (!approved) {
            // 重试 1 次
            AgentMessage retryOutput = worker.execute(
                    AgentMessage.task(planner.getName(),
                            "修正以下步骤，上次的问题反馈: " + review.content()
                                    + "\n原始任务: " + step.description));
            worker.clearHistory();

            AgentMessage reReview = reviewer.execute(
                    AgentMessage.task("orchestrator",
                            "请重新审核修正后的结果，以 JSON 格式输出：{\"approved\": true/false, \"summary\": \"...\"}\n\n"
                                    + "步骤: " + step.description + "\n修正结果: " + retryOutput.content()));
            reviewer.clearHistory();

            boolean reApproved = isApproved(reReview.content());
            if (reApproved) {
                return new StepResult(step.id, "COMPLETED", retryOutput.content(), true);
            }
            return new StepResult(step.id, "FAILED",
                    "审核不通过（重试后仍失败）: " + reReview.content(), false);
        }

        return new StepResult(step.id, "COMPLETED", output.content(), true);
    }

    /**
     * 尝试从 Reviewer 的输出中判断是否审批通过。
     * <p>
     * [s12 教学版] 先尝试 JSON 解析 {"approved": true}；
     * 解析失败则 fallback 到包含 "approved" 关键词（不区分大小写）的简单匹配。
     * </p>
     */
    private static boolean isApproved(String reviewerOutput) {
        if (reviewerOutput == null) return false;
        try {
            // 尝试 JSON 解析
            String json = reviewerOutput.replaceAll("```(?:json)?", "").trim();
            var node = MAPPER.readTree(json);
            if (node.has("approved")) {
                return node.get("approved").asBoolean(false);
            }
        } catch (Exception ignored) {
            // fallback 到字符串匹配
        }
        return reviewerOutput.toLowerCase().contains("approved");
    }

    /**
     * 获取当前批次可执行的步骤——状态为 PENDING 且所有依赖已完成。
     */
    private List<ExecutionStep> getReadySteps(List<ExecutionStep> steps) {
        Set<String> completed = new HashSet<>();
        for (var s : steps) {
            if ("COMPLETED".equals(s.status)) completed.add(s.id);
        }
        List<ExecutionStep> ready = new ArrayList<>();
        for (var s : steps) {
            if ("PENDING".equals(s.status) && completed.containsAll(s.dependencies)) {
                ready.add(s);
            }
        }
        return ready;
    }

    /**
     * 一个执行计划中的步骤。
     * <p>
     * [s12] 由 Planner 生成 JSON → parseSteps() 解析为此类实例。
     * 状态机：PENDING → RUNNING → COMPLETED/FAILED。
     * </p>
     */
    public static class ExecutionStep {
        public final String id;
        public final String description;
        public final String type;
        public final List<String> dependencies;
        public String status = "PENDING";
        public String result;

        ExecutionStep(String id, String desc, String type, List<String> deps) {
            this.id = id;
            this.description = desc;
            this.type = type;
            this.dependencies = new ArrayList<>(deps);
        }
    }

    /** 并行执行结果包装——由 executeOneStep 返回。 */
    private record StepResult(String id, String status, String result, boolean reviewed) {}
}
