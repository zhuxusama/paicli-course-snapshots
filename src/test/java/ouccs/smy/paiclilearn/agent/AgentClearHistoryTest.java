package ouccs.smy.paiclilearn.agent;

import org.junit.jupiter.api.Test;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link Agent#clearHistory()} 的边界行为。
 * <p>
 * 教学目的：理解 Agent 的对话历史管理——clearHistory 应重置对话
 * 但保留 system prompt，方便复用同一 Agent 实例开启新话题。</p>
 */
class AgentClearHistoryTest {

    /** 验证 clearHistory 后对话历史只剩 system 消息。 */
    @Test
    void clearHistoryResetsConversation() throws IOException {
        // 1. 输入：构造 Agent + StubLlmClient（固定返回一句话）
        var stub = new StubLlmClient("你好，我是助手。");
        var agent = new Agent(stub);

        // 验证初始状态：只有 system 消息
        assertEquals(1, agent.getConversationHistory().size(),
                "初始化后应只有 system 消息");
        assertEquals("system", agent.getConversationHistory().get(0).role());

        // 2. 转换：执行一轮对话
        String result = agent.run("今天天气怎么样？");

        // 验证 run 之后有 user + assistant
        assertTrue(agent.getConversationHistory().size() >= 3,
                "一轮对话后应包含 system + user + assistant");

        // 3. 清空
        agent.clearHistory();

        // 4. 输出：验证只剩下 system
        assertEquals(1, agent.getConversationHistory().size(),
                "clearHistory 后应只剩 system 消息");
        assertEquals("system", agent.getConversationHistory().get(0).role());

        // 清空后仍可继续对话
        String secondResult = agent.run("再来一次");
        assertNotNull(secondResult);
        System.out.println("第二次对话结果: " + secondResult);
    }

    /** 验证多次 clearHistory 不会累积 system 消息。 */
    @Test
    void multipleClearHistoryDoesNotDuplicateSystem() {
        var stub = new StubLlmClient("ok");
        var agent = new Agent(stub);

        agent.clearHistory();
        assertEquals(1, agent.getConversationHistory().size(),
                "第一次 clear 后应只有 1 条 system");

        agent.clearHistory();
        assertEquals(1, agent.getConversationHistory().size(),
                "第二次 clear 后 system 不应累积");
    }

    /** 验证 run 之后 statusSummary 反映 history 增长，clear 后回退。 */
    @Test
    void statusSummaryAfterClear() throws IOException {
        var stub = new StubLlmClient("ok");
        var agent = new Agent(stub);

        // 先执行一轮对话，history 增长到 3+
        agent.run("hello");
        String afterRun = agent.statusSummary();
        System.out.println("run 后: " + afterRun);
        assertTrue(afterRun.contains("history=3") || afterRun.contains("history=5"),
                "run 后 history 应 > 1, 实际: " + afterRun);

        // clear 后回退到 1
        agent.clearHistory();
        String afterClear = agent.statusSummary();
        System.out.println("clear 后: " + afterClear);
        assertTrue(afterClear.contains("history=1"),
                "clear 后 history 应 = 1, 实际: " + afterClear);
    }

    // ========== StubLlmClient ==========

    /** 用于测试的占位模型客户端——固定返回预设 content。 */
    static class StubLlmClient implements LlmClient {
        private final String fixedContent;
        StubLlmClient(String content) { this.fixedContent = content; }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            // 简单模拟：遍历 messages 最后一条，然后返回固定回复
            return new ChatResponse("assistant", fixedContent, null, null, 10, 20, 0);
        }

        @Override
        public String getModelName() { return "stub-model"; }

        @Override
        public String getProviderName() { return "stub"; }
    }
}
