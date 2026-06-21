package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史压缩器：当对话即将超过模型窗口时，把中间轮次的
 * user/assistant/tool 消息摘要成一条压缩消息，保留最近的原始消息。
 * <p>
 * [s09 新增] 压缩策略：
 * <ol>
 *   <li>找到最早的几条完整 user→assistant（→tool→assistant）轮次</li>
 *   <li>调用真实 LLM 生成摘要</li>
 *   <li>用一条 user("[已压缩]" + 摘要) 替换被压缩的消息</li>
 *   <li>保留最近 N 轮原始消息</li>
 * </ol>
 * </p>
 *
 * @since s09
 */
public class ConversationHistoryCompactor {
    private final LlmClient llmClient;
    private final int retainRecentRounds;

    public ConversationHistoryCompactor(LlmClient llmClient) {
        this(llmClient, 3);
    }

    public ConversationHistoryCompactor(LlmClient llmClient, int retainRecentRounds) {
        this.llmClient = llmClient;
        this.retainRecentRounds = retainRecentRounds;
    }

    /**
     * 检查并压缩历史。
     * @param history 可变对话历史列表
     * @param triggerTokens 触发压缩的 token 阈值
     * @return true 表示执行了压缩
     */
    public boolean compactIfNeeded(List<LlmClient.Message> history, int triggerTokens) {
        if (history == null || history.size() < 3) return false;
        int estimated = TokenBudget.estimateMessagesTokens(history);
        if (estimated < triggerTokens) return false;

        // 找到可以压缩的用户消息边界
        int compressEnd = findCompressBoundary(history);
        if (compressEnd < 3) return false; // 保留 system + 至少 1 轮

        // 提取待压缩的消息（保留 system）
        List<LlmClient.Message> toCompress = new ArrayList<>(history.subList(1, compressEnd));
        List<LlmClient.Message> tail = new ArrayList<>(history.subList(compressEnd, history.size()));

        try {
            String summary = summarize(toCompress);
            history.clear();
            // system 消息
            history.add(toCompress.get(0)); // 实际上 system 在 index 0
            history.add(LlmClient.Message.user("[已压缩] " + summary));
            // 尾部 + 辅助确认
            history.add(LlmClient.Message.assistant("好的，已理解前面的对话摘要。"));
            history.addAll(tail);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 找到最早的轮次边界——以 user 消息为分割点。 */
    private int findCompressBoundary(List<LlmClient.Message> history) {
        if (history.size() < 3) return history.size();
        // 从后往前找用户消息，保留 retainRecentRounds 轮
        int userCount = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) userCount++;
            if (userCount >= retainRecentRounds) return i;
        }
        // 如果保留轮次不够，至少保留一条
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) return i;
        }
        return history.size() - 1;
    }

    /** 调用真实 LLM 生成摘要。 */
    protected String summarize(List<LlmClient.Message> messages) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (var msg : messages) {
            sb.append("[").append(msg.role()).append("] ");
            if (msg.content() != null && !msg.content().isBlank()) {
                String truncated = msg.content().length() > 2000
                        ? msg.content().substring(0, 2000) + "..." : msg.content();
                sb.append(truncated);
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                sb.append(" [调用了 ").append(msg.toolCalls().size()).append(" 个工具]");
            }
            sb.append("\\n");
        }
        String text = sb.toString();
        if (text.length() > 60000) text = text.substring(0, 60000);

        var response = llmClient.chat(
                List.of(
                        LlmClient.Message.system("请用一句话概括以下对话的关键信息和决策。"),
                        LlmClient.Message.user(text)
                ),
                List.of()
        );
        return response.content() != null ? response.content() : "";
    }
}
