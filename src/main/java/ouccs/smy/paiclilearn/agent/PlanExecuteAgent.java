package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.cli.PlanReviewInputParser;
import ouccs.smy.paiclilearn.cli.PlanReviewInputParser.Decision;
import ouccs.smy.paiclilearn.cli.PlanReviewInputParser.DecisionType;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmTraceLogger;
import ouccs.smy.paiclilearn.memory.ConversationHistoryCompactor;
import ouccs.smy.paiclilearn.memory.MemoryManager;
import ouccs.smy.paiclilearn.plan.ExecutionPlan;
import ouccs.smy.paiclilearn.plan.Planner;
import ouccs.smy.paiclilearn.plan.Task;
import ouccs.smy.paiclilearn.prompt.PromptAssembler;
import ouccs.smy.paiclilearn.prompt.PromptContext;
import ouccs.smy.paiclilearn.prompt.PromptMode;
import ouccs.smy.paiclilearn.tool.ToolRegistry;
import ouccs.smy.paiclilearn.tool.ToolRegistry.ToolExecutionResult;
import ouccs.smy.paiclilearn.tool.ToolRegistry.ToolInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * Plan-and-Execute Agent——先规划，审阅，再按拓扑序逐任务执行。
 *
 * <p>核心路径：用户输入 → Planner 生成 DAG → 审阅循环（执行/补充/取消）
 * → 拓扑序取可执行任务 → 单任务多轮工具调用 → 失败时若进度 &lt; 50% 则 replan。</p>
 *
 * <p>s11 边界内简化：
 * <ul>
 *   <li>单任务顺序执行（并行批次延至 s12）</li>
 *   <li>纯文本 PrintStream 输出（终端样式延至 s26）</li>
 *   <li>不依赖 skill/lsp/image/cancellation（延至对应章节）</li>
 * </ul></p>
 *
 * @since s11
 */
public class PlanExecuteAgent {

    private static final Logger log = LoggerFactory.getLogger(PlanExecuteAgent.class);
    private static final int MAX_TASK_ITERATIONS = 5;
    private static final int MAX_REPLAN_COUNT = 3;

    // ========== 审阅接口 ==========

    /** 计划审阅——DAG 生成后、执行前回调。 */
    @FunctionalInterface
    public interface PlanReviewHandler {
        Decision review(String goal, ExecutionPlan plan);
    }

    // ========== 组件 ==========

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final Planner planner;
    private final PlanReviewHandler reviewHandler;
    private final MemoryManager memoryManager;
    private final ConversationHistoryCompactor historyCompactor;
    private final PromptAssembler promptAssembler = PromptAssembler.createDefault();
    private final PrintStream out;

    // ========== 构造 ==========

    public PlanExecuteAgent(LlmClient llmClient) {
        this(llmClient, (g, p) -> Decision.execute());
    }

    public PlanExecuteAgent(LlmClient llmClient, PlanReviewHandler reviewHandler) {
        this(llmClient, new ToolRegistry(), null, reviewHandler);
    }

