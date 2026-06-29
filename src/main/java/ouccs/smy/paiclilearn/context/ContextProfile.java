package ouccs.smy.paiclilearn.context;

import ouccs.smy.paiclilearn.llm.LlmClient;

/**
 * 上下文配置画像——从模型窗口导出主要预算参数。
 * <p>
 * [s09 新增] {@link #from(LlmClient)} 工厂从真实模型客户端读取
 * maxContextWindow，按固定比例分配各项预算。
 * {@link #custom(int, int)} 允许测试或特殊配置覆盖。</p>
 *
 * @since s09
 */
/** [s09 新增] */
public record ContextProfile(
        int maxContextWindow,
        int agentTokenBudget,
        double compressionTriggerRatio,
        int shortTermMemoryBudget,
        int memoryContextTokens,
        boolean mcpResourceIndexEnabled,
        boolean promptCachingSupported,
        String promptCacheMode
) {
    public static final double DEFAULT_COMPRESSION_TRIGGER_RATIO = 0.90;
    private static final int MIN_WINDOW = 8_000;
    private static final int MCP_RESOURCE_INDEX_MIN_WINDOW = 32_000;

    /** 从真实 LLM 客户端创建配置画像。 */
    public static ContextProfile from(LlmClient llmClient) {
        int window = Math.max(MIN_WINDOW, llmClient == null ? 128_000 : llmClient.maxContextWindow());
        int agentBudget = Math.max(4000, (int) (window * 0.8));
        int shortTermBudget = Math.max(4000, (int) (window * 0.45));
        int memoryCtx = Math.max(500, Math.min(5000, window / 200));
        return new ContextProfile(window, agentBudget, DEFAULT_COMPRESSION_TRIGGER_RATIO,
                shortTermBudget, memoryCtx,
                window >= MCP_RESOURCE_INDEX_MIN_WINDOW,
                llmClient != null && llmClient.supportsPromptCaching(),
                llmClient == null ? "none" : llmClient.promptCacheMode());
    }

    /** 自定义窗口和短期预算，其他参数用默认值。 */
    public static ContextProfile custom(int contextWindow, int shortTermMemoryBudget) {
        int window = Math.max(MIN_WINDOW, contextWindow);
        return new ContextProfile(window, Math.max(4000, (int) (window * 0.8)),
                DEFAULT_COMPRESSION_TRIGGER_RATIO, Math.max(1, shortTermMemoryBudget),
                Math.max(500, Math.min(5000, window / 200)),
                window >= MCP_RESOURCE_INDEX_MIN_WINDOW, false, "none");
    }

    /** 触发压缩的 token 阈值（maxWindow × triggerRatio）。 */
    public int compressionTriggerTokens() {
        return (int) (maxContextWindow * compressionTriggerRatio);
    }

    /** 给状态栏/日志使用的单行摘要。 */
    public String summary() {
        return "window=" + maxContextWindow
                + ", trigger=" + (int) (compressionTriggerRatio * 100) + "%"
                + ", shortTerm=" + shortTermMemoryBudget
                + ", memoryContext=" + memoryContextTokens
                + ", mcpResourceIndex=" + mcpResourceIndexEnabled
                + ", promptCache=" + promptCacheMode;
    }
}
