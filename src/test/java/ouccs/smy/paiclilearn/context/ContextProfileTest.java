package ouccs.smy.paiclilearn.context;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.TokenBudget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** [s09 新增] 验证 ContextProfile 的创建和参数计算。 */
class ContextProfileTest {

    @Test void fromLlmClientDerivesWindow() {
        var client = new StubLlmClient();
        var profile = ContextProfile.from(client);
        assertEquals(64000, profile.maxContextWindow());
        assertTrue(profile.agentTokenBudget() > 0);
        assertTrue(profile.memoryContextTokens() > 0);
    }

    @Test void customWindow() {
        var profile = ContextProfile.custom(128000, 10000);
        assertEquals(128000, profile.maxContextWindow());
        assertEquals(10000, profile.shortTermMemoryBudget());
    }

    @Test void compressionTriggerTokens() {
        var profile = ContextProfile.custom(100000, 5000);
        assertEquals(90000, profile.compressionTriggerTokens());
    }

    @Test void fromLlmClientSupportsDefaultValues() {
        var client = new StubLlmClient();
        var profile = ContextProfile.from(client);
        assertEquals(0.90, profile.compressionTriggerRatio(), 0.01);
    }

    /** 最小窗口也保证最低内存预算。 */
    @Test void smallWindowHasMinimumBudget() {
        var client = new StubLlmClient() {
            @Override public int maxContextWindow() { return 1000; }
        };
        var profile = ContextProfile.from(client);
        assertEquals(8000, profile.maxContextWindow(), "源码会把过小窗口抬升到 MIN_WINDOW");
        assertEquals(6400, profile.agentTokenBudget(), "最小窗口仍按 80% 派生 agent 预算");
    }

    static class StubLlmClient implements LlmClient {
        @Override public int maxContextWindow() { return 64000; }
        @Override public String getModelName() { return "stub-ctx"; }
        @Override public String getProviderName() { return "stub"; }
        @Override public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return new ChatResponse("assistant", "", null, null, 0, 0, 0);
        }
    }
}
