package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * s08: 直接压缩 Agent 发送给 LLM 的 conversationHistory，区别于压缩 ConversationMemory。
 * <p>
 * 【s08 状态：骨架引入】本章定义了 SUMMARY_PROMPT 模板、retainRecentRounds 策略、
 * compact() 方法签名和 LLM 依赖注入点。Agent 构造时会创建此对象，但真实 LLM 调用
 * 链路中的自动压缩时机（maybeCompactHistory）由 s09 完善。
 * 两类压缩不要混淆：ConversationHistoryCompactor 压缩 LLM 消息历史（防窗口超限），
 * ContextCompressor 压缩 ConversationMemory 短期记忆（防 token 预算溢出）。
 * </p>
 */
public class ConversationHistoryCompactor {
    private static final Logger log = LoggerFactory.getLogger(ConversationHistoryCompactor.class);
    private static final int DEFAULT_RETAIN_RECENT_ROUNDS = 3;
    private static final int MAX_SUMMARY_INPUT_CHARS = 60_000;

    private static final String SUMMARY_PROMPT = """
            请把下面的对话历史压缩成简明摘要，保留用户目标、已完成操作、工具结果、已达成结论和未解决问题。
            输出 1-3 段中文，不要加入元解释。

            === 待压缩的对话 ===
            %s
            === 待压缩的对话（结束）===
            """;

    private LlmClient llmClient;
    private final int retainRecentRounds;

    public ConversationHistoryCompactor(LlmClient llmClient) {
        this(llmClient, DEFAULT_RETAIN_RECENT_ROUNDS);
    }

    public ConversationHistoryCompactor(LlmClient llmClient, int retainRecentRounds) {
        this.llmClient = llmClient;
        this.retainRecentRounds = Math.max(1, retainRecentRounds);
    }

    public void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public boolean compactIfNeeded(List<LlmClient.Message> history, int triggerTokens) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        int currentTokens = TokenBudget.estimateMessagesTokens(history);
        if (currentTokens < triggerTokens) {
            return false;
        }

        int systemEnd = "system".equals(history.get(0).role()) ? 1 : 0;
        List<Integer> userIndices = new ArrayList<>();
        for (int i = systemEnd; i < history.size(); i++) {
            if ("user".equals(history.get(i).role())) {
                userIndices.add(i);
            }
        }
        if (userIndices.size() <= retainRecentRounds) {
            log.info("compactIfNeeded skip: only {} user turns, retain {}", userIndices.size(), retainRecentRounds);
            return false;
        }

        int splitIdx = userIndices.get(userIndices.size() - retainRecentRounds);
        if (splitIdx <= systemEnd) {
            return false;
        }
        List<LlmClient.Message> oldMsgs = new ArrayList<>(history.subList(systemEnd, splitIdx));
        String summary;
        try {
            summary = summarize(oldMsgs);
        } catch (IOException e) {
            log.warn("conversation summary LLM call failed; skip compaction", e);
            return false;
        }
        if (summary == null || summary.isBlank()) {
            return false;
        }

        List<LlmClient.Message> rebuilt = new ArrayList<>();
        for (int i = 0; i < systemEnd; i++) {
            rebuilt.add(history.get(i));
        }
        rebuilt.add(LlmClient.Message.user("[已压缩的历史对话摘要]\n" + summary.trim()));
        rebuilt.add(LlmClient.Message.assistant("好的，我已了解之前的上下文，请继续。"));
        rebuilt.addAll(history.subList(splitIdx, history.size()));

        int afterTokens = TokenBudget.estimateMessagesTokens(rebuilt);
        history.clear();
        history.addAll(rebuilt);
        log.info(String.format(Locale.ROOT,
                "compacted conversationHistory: tokens %d -> %d, messages -> %d",
                currentTokens, afterTokens, rebuilt.size()));
        return true;
    }

    protected String summarize(List<LlmClient.Message> messages) throws IOException {
        if (llmClient == null) {
            throw new IOException("LLM client not configured");
        }
        StringBuilder sb = new StringBuilder();
        for (LlmClient.Message m : messages) {
            sb.append(m.role().toUpperCase(Locale.ROOT)).append(": ");
            if (m.content() != null) {
                sb.append(m.content());
            }
            if (m.toolCalls() != null) {
                for (LlmClient.ToolCall tc : m.toolCalls()) {
                    sb.append("\n  TOOL_CALL ").append(tc.function().name())
                            .append(": ").append(tc.function().arguments());
                }
            }
            sb.append("\n\n");
            if (sb.length() > MAX_SUMMARY_INPUT_CHARS) {
                sb.append("...(超长内容已截断)\n");
                break;
            }
        }
        String prompt = String.format(SUMMARY_PROMPT, sb);
        LlmClient.ChatResponse response = llmClient.chat(List.of(
                LlmClient.Message.system("你是对话摘要助手，只输出摘要本身。"),
                LlmClient.Message.user(prompt)
        ), null);
        return response == null ? null : response.content();
    }

    public int retainRecentRounds() {
        return retainRecentRounds;
    }
}
