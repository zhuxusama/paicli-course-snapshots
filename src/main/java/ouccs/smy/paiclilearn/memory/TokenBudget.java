package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.util.List;

/**
 * Token 预算跟踪器，用于判断对话历史是否接近模型窗口上限。
 * <p>
 * [s09 新增] 构造时从模型上下文窗口扣除系统/工具/回复预留后，
 * 得到可用的对话预算。{@link #needsCompression(ConversationMemory, double)}
 * 检查是否超过触发比例（默认 90%），由 Agent 或历史压缩器按需调用。</p>
 *
 * @since s09
 */
/** [s09 新增] */
public class TokenBudget {
    private final int contextWindow;
    private final int reservedForSystem;
    private final int reservedForTools;
    private final int reservedForResponse;

    private int totalInputTokens;
    private int totalOutputTokens;
    private int totalCachedInputTokens;
    private int llmCallCount;

    public TokenBudget(int contextWindow) {
        this(contextWindow, 500, 800, 2000);
    }

    public TokenBudget(int contextWindow, int reservedForSystem,
                       int reservedForTools, int reservedForResponse) {
        if (contextWindow < 1) throw new IllegalArgumentException("contextWindow 必须 > 0");
        this.contextWindow = contextWindow;
        this.reservedForSystem = reservedForSystem;
        this.reservedForTools = reservedForTools;
        this.reservedForResponse = reservedForResponse;
    }

    /** 扣除预留后可用于对话的 token 数。 */
    public int getAvailableForConversation() {
        return contextWindow - reservedForSystem - reservedForTools - reservedForResponse;
    }

    /** 消息列表是否在预算内。 */
    public boolean isWithinBudget(List<LlmClient.Message> messages) {
        int estimated = estimateMessagesTokens(messages);
        return estimated <= getAvailableForConversation();
    }

    /** 当前短期记忆是否超过压缩触发比例。 */
    public boolean needsCompression(ConversationMemory memory, double triggerRatio) {
        if (memory == null) return false;
        int total = memory.getAll().stream()
                .mapToInt(MemoryEntry::tokenCount)
                .sum();
        return total >= getAvailableForConversation() * triggerRatio;
    }

    /** 兼容源码默认触发率：短期记忆达到 90% 可用预算时需要压缩。 */
    public boolean needsCompression(ConversationMemory memory) {
        return needsCompression(memory, 0.9);
    }

    /** 兼容不区分 cached input 的调用路径。 */
    public void recordUsage(int inputTokens, int outputTokens) {
        recordUsage(inputTokens, outputTokens, 0);
    }

    /** 记录一次 LLM 调用的 token 消耗。 */
    public void recordUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        this.totalInputTokens += inputTokens;
        this.totalOutputTokens += outputTokens;
        this.totalCachedInputTokens += Math.max(0, cachedInputTokens);
        this.llmCallCount++;
    }

    /** 对 {@link LlmClient.Message} 列表做线性 token 估算。 */
    public static int estimateMessagesTokens(List<LlmClient.Message> messages) {
        if (messages == null) return 0;
        int total = 0;
        for (var msg : messages) {
            if (msg.content() != null) total += estimateTextTokens(msg.content());
            if (msg.reasoningContent() != null) total += estimateTextTokens(msg.reasoningContent());
            if (msg.toolCalls() != null) {
                for (var tc : msg.toolCalls()) {
                    total += estimateTextTokens(tc.function().name());
                    total += estimateTextTokens(tc.function().arguments());
                }
            }
            total += 4; // 消息开销
        }
        return total;
    }

    /** 使用字符比例的近似 token 估值。 */
    private static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        long cjk = text.codePoints().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long other = text.length() - cjk;
        return Math.max(1, (int) Math.ceil(cjk / 1.5 + other / 4.0));
    }

    // ========== 访问器 ==========

    public int contextWindow() { return contextWindow; }
    public int totalInputTokens() { return totalInputTokens; }
    public int totalOutputTokens() { return totalOutputTokens; }
    public int totalCachedInputTokens() { return totalCachedInputTokens; }
    public int llmCallCount() { return llmCallCount; }
    public int getContextWindow() { return contextWindow; }
    public int getTotalInputTokens() { return totalInputTokens; }
    public int getTotalOutputTokens() { return totalOutputTokens; }
    public int getTotalCachedInputTokens() { return totalCachedInputTokens; }
    public int getLlmCallCount() { return llmCallCount; }
    public void reset() { totalInputTokens = totalOutputTokens = totalCachedInputTokens = 0; llmCallCount = 0; }

    /** [s09 新增] 用量报告，供 getSystemStatus 使用。 */
    public String getUsageReport() {
        return "Token 用量: 输入=" + totalInputTokens + " 输出=" + totalOutputTokens
                + " 缓存=" + totalCachedInputTokens + " 调用=" + llmCallCount + " 次"
                + " (窗口=" + contextWindow + ")";
    }
}
