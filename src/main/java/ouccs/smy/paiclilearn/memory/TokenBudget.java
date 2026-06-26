package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.util.List;

/**
 * s08: 统一估算消息 token、上下文窗口余量和压缩触发条件。
 * <p>
 * 【s08 状态：骨架引入】本章提供完整的 API 表面（窗口预算计算、压缩触发条件判断、
 * 消息 token 估算），但压缩触发后的 LLM 摘要压缩流程（ContextCompressor）和
 * 对话历史的 token 级压缩（ConversationHistoryCompactor）由 s09 完成。
 * s08 中的 TokenBudget 已能被 MemoryManager 引用，日常检索和短期淘汰不受影响。
 * </p>
 */
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

    public TokenBudget(int contextWindow, int reservedForSystem, int reservedForTools, int reservedForResponse) {
        this.contextWindow = contextWindow;
        this.reservedForSystem = reservedForSystem;
        this.reservedForTools = reservedForTools;
        this.reservedForResponse = reservedForResponse;
    }

    public int getAvailableForConversation() {
        return contextWindow - reservedForSystem - reservedForTools - reservedForResponse;
    }

    public boolean isWithinBudget(List<LlmClient.Message> messages) {
        return estimateMessagesTokens(messages) <= getAvailableForConversation();
    }

    public boolean needsCompression(ConversationMemory memory, double triggerRatio) {
        int compressionBudget = Math.min(memory.getMaxTokens(), getAvailableForConversation());
        return memory.getTokenCount() >= compressionBudget * triggerRatio;
    }

    public boolean needsCompression(ConversationMemory memory) {
        return needsCompression(memory, 0.9);
    }

    public void recordUsage(int inputTokens, int outputTokens) {
        recordUsage(inputTokens, outputTokens, 0);
    }

    public void recordUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        totalInputTokens += inputTokens;
        totalOutputTokens += outputTokens;
        totalCachedInputTokens += Math.max(0, cachedInputTokens);
        llmCallCount++;
    }

    public String getUsageReport() {
        double avgInput = llmCallCount > 0 ? (double) totalInputTokens / llmCallCount : 0;
        return String.format("Token 统计: 调用 %d 次 | 总输入 %d | 总输出 %d | cached: %d | 平均输入: %.0f | 预算: %d (可用: %d)",
                llmCallCount, totalInputTokens, totalOutputTokens, totalCachedInputTokens, avgInput,
                contextWindow, getAvailableForConversation());
    }

    public int getContextWindow() { return contextWindow; }
    public int getTotalInputTokens() { return totalInputTokens; }
    public int getTotalOutputTokens() { return totalOutputTokens; }
    public int getTotalCachedInputTokens() { return totalCachedInputTokens; }
    public int getLlmCallCount() { return llmCallCount; }

    public static int estimateMessagesTokens(List<LlmClient.Message> messages) {
        if (messages == null) {
            return 0;
        }
        int total = 0;
        for (LlmClient.Message msg : messages) {
            total += MemoryEntry.estimateTokens(msg.content());
            if (msg.toolCalls() != null) {
                for (LlmClient.ToolCall tc : msg.toolCalls()) {
                    total += MemoryEntry.estimateTokens(tc.function().arguments());
                }
            }
        }
        return total + messages.size() * 4;
    }
}
