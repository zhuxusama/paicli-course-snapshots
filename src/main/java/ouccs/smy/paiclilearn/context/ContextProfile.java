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
    /** 从真实 LLM 客户端创建配置画像。 */
    public static ContextProfile from(LlmClient llmClient) {
        int window = llmClient.maxContextWindow();
        int agentBudget = Math.max(4000, (int) (window * 0.8));
        int shortTermBudget = Math.max(4000, (int) (window * 0.45));
        int memoryCtx = Math.max(500, Math.min(5000, window / 200));
        return new ContextProfile(window, agentBudget, 0.90,
                shortTermBudget, memoryCtx,
                false, llmClient.supportsPromptCaching(), llmClient.promptCacheMode());
    }

    /** 自定义窗口和短期预算，其他参数用默认值。 */
    public static ContextProfile custom(int contextWindow, int shortTermMemoryBudget) {
        return new ContextProfile(contextWindow, Math.max(4000, (int) (contextWindow * 0.8)),
                0.90, shortTermMemoryBudget, Math.max(500, contextWindow / 200),
                false, false, "none");
    }

    /** 触发压缩的 token 阈值（maxWindow × triggerRatio）。 */
    public int compressionTriggerTokens() {
        return (int) (maxContextWindow * compressionTriggerRatio);
    }
}
