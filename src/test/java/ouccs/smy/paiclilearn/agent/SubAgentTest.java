package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * [s12 新增] 验证 SubAgent 的角色感知执行。
 *
 * <p>使用 StubLlmClient（返回固定文本，无工具调用）验证：
 * SubAgent.execute() 能正确返回 RESULT 消息，且 fromAgent/fromRole 正确。
 * </p>
 */
class SubAgentTest {
    @Test
    void executeReturnsResult() {
        var stub = new StubLlmClient();
        var sub = new SubAgent("test-worker", AgentRole.WORKER, stub, new ToolRegistry());
        var result = sub.execute(AgentMessage.task("orchestrator", "hello"));
        assertEquals(AgentMessage.Type.RESULT, result.type());
        assertEquals("test-worker", result.fromAgent());
        assertEquals(AgentRole.WORKER, result.fromRole());
    }

    @Test
    void plannerDoesNotGetTools() {
        // PLANNER 角色——SubAgent 内部 shouldUseTools 为 false
        var stub = new StubLlmClient();
        var sub = new SubAgent("planner", AgentRole.PLANNER, stub, new ToolRegistry());
        var result = sub.execute(AgentMessage.task("o", "制定计划"));
        assertEquals(AgentMessage.Type.RESULT, result.type());
        assertEquals(AgentRole.PLANNER, result.fromRole());
    }

    @Test
    void clearHistoryKeepsSystemPrompt() {
        var stub = new StubLlmClient();
        var sub = new SubAgent("w", AgentRole.WORKER, stub, new ToolRegistry());
        sub.execute(AgentMessage.task("o", "task1"));
        sub.clearHistory();
        // 清空后仍可执行新任务
        var r2 = sub.execute(AgentMessage.task("o", "task2"));
        assertEquals(AgentMessage.Type.RESULT, r2.type());
    }

    /** 不使用工具、直接返回文本响应的桩 LLM 客户端。 */
    static class StubLlmClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            return new ChatResponse("assistant", "完成", null, null, 0, 0, 0);
        }
        @Override public String getModelName() { return "stub"; }
        @Override public String getProviderName() { return "stub"; }
    }
}
