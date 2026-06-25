package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.ExplicitMemoryHints;
import ouccs.smy.paiclilearn.context.ContextProfile;
import ouccs.smy.paiclilearn.context.TokenUsageFormatter;
import ouccs.smy.paiclilearn.memory.ConversationHistoryCompactor;
import ouccs.smy.paiclilearn.memory.ConversationMemory;
import ouccs.smy.paiclilearn.memory.MemoryManager;
import ouccs.smy.paiclilearn.memory.TokenBudget;
import ouccs.smy.paiclilearn.prompt.PromptAssembler;
import ouccs.smy.paiclilearn.prompt.PromptContext;
import ouccs.smy.paiclilearn.prompt.PromptMode;
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 最小化 ReAct 推理-工具-观察循环。
 * <p>
 * 本章（s03）实现原项目 Agent 的核心消息循环骨架：
 * <pre>
 * user input → 追加 user message → LLM chat（携带历史）
 *   → 若返回 tool calls → 追加 assistant msg → 执行工具 → 追加 tool msg → 继续
 *   → 若返回 content   → 追加 assistant msg → 返回最终回答
 * </pre>
 * s07 已接入分层 prompt；后续章节继续加入记忆、预算、扩展、渲染和取消能力。
 * </p>
 *
 * @since s03
 */
public class Agent {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final PromptAssembler promptAssembler;
    private final PromptContext promptContext;
    private final MemoryManager memoryManager;
    // [s09 新增] Token 预算、压缩器、配置画像
    private TokenBudget tokenBudget;
    private ContextProfile contextProfile;
    private ConversationHistoryCompactor compactor;
    private long startNanos;
    private final List<LlmClient.Message> conversationHistory = new ArrayList<>();

    /** [s05 新增] HITL 审批链；为 null 时副作用工具由 ToolRegistry 直接执行（无审批保护）。 */
    private ouccs.smy.paiclilearn.hitl.HitlToolRegistry hitlRegistry;

    /** 用户传入的流式输出监听器。不可为 null；默认为 NO_OP。 */
    private LlmClient.StreamListener userStreamListener = LlmClient.StreamListener.NO_OP;

    /** 最大 ReAct 循环迭代数，超过即返回错误。 */
    static final int MAX_ITERATIONS = 10;

    /**
     * 使用默认 ToolRegistry 构造 Agent。
     * @param llmClient 真实模型客户端，不可为 null
     */
    public Agent(LlmClient llmClient) {
        this(llmClient, new ToolRegistry());
    }

    /**
     * 完全构造 Agent。
     * @param llmClient   真实模型客户端，不可为 null
     * @param toolRegistry 工具注册中心；s03 起逐章增加真实工具
     */
    public Agent(LlmClient llmClient, ToolRegistry toolRegistry) {
        this(llmClient, toolRegistry, PromptAssembler.createDefault(), PromptContext.empty());
    }

    /** 生产入口：使用持久化 MemoryManager 和默认 prompt 组件。 */
    public Agent(LlmClient llmClient, ToolRegistry toolRegistry, MemoryManager memoryManager) {
        this(llmClient, toolRegistry, PromptAssembler.createDefault(), PromptContext.empty(), memoryManager);
    }

    /**
     * 使用可注入的 prompt 组件构造 Agent，便于项目覆盖和确定性测试。
     *
     * @param llmClient 真实模型客户端
     * @param toolRegistry 工具注册中心
     * @param promptAssembler 分层 prompt 组装器
     * @param promptContext 当前运行时 prompt 上下文
     */
    public Agent(LlmClient llmClient, ToolRegistry toolRegistry,
                 PromptAssembler promptAssembler, PromptContext promptContext) {
        this(llmClient, toolRegistry, promptAssembler, promptContext, MemoryManager.inMemory(llmClient));
    }

    /** 完整依赖注入构造器。 */
    public Agent(LlmClient llmClient, ToolRegistry toolRegistry,
                 PromptAssembler promptAssembler, PromptContext promptContext,
                 MemoryManager memoryManager) {
        if (llmClient == null) {
            throw new IllegalArgumentException("llmClient 不可为空");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry 不可为空");
        }
        if (promptAssembler == null || promptContext == null || memoryManager == null) {
            throw new IllegalArgumentException("prompt 和 memory 组件不可为空");
        }
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.promptAssembler = promptAssembler;
        this.promptContext = promptContext;
        this.memoryManager = memoryManager;
        // [s09 新增] 初始化 Token 预算和压缩器
        this.contextProfile = ContextProfile.from(llmClient);
        this.tokenBudget = new TokenBudget(contextProfile.maxContextWindow());
        this.compactor = new ConversationHistoryCompactor(llmClient);
        this.startNanos = System.nanoTime();
        resetConversationHistory(buildSystemPrompt());
    }

    // ========== 对外接口 ==========

    /**
     * [s05 新增] 注入 HITL 审批链。
     * <p>设置后，副作用工具（write_file/execute_command/create_project）
     * 在 Agent 执行工具调用前经过 Policy → HITL 审批链。</p>
     */
    public void setHitlRegistry(ouccs.smy.paiclilearn.hitl.HitlToolRegistry hitlRegistry) {
        this.hitlRegistry = hitlRegistry;
    }

    /**
     * 设置用户侧流式监听器，用于展示 LLM 推理和内容的实时增量。
     */
    public void setStreamListener(LlmClient.StreamListener listener) {
        this.userStreamListener = listener != null ? listener : LlmClient.StreamListener.NO_OP;
    }

    /**
     * 获取当前对话历史（只读视图），用于外部展示或测试断言。
     */
    public List<LlmClient.Message> getConversationHistory() {
        return List.copyOf(conversationHistory);
    }

