package ouccs.smy.paiclilearn.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * [s12 新增] 验证 AgentRole 枚举的三个角色和描述。
 */
class AgentRoleTest {
    @Test
    void hasThreeRoles() {
        assertEquals(3, AgentRole.values().length);
    }

    @Test
    void plannerDescription() {
        assertFalse(AgentRole.PLANNER.getDescription().isBlank());
        assertTrue(AgentRole.PLANNER.getDescription().contains("拆解"));
    }

    @Test
    void workerDescription() {
        assertFalse(AgentRole.WORKER.getDescription().isBlank());
        assertTrue(AgentRole.WORKER.getDescription().contains("执行"));
    }

    @Test
    void reviewerDescription() {
        assertFalse(AgentRole.REVIEWER.getDescription().isBlank());
        assertTrue(AgentRole.REVIEWER.getDescription().contains("检查"));
    }
}
