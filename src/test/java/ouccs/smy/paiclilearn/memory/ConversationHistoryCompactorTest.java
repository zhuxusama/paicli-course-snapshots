package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s09 新增] 验证 ConversationHistoryCompactor 的压缩触发和消息重建。 */
class ConversationHistoryCompactorTest {

    @Test void compactNotNeededWhenUnderThreshold() {
        var client = new StubLlmClient("ok");
        var compactor = new ConversationHistoryCompactor(client, 3);
        var history = new ArrayList<>(List.of(
                LlmClient.Message.system("你是助手"),
                LlmClient.Message.user("你好"),
                LlmClient.Message.assistant("你好！")
        ));
        boolean compressed = compactor.compactIfNeeded(history, 99999999);
        assertFalse(compressed);
        assertEquals(3, history.size());
    }

    @Test void compactTriggersOnLargeHistory() {
        System.out.println("\n===== 测试：大历史触发压缩 =====");
        var client = new StubLlmClient("这是摘要");
        var compactor = new ConversationHistoryCompactor(client, 2);
        var history = new ArrayList<>(List.of(
                LlmClient.Message.system("你是助手"),
                LlmClient.Message.user("A".repeat(5000)),
                LlmClient.Message.assistant("B".repeat(5000)),
                LlmClient.Message.user("C".repeat(5000)),
                LlmClient.Message.assistant("D".repeat(5000)),
                LlmClient.Message.user("最新问题"),
                LlmClient.Message.assistant("最新回答")
        ));

        int beforeTokens = TokenBudget.estimateMessagesTokens(history);
        System.out.println("压缩前消息数: " + history.size());
        System.out.println("压缩前 Token 数: " + beforeTokens);
        System.out.println("触发阈值: " + (beforeTokens - 100));

        boolean compressed = compactor.compactIfNeeded(history, beforeTokens - 100);

        int afterTokens = TokenBudget.estimateMessagesTokens(history);
        System.out.println("压缩后消息数: " + history.size());
        System.out.println("压缩后 Token 数: " + afterTokens);
        System.out.println("Token 减少率: " + ((beforeTokens - afterTokens) * 100.0 / beforeTokens) + "%");

        System.out.println("\n压缩后消息结构:");
        for (int i = 0; i < history.size(); i++) {
            var msg = history.get(i);
            String preview = msg.content().length() > 50
                ? msg.content().substring(0, 50) + "..."
                : msg.content();
            System.out.println("  [" + i + "] " + msg.role() + ": " + preview);
        }

        assertTrue(beforeTokens > 2000, "5000 字符消息的 token 估算应 > 2000, 实际: " + beforeTokens);
        assertTrue(compressed, "应触发压缩");
        assertEquals("system", history.get(0).role());
        assertEquals("你是助手", history.get(0).content());
        assertEquals("user", history.get(1).role());
        assertTrue(history.get(1).content().contains("这是摘要"));
        assertEquals("assistant", history.get(2).role());
        assertTrue(history.get(3).content().startsWith("C"));
        assertTrue(history.get(5).content().startsWith("最新问题"));
    }

    @Test void retainRecentRoundsKeepsLastMessages() {
        System.out.println("\n===== 测试：最近轮次保留 =====");
        var client = new StubLlmClient("summary");
        var compactor = new ConversationHistoryCompactor(client, 1); // 只保留 1 轮
        assertEquals(1, compactor.retainRecentRounds());
        var history = new ArrayList<>(List.of(
                LlmClient.Message.system("system"),
                LlmClient.Message.user("A".repeat(8000)),
                LlmClient.Message.assistant("B".repeat(8000)),
                LlmClient.Message.user("C".repeat(8000)),
                LlmClient.Message.assistant("D".repeat(8000))
        ));

        int beforeTokens = TokenBudget.estimateMessagesTokens(history);
        System.out.println("压缩前消息数: " + history.size());
        System.out.println("压缩前 Token 数: " + beforeTokens);
        System.out.println("retainRecentRounds=1（只保留最近 1 轮原始消息）");

        boolean compressed = compactor.compactIfNeeded(history, beforeTokens - 100);

        int afterTokens = TokenBudget.estimateMessagesTokens(history);
        System.out.println("压缩后消息数: " + history.size());
        System.out.println("压缩后 Token 数: " + afterTokens);
        System.out.println("Token 减少率: " + ((beforeTokens - afterTokens) * 100.0 / beforeTokens) + "%");

        System.out.println("\n压缩后消息结构:");
        for (int i = 0; i < history.size(); i++) {
            var msg = history.get(i);
            String preview = msg.content().length() > 50
                ? msg.content().substring(0, 50) + "..."
                : msg.content();
            System.out.println("  [" + i + "] " + msg.role() + ": " + preview);
        }

        assertTrue(compressed, "应触发压缩");
        assertEquals("system", history.get(0).role());
        assertTrue(history.get(1).content().contains("summary"));
        assertEquals("C".repeat(8000), history.get(history.size() - 2).content());
    }

    @Test void summaryRequestSamplesHeadMiddleAndTailOfOldHistory() {
        var client = new RecordingPromptClient();
        var compactor = new ConversationHistoryCompactor(client, 1);
        var history = new ArrayList<LlmClient.Message>();
        history.add(LlmClient.Message.system("system"));
        for (int round = 0; round < 120; round++) {
            String marker = switch (round) {
                case 0 -> "HEAD_DECISION";
                case 60 -> "MIDDLE_CONSTRAINT";
                case 118 -> "TAIL_ERROR";
                default -> "round-" + round;
            };
            history.add(LlmClient.Message.user(marker + " " + "u".repeat(900)));
            history.add(LlmClient.Message.assistant("answer-" + round + " " + "a".repeat(900)));
        }

        int tokens = TokenBudget.estimateMessagesTokens(history);
        assertTrue(compactor.compactIfNeeded(history, tokens));
        assertTrue(client.prompt.contains("HEAD_DECISION"), "摘要输入必须覆盖旧历史头部");
        assertTrue(client.prompt.contains("MIDDLE_CONSTRAINT"), "摘要输入必须覆盖旧历史中部");
        assertTrue(client.prompt.contains("TAIL_ERROR"), "摘要输入必须覆盖保留轮次之前的尾部");
        assertTrue(client.prompt.length() <= 65_000, "摘要请求仍需保持有界");
    }

    // ========== Stub ==========

    static class StubLlmClient implements LlmClient {
        private final String response;
        StubLlmClient(String r) { this.response = r; }
        @Override public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            return new ChatResponse("assistant", response, null, null, 0, 0, 0);
        }
        @Override public String getModelName() { return "stub-compact"; }
        @Override public String getProviderName() { return "stub"; }
    }

    static final class RecordingPromptClient implements LlmClient {
        String prompt = "";

        @Override public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            prompt = messages.stream().map(Message::content)
                    .filter(java.util.Objects::nonNull).reduce("", (a, b) -> a + "\n" + b);
            return new ChatResponse("assistant", "覆盖整段旧历史的摘要", null, null, 0, 0, 0);
        }

        @Override public String getModelName() { return "recording"; }
        @Override public String getProviderName() { return "test"; }
    }
}
