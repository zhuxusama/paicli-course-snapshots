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
        int tokens = TokenBudget.estimateMessagesTokens(history);
        boolean compressed = compactor.compactIfNeeded(history, tokens - 100);

        assertTrue(tokens > 2000, "5000 字符消息的 token 估算应 > 2000, 实际: " + tokens);
        assertTrue(compressed);
        assertEquals("system", history.get(0).role());
        assertEquals("你是助手", history.get(0).content());
        assertEquals("user", history.get(1).role());
        assertTrue(history.get(1).content().contains("这是摘要"));
        assertEquals("assistant", history.get(2).role());
        assertTrue(history.get(3).content().startsWith("C"));
        assertTrue(history.get(5).content().startsWith("最新问题"));
    }

    @Test void retainRecentRoundsKeepsLastMessages() {
        var client = new StubLlmClient("summary");
        var compactor = new ConversationHistoryCompactor(client, 1);
        var history = new ArrayList<>(List.of(
                LlmClient.Message.system("system"),
                LlmClient.Message.user("A".repeat(8000)),
                LlmClient.Message.assistant("B".repeat(8000)),
                LlmClient.Message.user("C".repeat(8000)),
                LlmClient.Message.assistant("D".repeat(8000))
        ));
        int tokens = TokenBudget.estimateMessagesTokens(history);
        boolean compressed = compactor.compactIfNeeded(history, tokens - 100);

        assertTrue(compressed);
        assertEquals("system", history.get(0).role());
        assertTrue(history.get(1).content().contains("summary"));
        assertEquals("C".repeat(8000), history.get(history.size() - 2).content());
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
}
