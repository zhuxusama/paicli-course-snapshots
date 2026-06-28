package ouccs.smy.paiclilearn.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * [s12 新增] 验证 AgentMessage 的六种消息类型和工厂方法。
 */
class AgentMessageTest {
    @Test
    void task() {
        var m = AgentMessage.task("orchestrator", "做X");
        assertEquals(AgentMessage.Type.TASK, m.type());
        assertEquals("orchestrator", m.fromAgent());
        assertEquals("做X", m.content());
    }

    @Test
    void result() {
        var m = AgentMessage.result("worker-1", AgentRole.WORKER, "完成了");
        assertEquals(AgentMessage.Type.RESULT, m.type());
        assertEquals(AgentRole.WORKER, m.fromRole());
        assertEquals("完成了", m.content());
    }

    @Test
    void feedback() {
        var m = AgentMessage.feedback("reviewer", "需要改进格式");
        assertEquals(AgentMessage.Type.FEEDBACK, m.type());
        assertEquals(AgentRole.REVIEWER, m.fromRole());
    }

    @Test
    void approval() {
        var m = AgentMessage.approval("reviewer", "通过");
        assertEquals(AgentMessage.Type.APPROVAL, m.type());
        assertEquals(AgentRole.REVIEWER, m.fromRole());
    }

    @Test
    void rejection() {
        var m = AgentMessage.rejection("reviewer", "结果有误");
        assertEquals(AgentMessage.Type.REJECTION, m.type());
        assertEquals(AgentRole.REVIEWER, m.fromRole());
    }

    @Test
    void error() {
        var m = AgentMessage.error("worker-2", AgentRole.WORKER, "LLM 调用失败");
        assertEquals(AgentMessage.Type.ERROR, m.type());
        assertEquals("worker-2", m.fromAgent());
        assertEquals(AgentRole.WORKER, m.fromRole());
        assertEquals("LLM 调用失败", m.content());
    }
}
