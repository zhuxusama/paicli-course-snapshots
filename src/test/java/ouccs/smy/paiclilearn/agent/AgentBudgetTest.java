package ouccs.smy.paiclilearn.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * [s13 新增] 验证 AgentBudget 的三种兜底退出条件。
 */
class AgentBudgetTest {
    @Test
    void withinBudgetByDefault() {
        var budget = new AgentBudget();
        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());
    }

    @Test
    void tokenBudgetExceeded() {
        var budget = new AgentBudget(100, 3, 50);
        budget.recordTokens(60, 41);  // 101 >= 100
        assertEquals(AgentBudget.ExitReason.TOKEN_BUDGET_EXCEEDED, budget.check());
    }

    @Test
    void hardIterationLimit() {
        var budget = new AgentBudget(Integer.MAX_VALUE, 3, 5);
        for (int i = 0; i < 5; i++) budget.beginIteration();
        assertEquals(AgentBudget.ExitReason.HARD_ITERATION_LIMIT, budget.check());
    }

    @Test
    void stagnationDetected() {
        var budget = new AgentBudget(Integer.MAX_VALUE, 3, 50);
        // 构造真实的 ToolCall 对象
        var func = new ouccs.smy.paiclilearn.llm.LlmClient.ToolCall.Function("read_file", "{\"path\":\"a.txt\"}");
        var tc = new ouccs.smy.paiclilearn.llm.LlmClient.ToolCall("call_1", func);
        var list = java.util.List.of(tc);

        budget.recordToolCalls(list);
        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());

        budget.recordToolCalls(list);
        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());

        budget.recordToolCalls(list);  // 第 3 次相同 → 停滞
        assertEquals(AgentBudget.ExitReason.STAGNATION_DETECTED, budget.check());
    }

    @Test
    void describeExit() {
        var budget = new AgentBudget(1000, 3, 50);
        assertNotNull(budget.describeExit(AgentBudget.ExitReason.STAGNATION_DETECTED));
        assertTrue(budget.describeExit(AgentBudget.ExitReason.TOKEN_BUDGET_EXCEEDED).contains("Token"));
        assertTrue(budget.describeExit(AgentBudget.ExitReason.HARD_ITERATION_LIMIT).contains("硬轮数"));
    }

    @Test
    void readsPositiveSystemPropertyOverrides() {
        System.setProperty("paicli.react.token.budget", "123");
        System.setProperty("paicli.react.stagnation.window", "4");
        System.setProperty("paicli.react.hard.max.iterations", "9");
        try {
            var budget = AgentBudget.fromSystemProperties();
            assertEquals(123, budget.tokenBudget());
            assertEquals(4, budget.stagnationWindow());
            assertEquals(9, budget.hardMaxIterations());
        } finally {
            System.clearProperty("paicli.react.token.budget");
            System.clearProperty("paicli.react.stagnation.window");
            System.clearProperty("paicli.react.hard.max.iterations");
        }
    }
}