    /**
     * 执行一次 ReAct 循环。
     *
     * @param userInput 用户输入文本
     * @return 最终回答文本；若内容已流式输出则返回空字符串
     * @throws IOException LLM 调用或工具执行中的 IO 异常
     */
    public String run(String userInput) throws IOException {
        String explicitFact = ExplicitMemoryHints.extractFact(userInput);
        if (explicitFact != null) {
            memoryManager.saveFact(explicitFact, "project");
        }
        refreshSystemPrompt(userInput);
        conversationHistory.add(LlmClient.Message.user(userInput));
        memoryManager.addUserMessage(userInput);

        StreamRenderer streamRenderer = new StreamRenderer();

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // [s09 新增] Token 压缩检查
            if (tokenBudget.needsCompression(memoryManager.getConversationMemory(), 0.90)) {
                compactor.compactIfNeeded(conversationHistory,
                        contextProfile.compressionTriggerTokens());
            }

            var response = llmClient.chat(
                    conversationHistory,
                    toolRegistry.getToolDefinitions(),
                    streamRenderer
            );

            // [s09 新增] 记录 Token 消耗并展示统计
            tokenBudget.recordUsage(response.inputTokens(), response.outputTokens(),
                    response.cachedInputTokens());
            System.out.println(TokenUsageFormatter.format(llmClient, tokenBudget,
                    contextProfile, startNanos));

            if (response.hasToolCalls()) {
                // ---- 工具调用分支 ----
                // 1. 追加 assistant 消息（含 reasoning、content 和 toolCalls）
                conversationHistory.add(LlmClient.Message.assistant(
                        response.reasoningContent(),
                        response.content(),
                        response.toolCalls()
                ));

                // 2. 执行工具 [s05 修改] 优先走 HITL 审批链
                var invocations = toolRegistry.convertToolCalls(response.toolCalls());
                var results = hitlRegistry != null
                        ? hitlRegistry.executeTools(invocations)
                        : toolRegistry.executeTools(invocations);

                // 3. 追加 tool 结果消息
                for (var result : results) {
                    conversationHistory.add(LlmClient.Message.tool(result.id(), result.result()));
                    memoryManager.addToolResult(result.result());
                }

                streamRenderer.resetBetweenIterations();
            } else {
                // ---- 最终回答分支 ----
                conversationHistory.add(LlmClient.Message.assistant(response.content()));
                memoryManager.addAssistantMessage(response.content());
                streamRenderer.finish();

                if (streamRenderer.hasStreamedOutput()) {
                    return "";
                }
                return formatUserFacingResponse(
                        response.reasoningContent(),
                        response.content()
                );
            }
        }

        // 超过最大迭代次数
        return "已达最大迭代次数（" + MAX_ITERATIONS + "），无法在预算内完成请求。s13 加入可配置预算。";
    }

    /**
     * 清空对话历史，保留一条新的 system 提示。
     * <p>
     * 本章只清空对话消息；后续章节会把新增的会话状态纳入同一清理入口。</p>
     */
    public void clearHistory() {
        memoryManager.clearShortTerm();
        resetConversationHistory(buildSystemPrompt());
    }

    /**
     * 获取当前 Agent 状态摘要，供外部展示或测试断言。
     */
    public String statusSummary() {
        return String.format("Agent[provider=%s, model=%s, history=%d]",
                llmClient.getProviderName(),
                llmClient.getModelName(),
                conversationHistory.size());
    }

    // ========== 内部方法 ==========

    private void resetConversationHistory(String systemContent) {
        conversationHistory.clear();
        conversationHistory.add(LlmClient.Message.system(systemContent));
    }

    /** 组装当前 ReAct Agent 使用的完整 system prompt。 */
    private String buildSystemPrompt() {
        return promptAssembler.assemble(PromptMode.AGENT, promptContext);
    }

    private void refreshSystemPrompt(String query) {
        String memoryContext = memoryManager.buildContextForQuery(query, 600);
        String systemPrompt = promptAssembler.assemble(
                PromptMode.AGENT, promptContext.withMemoryContext(memoryContext));
        conversationHistory.set(0, LlmClient.Message.system(systemPrompt));
    }

    private static String formatUserFacingResponse(String reasoning, String content) {
        StringBuilder sb = new StringBuilder();
        if (reasoning != null && !reasoning.isBlank()) {
            sb.append("\uD83E\uDDE0 思考过程:\n").append(reasoning).append("\n\n");
        }
        sb.append("\u25AA ").append(content);
        return sb.toString();
    }

    // ========== 内部 StreamRenderer ==========

    /**
     * 管理流式输出的内部监听器。
     * <ul>
     *   <li>代理 reasoning/content delta 到用户监听器</li>
     *   <li>记录 {@link #hasStreamedOutput()} 状态</li>
     *   <li>{@link #resetBetweenIterations()} 在每轮工具执行前清空 reasoning 累积</li>
     * </ul>
     */
    class StreamRenderer implements LlmClient.StreamListener {
        private boolean streamedOutput = false;

        @Override
        public void onReasoningDelta(String delta) {
            userStreamListener.onReasoningDelta(delta);
        }

        @Override
        public void onContentDelta(String delta) {
            streamedOutput = true;
            userStreamListener.onContentDelta(delta);
        }

        /** 在工具执行前调用，重置跨迭代的推理显示状态。 */
        void resetBetweenIterations() {
            // 本章只重置流式状态；终端动态区在渲染章节统一处理
        }

        /** 在最终回答完成后调用，执行收尾刷新。 */
        void finish() {
            // s03：无特殊收尾操作
        }

        /** 当前 ReAct 轮次中是否有 content 被流式输出。 */
        boolean hasStreamedOutput() {
            return streamedOutput;
        }
    }
}
