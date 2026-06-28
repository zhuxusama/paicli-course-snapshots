package ouccs.smy.paiclilearn.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmTraceLogger;
import ouccs.smy.paiclilearn.prompt.PromptAssembler;
import ouccs.smy.paiclilearn.prompt.PromptContext;
import ouccs.smy.paiclilearn.prompt.PromptMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * 计划生成器——将用户目标转换为可执行的 DAG 计划。
 *
 * <p>核心流程：用户目标 → 简单目标走单任务捷径 → 复杂目标调用 LLM
 * 生成 JSON 计划 → 两遍解析依赖 → 校验无环 → 返回可调度 DAG。</p>
 *
 * <p>直接移植自原项目 {@code com.paicli.plan.Planner}，仅调整课程包名。
 * 保留源码的 {@code PromptAssembler(PromptMode.PLANNER)}、
 * 流式推理渲染（s10 使用纯文本 PrintStream，s26 再接回终端样式）、
 * {@link #replan(ExecutionPlan, String)} 以及两遍依赖解析。</p>
 *
 * @since s10
 */
public class Planner {

    private static final Logger log = LoggerFactory.getLogger(Planner.class);

    private final LlmClient llmClient;
    private final PrintStream out;
    private final ObjectMapper mapper = new ObjectMapper();
    private final PromptAssembler promptAssembler = PromptAssembler.createDefault();

    public Planner(LlmClient llmClient) {
        this(llmClient, System.out);
    }

    public Planner(LlmClient llmClient, PrintStream out) {
        this.llmClient = llmClient;
        this.out = out == null ? System.out : out;
    }

    /**
     * 为用户目标创建执行计划。
     * 简单目标（如"列出文件"）走本地单任务捷径，复杂目标通过 LLM 拆解为 DAG。
     *
     * @param goal 用户目标
     * @return 解析后的 ExecutionPlan
     * @throws IOException LLM 调用或 JSON 解析失败
     */
    public ExecutionPlan createPlan(String goal) throws IOException {
        out.println("📋 正在规划任务: " + goal + "\n");

        if (isSimpleGoal(goal)) {
            return createMinimalPlan(goal);
        }

        // 使用 PromptAssembler 组装 PLANNER 模式 system prompt
        List<LlmClient.Message> messages = Arrays.asList(
                LlmClient.Message.system(
                        promptAssembler.assemble(PromptMode.PLANNER, PromptContext.empty())),
                LlmClient.Message.user("请为以下任务制定执行计划：\n" + goal)
        );

        // 调用 LLM 并流式渲染 reasoning
        PlanningStreamRenderer streamRenderer = new PlanningStreamRenderer(out);
        LlmClient.ChatResponse response = llmClient.chat(messages, null, streamRenderer);
        LlmTraceLogger.logReasoning(log, "planner", llmClient, response.reasoningContent());
        streamRenderer.finish();
        String planJson = response.content();

        // 解析 JSON 计划为 DAG
        return parsePlan(goal, planJson);
    }

    /**
     * 根据执行结果重新规划。
     * 将原目标、失败原因和已完成任务注入新规划上下文。
     *
     * @param failedPlan    执行失败的计划
     * @param failureReason 失败原因说明
     * @return 重新生成的 ExecutionPlan
     * @throws IOException LLM 调用或解析失败
     */
    public ExecutionPlan replan(ExecutionPlan failedPlan, String failureReason) throws IOException {
        out.println("🔄 重新规划，原因: " + failureReason + "\n");

        StringBuilder context = new StringBuilder();
        context.append("原任务: ").append(failedPlan.getGoal()).append("\n");
        context.append("失败原因: ").append(failureReason).append("\n");
        context.append("已完成的任务:\n");

        for (Task task : failedPlan.getAllTasks()) {
            if (task.getStatus() == Task.TaskStatus.COMPLETED) {
                context.append("- ").append(task.getId())
                        .append(": ").append(task.getDescription())
                        .append("\n");
            }
        }

        context.append("\n请制定新的执行计划，避开之前的问题。");
        return createPlan(context.toString());
    }

    // ========== 目标分类 ==========

    /**
     * 判断是否为简单目标——中文多步提示词检测 + 长度阈值 + 简单操作模式匹配。
     * 空目标不走简单任务捷径。
     */
    private boolean isSimpleGoal(String goal) {
        if (goal == null) {
            return false;
        }

        String normalized = goal.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        // 中文多步提示词 → 需要 LLM 拆解
        boolean hasMultiStepCue = normalized.contains("然后")
                || normalized.contains("并且")
                || normalized.contains("并")
                || normalized.contains("再")
                || normalized.contains("最后")
                || normalized.contains("同时")
                || normalized.contains("先")
                || normalized.contains("之后")
                || normalized.contains("接着")
                || normalized.contains("以及");
        if (hasMultiStepCue) {
            return false;
        }

        // 较长目标 → 可能需要 LLM 拆解
        if (normalized.length() > 30) {
            return false;
        }

        // 简单操作模式匹配
        return normalized.contains("列出")
                || normalized.contains("查看")
                || normalized.contains("读取")
                || normalized.contains("显示")
                || normalized.contains("执行")
                || normalized.contains("运行")
                || normalized.contains("搜索")
                || normalized.contains("当前目录")
                || normalized.contains("文件");
    }

    /**
     * 为简单目标创建单任务计划（无需 LLM）。
     */
    private ExecutionPlan createMinimalPlan(String goal) {
        ExecutionPlan plan = new ExecutionPlan(generatePlanId(), goal);
        plan.setSummary(buildMinimalSummary(goal));
        plan.addTask(new Task("task_1", goal.trim(), inferSimpleTaskType(goal)));
        if (!plan.computeExecutionOrder()) {
            throw new IllegalStateException("简单计划不应出现循环依赖");
        }
        return plan;
    }

    private String buildMinimalSummary(String goal) {
        String normalized = goal == null ? "" : goal.trim();
        if (normalized.isEmpty()) {
            return "执行简单任务";
        }
        return "直接执行简单任务：" + normalized;
    }

    /** 从目标文本推断简单任务类型。 */
    private Task.TaskType inferSimpleTaskType(String goal) {
        String normalized = goal == null ? "" : goal.trim();
        if (normalized.contains("读取") || normalized.contains("打开") || normalized.contains("查看")
                && normalized.contains("文件")) {
            return Task.TaskType.FILE_READ;
        }
        if (normalized.contains("写入") || normalized.contains("修改") || normalized.contains("创建文件")) {
            return Task.TaskType.FILE_WRITE;
        }
        if (normalized.contains("分析") || normalized.contains("总结") || normalized.contains("解释")) {
            return Task.TaskType.ANALYSIS;
        }
        if (normalized.contains("验证") || normalized.contains("检查")) {
            return Task.TaskType.VERIFICATION;
        }
        return Task.TaskType.COMMAND;
    }

    // ========== 计划解析 ==========

    /**
     * 将 LLM 返回的 JSON 解析为 ExecutionPlan。
     *
     * <p>两遍解析：第一遍在 idMapping 中注册所有 ID 映射并创建 Task 节点；
     * 第二遍遍历依赖建立双向引用（addDependency + addDependent），
     * 确保即使 LLM 返回的 JSON 有前向引用也能正确建立依赖链。</p>
     *
     * @param goal    用户目标
     * @param planJson LLM 返回的 JSON 字符串（可能含 markdown 代码块标记）
     * @return 校验后的无环 ExecutionPlan
     * @throws IOException JSON 解析失败或存在循环依赖
     */
    public ExecutionPlan parsePlan(String goal, String planJson) throws IOException {
        // 去除 markdown 代码块标记
        String cleaned = planJson.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        JsonNode root = mapper.readTree(cleaned);
        String summary = root.path("summary").asText();
        JsonNode tasksNode = root.path("tasks");

        ExecutionPlan plan = new ExecutionPlan(generatePlanId(), goal);
        plan.setSummary(summary);

        // 第一遍：创建所有 Task（暂不处理依赖，防范前向引用）
        Map<String, String> idMapping = new HashMap<>();
        int taskIndex = 1;

        for (JsonNode taskNode : tasksNode) {
            String originalId = taskNode.path("id").asText();
            String newId = "task_" + taskIndex++;
            idMapping.put(originalId, newId);

            String description = taskNode.path("description").asText();
            String typeStr = taskNode.path("type").asText();
            Task.TaskType type = parseTaskType(typeStr);

            plan.addTask(new Task(newId, description, type));
        }

        // 第二遍：建立依赖和被依赖关系
        taskIndex = 1;
        for (JsonNode taskNode : tasksNode) {
            String newId = "task_" + taskIndex++;
            Task task = plan.getTask(newId);

            JsonNode depsNode = taskNode.path("dependencies");
            if (depsNode.isArray()) {
                for (JsonNode depNode : depsNode) {
                    String originalDepId = depNode.asText();
                    String newDepId = idMapping.getOrDefault(originalDepId, originalDepId);
                    Task dep = plan.getTask(newDepId);
                    if (dep != null) {
                        task.addDependency(newDepId);
                        dep.addDependent(task.getId());
                    }
                }
            }
        }

        // 校验无环
        if (!plan.computeExecutionOrder()) {
            throw new IOException("计划中存在循环依赖");
        }

        return plan;
    }

    private Task.TaskType parseTaskType(String typeStr) {
        return switch (typeStr.toUpperCase()) {
            case "FILE_READ" -> Task.TaskType.FILE_READ;
            case "FILE_WRITE" -> Task.TaskType.FILE_WRITE;
            case "COMMAND" -> Task.TaskType.COMMAND;
            case "ANALYSIS" -> Task.TaskType.ANALYSIS;
            case "VERIFICATION" -> Task.TaskType.VERIFICATION;
            default -> Task.TaskType.ANALYSIS;
        };
    }

    // ========== 内部工具 ==========

    private String generatePlanId() {
        return "plan_" + System.currentTimeMillis();
    }

    // ========== 流式渲染 ==========

    /**
     * 规划专用流式推理渲染器。
     *
     * <p>s10 使用纯文本 PrintStream 输出 thinking 内容；
     * s26 将迁移到 {@code AnsiStyle.heading()} 与
     * {@code TerminalMarkdownRenderer} 实现终端样式和 Markdown 渲染。</p>
     */
    private static final class PlanningStreamRenderer implements LlmClient.StreamListener {
        private final PrintStream out;
        private boolean reasoningStarted;
        private boolean streamed;

        private PlanningStreamRenderer(PrintStream out) {
            this.out = out == null ? System.out : out;
        }

        @Override
        public void onReasoningDelta(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            if (!reasoningStarted) {
                out.println("🧠 规划思考");
                reasoningStarted = true;
                streamed = true;
            }
            out.print(delta);
            out.flush();
        }

        void finish() {
            if (streamed) {
                out.println("\n");
            }
        }
    }
}