    public PlanExecuteAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                            MemoryManager memoryManager, PlanReviewHandler reviewHandler) {
        this(llmClient, toolRegistry, memoryManager, reviewHandler, System.out);
    }

    public PlanExecuteAgent(LlmClient llmClient, ToolRegistry toolRegistry,
                            MemoryManager memoryManager, PlanReviewHandler reviewHandler,
                            PrintStream out) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        this.out = out != null ? out : System.out;
        this.planner = new Planner(llmClient, this.out);
        this.reviewHandler = reviewHandler != null ? reviewHandler : (g, p) -> Decision.execute();
        this.memoryManager = memoryManager != null ? memoryManager : MemoryManager.inMemory(llmClient);
        this.historyCompactor = new ConversationHistoryCompactor(llmClient);
    }

    // ========== 主入口 ==========

    /**
     * 运行用户输入——计划生成 → 审阅 → 执行闭环。
     */
    public String run(String userInput) {
        log.info("Plan run: inputLen={}", userInput == null ? 0 : userInput.length());
        memoryManager.addUserMessage(userInput);
        try {
            ExecutionPlan plan = planner.createPlan(userInput);
            int replanCount = 0;

            while (true) {
                Decision d = reviewHandler.review(plan.getGoal(), plan);
                if (d.type() == DecisionType.CANCEL) {
                    return "⏹ 已取消";
                }
                if (d.type() == DecisionType.EXECUTE) {
                    String result = executePlan(plan);
                    if (result != null && !result.isBlank()) {
                        memoryManager.addAssistantMessage("[计划] " + result);
                    }
                    return result;
                }
                // SUPPLEMENT
                replanCount++;
                if (replanCount > MAX_REPLAN_COUNT) {
                    return "⚠ 重规划次数超限（" + MAX_REPLAN_COUNT + "），请明确需求后重试";
                }
                String fb = d.feedback() == null ? "" : d.feedback().trim();
                if (fb.isEmpty()) return executePlan(plan);
                out.println("📝 已收到补充要求，重新规划...\n");
                plan = planner.createPlan(plan.getGoal() + "\n补充：" + fb);
            }
        } catch (Exception e) {
            log.error("Plan run failed", e);
            String msg = "❌ 执行失败: " + e.getMessage();
            memoryManager.addAssistantMessage(msg);
            return msg;
        }
    }

    // ========== 计划执行 ==========

    private String executePlan(ExecutionPlan plan) throws IOException {
        log.info("Execute plan: '{}' tasks={}", plan.getGoal(), plan.getAllTasks().size());
        out.println("🚀 开始执行计划...\n");
        plan.markStarted();

        Set<String> completed = new HashSet<>();
        while (true) {
            List<Task> exec = plan.getExecutableTasks().stream()
                    .sorted(Comparator.comparingInt(
                            t -> plan.getExecutionOrder().indexOf(t.getId())))
                    .toList();

            if (exec.isEmpty()) {
                boolean allDone = plan.getAllTasks().stream()
                        .allMatch(t -> t.getStatus() == Task.TaskStatus.COMPLETED
                                || t.getStatus() == Task.TaskStatus.SKIPPED);
                if (allDone) break;
                plan.markFailed();
                return "⚠ 计划死锁：存在未满足依赖的任务";
            }

            for (Task task : exec) {
                out.println("▶ 执行 [" + task.getId() + "]: " + task.getDescription());
                task.markStarted();
                try {
                    String r = executeTask(plan.getGoal(), plan, task);
                    task.markCompleted(r);
                    completed.add(task.getId());
                    out.println("✅ 完成 [" + task.getId() + "]: "
                            + preview(r, 100) + "\n");
                } catch (Exception e) {
                    task.markFailed(e.getMessage());
                    out.println("❌ 失败 [" + task.getId() + "]: "
                            + e.getMessage() + "\n");
                    if (plan.getProgress() < 0.5) {
                        out.println("🔄 尝试重新规划...\n");
                        return executePlan(planner.replan(plan, e.getMessage()));
                    }
                    return "⚠ 任务 " + task.getId() + " 失败: " + e.getMessage();
                }
            }
        }

        plan.markCompleted();
        return buildFinalResult(plan, completed);
    }

    // ========== 单任务 ==========

    private String executeTask(String goal, ExecutionPlan plan, Task task)
            throws IOException {
        String sys = promptAssembler.assemble(PromptMode.PLAN,
                PromptContext.builder()
                        .variable("taskType", task.getType().toString())
                        .variable("taskDescription", task.getDescription())
                        .variable("goal", goal)
                        .build());

        String ctx = buildTaskContext(goal, plan, task);

        List<LlmClient.Message> msgs = new ArrayList<>(Arrays.asList(
                LlmClient.Message.system(sys),
                LlmClient.Message.user(ctx)));

        StringBuilder all = new StringBuilder();
        for (int i = 0; i < MAX_TASK_ITERATIONS; i++) {
            maybeCompact(msgs);

            LlmClient.ChatResponse resp = llmClient.chat(
                    msgs, toolRegistry.getToolDefinitions(), null);
            LlmTraceLogger.logReasoning(log,
                    "plan-task " + task.getId() + " iter=" + (i + 1),
                    llmClient, resp.reasoningContent());

            memoryManager.recordTokenUsage(
                    resp.inputTokens(), resp.outputTokens(),
                    resp.cachedInputTokens());

            if (!resp.hasToolCalls()) {
                if (all.length() > 0
                        && (resp.content() == null || resp.content().isBlank()))
                    return all.toString().trim();
                return resp.content();
            }

            printToolCalls(resp.toolCalls());
            msgs.add(LlmClient.Message.assistant(
                    resp.reasoningContent(), resp.content(), resp.toolCalls()));

            List<ToolInvocation> invs = toolRegistry.convertToolCalls(
                    resp.toolCalls());
            List<ToolExecutionResult> results = toolRegistry.executeTools(invs);
            for (ToolExecutionResult r : results) {
                memoryManager.addToolResult(r.result());
                all.append(r.result()).append("\n");
                msgs.add(LlmClient.Message.tool(r.id(), r.result()));
            }
        }
        return all.toString().trim();
    }

    // ========== 上下文 ==========

    private String buildTaskContext(String goal, ExecutionPlan plan, Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("总目标：").append(goal).append("\n");
        sb.append("当前任务：").append(task.getDescription()).append("\n");
        List<String> deps = task.getDependencies();
        if (deps.isEmpty()) {
            sb.append("依赖：无\n");
        } else {
            sb.append("依赖任务结果：\n");
            for (String depId : deps) {
                Task d = plan.getTask(depId);
                if (d == null) continue;
                sb.append("- ").append(d.getId()).append(" / ")
                        .append(d.getDescription()).append(" / ")
                        .append(d.getStatus()).append("\n");
                if (d.getResult() != null && !d.getResult().isBlank())
                    sb.append(d.getResult()).append("\n");
            }
        }
        sb.append("请执行此任务。ANALYSIS/VERIFICATION 类型请直接给结果。");
        return sb.toString();
    }

    private String buildFinalResult(ExecutionPlan plan, Set<String> completed) {
        StringBuilder sb = new StringBuilder();
        for (Task t : plan.getAllTasks()) {
            if (!completed.contains(t.getId())) continue;
            if (t.getDependents().isEmpty()
                    && t.getResult() != null && !t.getResult().isBlank()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("[").append(t.getId()).append("] ").append(t.getResult());
            }
        }
        if (sb.length() > 0) return sb.toString();
        return plan.getAllTasks().stream()
                .filter(t -> t.getResult() != null && !t.getResult().isBlank())
                .reduce((a, b) -> b).map(Task::getResult)
                .orElse("计划执行完成");
    }

    // ========== 辅助 ==========

    private void maybeCompact(List<LlmClient.Message> msgs) {
        if (historyCompactor == null) return;
        try {
            int trigger = memoryManager.getTokenBudget().getAvailableForConversation();
            historyCompactor.compactIfNeeded(msgs, trigger);
        } catch (Exception e) {
            log.warn("Compact failed", e);
        }
    }

    private void printToolCalls(List<LlmClient.ToolCall> calls) {
        Map<String, Integer> cnt = new LinkedHashMap<>();
        for (LlmClient.ToolCall tc : calls) {
            cnt.merge(tc.function().name(), 1, Integer::sum);
        }
        cnt.forEach((name, n) -> out.println("  " + toolLabel(name, n)));
    }

    private static String toolLabel(String name, int n) {
        return switch (name) {
            case "read_file" -> "📖 读取 " + n + " 个文件";
            case "write_file" -> "✏ 写入 " + n + " 个文件";
            case "list_dir" -> "📂 列出 " + n + " 个目录";
            case "execute_command" -> "⚡ 执行 " + n + " 条命令";
            case "create_project" -> "🏗 创建 " + n + " 个项目";
            case "search_code" -> "🔍 搜索 " + n + " 次";
            case "save_memory" -> "💾 记忆 " + n + " 条";
            default -> "🔧 " + name + " × " + n;
        };
    }

    private static String preview(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        String n = s.replace("\r\n", "\n").replace('\r', '\n').trim();
        return n.length() <= max ? n : n.substring(0, max) + "...";
    }
}
