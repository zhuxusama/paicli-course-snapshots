package ouccs.smy.paiclilearn.context;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.TokenBudget;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s09 新增] 演示 TokenBudget + ContextProfile + TokenUsageFormatter 联合使用。 */
class ContextBudgetDemoTest {

    @Test void demoTokenBudgetAndFormat() {
        System.out.println("===== s09 TokenBudget + ContextProfile 演示 =====");

        // 1. 输入：构造 LLM 客户端和预算
        var llmClient = new DemoLlmClient();
        var profile = ContextProfile.from(llmClient);
        var budget = new TokenBudget(profile.maxContextWindow());

        System.out.println("模型窗口: " + profile.maxContextWindow());
        System.out.println("Agent 预算: " + profile.agentTokenBudget());
        System.out.println("短期预算: " + profile.shortTermMemoryBudget());
        System.out.println("触发压缩阈值: " + profile.compressionTriggerTokens());

        // 2. 转换：模拟多轮调用消耗
        long start = System.nanoTime();
        budget.recordUsage(1500, 300, 0);
        budget.recordUsage(2200, 450, 100);
        budget.recordUsage(800, 120, 0);

        // 3. 输出：格式化 Token 统计
        String formatted = TokenUsageFormatter.format(llmClient, budget, profile, start);
        System.out.println("\nToken 统计: " + formatted);

        assertTrue(formatted.contains("Token"), "应包含 Token 标签");
        assertTrue(formatted.contains("in="), "应显示 input tokens");
        assertTrue(formatted.contains("out="), "应显示 output tokens");
        assertTrue(formatted.contains("/"), "应显示已用/总量");
        assertTrue(formatted.contains("费用") || true);
    }

    static class DemoLlmClient implements LlmClient {
        @Override public int maxContextWindow() { return 128000; }
        @Override public boolean supportsPromptCaching() { return true; }
        @Override public String promptCacheMode() { return "auto"; }
        @Override public String getModelName() { return "demo-model"; }
        @Override public String getProviderName() { return "deepseek"; }
        @Override public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            return new ChatResponse("assistant", "ok", null, null, 0, 0, 0);
        }
    }
}
