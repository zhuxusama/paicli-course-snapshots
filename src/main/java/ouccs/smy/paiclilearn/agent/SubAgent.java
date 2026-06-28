package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.prompt.PromptMode;
import ouccs.smy.paiclilearn.runtime.CancellationContext;  // [s13 新增]
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 子代理——代表团队中的一个角色执行任务。
 * <p>
 * [s12 新增] 每个 SubAgent 有独立的 conversationHistory、LLM 客户端和角色。
 * WORKER 角色在调用 LLM 时携带工具定义，PLANNER/REVIEWER 只做文本推理。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>角色决定 system prompt 内容——通过 PromptAssembler 选择对应的 TEAM_* 模式。</li>
 *   <li>角色决定是否注入工具——只有 WORKER 可以调用工具。</li>
 *   <li>独立的 conversationHistory 保证任务隔离——每个任务执行完后可以 clearHistory()。</li>
 *   <li>MAX_ITERATIONS=5 防止无限循环，超限返回 ERROR 消息。</li>
 * </ul>
 * </p>
 *
 * @since s12
 */
public class SubAgent {
    private final String name;
    private final AgentRole role;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final List<LlmClient.Message> history = new ArrayList<>();

    /**
     * 构造一个具有指定角色和 LLM 客户端的子代理。
     *
     * @param name         代理名称（如 "planner", "worker-1", "reviewer"）
     * @param role         代理角色——决定 system prompt 和是否携带工具
     * @param llmClient    共享的 LLM 客户端
     * @param toolRegistry 共享的工具注册表（WORKER 角色会用到）
     */
    public SubAgent(String name, AgentRole role, LlmClient llmClient, ToolRegistry toolRegistry) {
        this.name = name;
        this.role = role;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        // system prompt 通过 PromptAssembler 组装——s12 用角色描述构建基础提示词
        history.add(LlmClient.Message.system(buildSystemPrompt()));
    }

    /**
     * 构建角色对应的 system prompt。
     * <p>
     * [s12 教学版] 当前使用角色 displayName + description 构建基础提示词。
     * 后续章节接入 PromptAssembler 的 TEAM_PLANNER/TEAM_WORKER/TEAM_REVIEWER 模式后，
     * 会切换到 PromptRepository 加载的外部 prompt 文件。
     * </p>
     */
    private String buildSystemPrompt() {
        return "你是 Multi-Agent 团队中的 " + role.getDisplayName() + "（" + role.name() + "）。\n"
                + role.getDescription() + "\n"
                + (role == AgentRole.WORKER
                    ? "你可以调用工具来完成文件操作、命令执行等任务。请根据任务需求合理使用工具。\n"
                    : "请直接输出你的分析结果，不要尝试调用工具。\n");
    }

    /**
     * 执行任务，返回结果消息。
     * <p>
     * [s12 核心流程] 把任务内容注入 conversationHistory，
     * 然后走 ReAct 循环：LLM 推理 → 如果有工具调用则执行工具并回灌结果 → 继续推理。
     * WORKER 角色的 LLM 调用会携带工具定义列表。
     * </p>
     *
     * @param task 来自编排器的任务消息（content 为任务描述）
     * @return RESULT 消息（成功）或 ERROR 消息（异常/超限）
     */
    public AgentMessage execute(AgentMessage task) {
        // 把任务注入对话历史
        history.add(LlmClient.Message.user(task.content()));

        // [s13 新增] 取消检查
        if (CancellationContext.isCancelled()) {
            return AgentMessage.error(name, role, "任务已取消");
        }

        // 每次独立任务创建新预算，避免前一个任务的计数或停滞状态污染后一个任务。
        AgentBudget budget = new AgentBudget();
        try {
            while (true) {
                // [s13] 预算检查
                AgentBudget.ExitReason exitReason = budget.check();
                if (exitReason != AgentBudget.ExitReason.WITHIN_BUDGET) {
                    return AgentMessage.error(name, role, budget.describeExit(exitReason));
                }
                budget.beginIteration();
                // WORKER 有工具，PLANNER/REVIEWER 无
                var tools = role == AgentRole.WORKER
                        ? toolRegistry.getToolDefinitions()
                        : List.<LlmClient.Tool>of();

                var resp = llmClient.chat(
                        new ArrayList<>(history),
                        tools,
                        LlmClient.StreamListener.NO_OP);
                budget.recordTokens(resp.inputTokens(), resp.outputTokens(), resp.cachedInputTokens());

                // 推理后有工具调用：执行工具并把 assistant+tool 消息回灌历史
                if (resp.hasToolCalls()) {
                    budget.recordToolCalls(resp.toolCalls());
                    history.add(LlmClient.Message.assistant(
                            resp.reasoningContent(), resp.content(), resp.toolCalls()));
                    for (var tc : resp.toolCalls()) {
                        String r = toolRegistry.executeTool(tc.function().name(), tc.function().arguments());
                        history.add(LlmClient.Message.tool(tc.id(), r));
                    }
                    continue;
                }

                // 无工具调用：LLM 给出了最终回答
                history.add(LlmClient.Message.assistant(resp.content()));
                return AgentMessage.result(name, role, resp.content());
            }
        } catch (Exception e) {
            return AgentMessage.error(name, role, "LLM 调用或工具执行异常: " + e.getMessage());
        }
    }

    /**
     * 清空对话历史（保留 system prompt），用于处理下一个独立任务。
     * <p>
     * [s12] 编排器在每个规划/执行阶段结束后调用此方法，
     * 确保不同任务之间的上下文隔离。
     * </p>
     */
    public void clearHistory() {
        var systemMsg = history.get(0);
        history.clear();
        history.add(systemMsg);
    }

    public String getName() { return name; }
    public AgentRole getRole() { return role; }
}
