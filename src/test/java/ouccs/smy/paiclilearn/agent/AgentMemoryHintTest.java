package ouccs.smy.paiclilearn.agent;

import org.junit.jupiter.api.Test;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Agent 对话历史中"记忆相关"的保留行为。
 * <p>
 * 教学目的：理解 conversationHistory 的结构——
 * system prompt 始终是第一条消息，对话消息在其后累积，
 * clearHistory 清空所有对话消息但重新创建 system prompt。</p>
 * <p>
 * s03 尚未引入持久化记忆组件，
 * 本章通过验证 system prompt 的持久性来模拟"记忆提示不丢失"这一概念。</p>
 */
class AgentMemoryHintTest {

    /** 验证 system prompt 始终是第一条消息且在多次对话后仍为首位。 */
    @Test
    void systemPromptAlwaysFirst() throws IOException {
        var stub = new StubLlm("回答");
        var agent = new Agent(stub);

        // 第一次对话
        agent.run("你好");
        assertEquals("system", agent.getConversationHistory().get(0).role(),
                "system 应始终是第一条消息");

        // 第二次对话（累积）
        agent.run("继续");

        // system 仍是第一条
        assertEquals("system", agent.getConversationHistory().get(0).role(),
                "多次对话后 system 仍应为第一条");

        System.out.println("对话消息数: " + agent.getConversationHistory().size());
        System.out.println("system: " + agent.getConversationHistory().get(0).content());
    }

    /** 验证清空后 system prompt 被重建。 */
    @Test
    void clearPreservesSystemPrompt() {
        var stub = new StubLlm("回答");
        var agent = new Agent(stub);

        agent.clearHistory();
        assertEquals(1, agent.getConversationHistory().size(),
                "clear 后应只有一条 system 消息");
        assertEquals("system", agent.getConversationHistory().get(0).role(),
                "clear 后第一条应为 system");

        // system 内容非空
        assertFalse(agent.getConversationHistory().get(0).content().isBlank(),
                "system 内容不应为空");
    }

    /** 验证对话消息累积：多轮对话后 history size 增长。 */
    @Test
    void conversationGrowsAcrossTurns() throws IOException {
        var stub = new StubLlm("回答");
        var agent = new Agent(stub);

        int initialSize = agent.getConversationHistory().size();
        assertEquals(1, initialSize, "初始只有 system");

        agent.run("第一问");
        int afterFirst = agent.getConversationHistory().size();
        System.out.println("第一问后 history size: " + afterFirst);

        agent.run("第二问");
        int afterSecond = agent.getConversationHistory().size();
        System.out.println("第二问后 history size: " + afterSecond);

        assertTrue(afterSecond > afterFirst,
                "多轮对话应累积更多消息");
    }

    /** 验证 statusSummary 包含 provider/model/history 信息。 */
    @Test
    void statusSummaryIncludesAgentInfo() throws IOException {
        var stub = new StubLlm("ok");
        var agent = new Agent(stub);

        String summary = agent.statusSummary();
        System.out.println("Agent 状态: " + summary);

        assertTrue(summary.contains("stub-mem"),
                "statusSummary 应包含 provider");
        assertTrue(summary.contains("stub-model"),
                "statusSummary 应包含 model");
    }

    // ========== StubLlmClient ==========

    static class StubLlm implements LlmClient {
        private final String response;
        StubLlm(String response) { this.response = response; }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            listener.onContentDelta(response);
            return new ChatResponse("assistant", response, null, null, 10, 10, 0);
        }

        @Override
        public String getModelName() { return "stub-model"; }

        @Override
        public String getProviderName() { return "stub-mem"; }
    }
}
