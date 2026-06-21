package ouccs.smy.paiclilearn.context;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.TokenBudget;

/**
 * Token 用量格式化工具——在终端显示模型上下文占用和预估费用。
 * <p>
 * [s09 新增] 格式示例：
 * {@code 📊 Token: 已用 1,234 / 128,000 | 费用: ¥0.0123}</p>
 *
 * @since s09
 */
public final class TokenUsageFormatter {
    private TokenUsageFormatter() {}

    /**
     * 格式化 token 用量和 LLM 调用信息。
     * @param llmClient     当前模型客户端
     * @param budget        预算跟踪器
     * @param profile       上下文配置画像
     * @param startNanos    系统启动纳秒
     * @return 格式化后的单行字符串
     */
    public static String format(LlmClient llmClient, TokenBudget budget,
                                ContextProfile profile, long startNanos) {
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCA Token: in=")
                .append(budget.totalInputTokens())
                .append(" out=").append(budget.totalOutputTokens());
        if (budget.totalCachedInputTokens() > 0) {
            sb.append(" cache=\u2713");
        }
        sb.append(" | 已用 ").append(budget.totalInputTokens() + budget.totalOutputTokens())
                .append(" / ").append(profile.maxContextWindow());

        String cost = estimatedCostCny(llmClient, budget.totalInputTokens(),
                budget.totalOutputTokens(), budget.totalCachedInputTokens());
        if (!cost.isEmpty()) {
            sb.append(" | 费用: \u00A5").append(cost);
        }

        long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsed > 1000) {
            sb.append(" | ").append(String.format("%.1fs", elapsed / 1000.0));
        } else {
            sb.append(" | ").append(elapsed).append("ms");
        }

        return sb.toString();
    }

    /**
     * 按 provider 估算费用。仅供参考，实际费率以 Provider 账单为准。
     */
    public static String estimatedCostCny(LlmClient llmClient, int inputTokens,
                                          int outputTokens, int cachedInputTokens) {
        String provider = llmClient.getProviderName().toLowerCase();
        double inRate, outRate, cacheRate;
        switch (provider) {
            case "deepseek" -> { inRate = 2.0; outRate = 0.5; cacheRate = 8.0; }
            case "glm" -> { inRate = 5.0; outRate = 1.0; cacheRate = 15.0; }
            default -> { return ""; } // 未知 provider 不估算费用
        }
        double cost = (inputTokens / 1_000_000.0 * inRate)
                + (outputTokens / 1_000_000.0 * outRate)
                + (cachedInputTokens / 1_000_000.0 * cacheRate);
        return String.format("%.4f", cost);
    }
}
