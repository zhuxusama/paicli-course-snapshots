package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s09 新增] 验证 TokenBudget 的预算计算和压缩触发判断。 */
class TokenBudgetTest {

    @Test void availableBudgetDeductsReservations() {
        var budget = new TokenBudget(128000, 500, 800, 2000);
        assertEquals(128000 - 500 - 800 - 2000, budget.getAvailableForConversation());
    }

    @Test void withinBudgetForSmallMessages() {
        var budget = new TokenBudget(128000);
        var msgs = List.of(LlmClient.Message.system("简短测试"));
        assertTrue(budget.isWithinBudget(msgs));
    }

    @Test void emptyWindowThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBudget(0));
    }

    @Test void estimateTokensForMixedContent() {
        var msgs = List.of(
                LlmClient.Message.system("你好世界Hello"),
                LlmClient.Message.user("中文字符"),
                LlmClient.Message.assistant(null, "HelloWorld", null)
        );
        int estimated = TokenBudget.estimateMessagesTokens(msgs);
        assertTrue(estimated > 0);
    }

    @Test void needsCompressionDetectsOverflow() {
        var budget = new TokenBudget(200000);
        var memory = new ConversationMemory(100);
        memory.store(MemoryEntry.message("A".repeat(200), MemoryEntry.MemoryType.USER, "test"));
        assertFalse(budget.needsCompression(memory, 0.9),
                "200 字符估算约 50 token, 可用约 196700, 不应触发压缩");
    }

    @Test void recordUsageAccumulates() {
        var budget = new TokenBudget(128000);
        budget.recordUsage(100, 50, 0);
        budget.recordUsage(200, 30, 10);
        assertEquals(300, budget.totalInputTokens());
        assertEquals(80, budget.totalOutputTokens());
        assertEquals(2, budget.llmCallCount());
    }

    @Test void resetClearsAccumulators() {
        var budget = new TokenBudget(128000);
        budget.recordUsage(100, 50, 0);
        budget.reset();
        assertEquals(0, budget.totalInputTokens());
        assertEquals(0, budget.llmCallCount());
    }
}
