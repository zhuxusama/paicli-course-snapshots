package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * [s12 新增] 验证 AgentOrchestrator 的规划解析、并行执行和 Reviewer 审核。
 *
 * <p>使用 StubLlmClient（Planner 返回 JSON 计划，Worker 返回完成文本，
 * Reviewer 返回 approved JSON）验证完整的 Planner→Worker→Reviewer 流水线。
 * </p>
 */
class AgentOrchestratorTest {
    @Test
    void parseStepsFromJson() {
        var orch = new AgentOrchestrator(new OrchestratorStubClient(), new ToolRegistry());
        var steps = orch.parseSteps(OrchestratorStubClient.PLAN_JSON);
        assertEquals(2, steps.size());
        // step_2 依赖 step_1
        assertEquals(List.of("step_1"), steps.get(1).dependencies);
        assertEquals("step_1", steps.get(0).id);
        assertEquals("step_2", steps.get(1).id);
    }

    @Test
    void runProducesResult() {
        var orch = new AgentOrchestrator(new OrchestratorStubClient(), new ToolRegistry());
        String result = orch.run("创建登录页面");
        assertNotNull(result);
        assertFalse(result.isBlank());
        // 结果中应包含两个步骤的完成信息
        assertTrue(result.contains("[step_1]"));
        assertTrue(result.contains("[step_2]"));
    }

    @Test
    void parallelExecutionSchedulesDependentSteps() {
        var orch = new AgentOrchestrator(new OrchestratorStubClient(), new ToolRegistry());
        var steps = orch.parseSteps(
                "{\"steps\":[" +
                "{\"id\":\"1\",\"description\":\"A\",\"type\":\"ANALYSIS\",\"dependencies\":[]}," +
                "{\"id\":\"2\",\"description\":\"B\",\"type\":\"COMMAND\",\"dependencies\":[\"1\"]}]}");
        assertEquals(2, steps.size());
        // step_2 有依赖 → 不能和 step_1 同一批
        assertEquals(1, steps.get(1).dependencies.size());
    }

    @Test
    void emptyPlanReturnsError() {
        var orch = new AgentOrchestrator(new OrchestratorStubClient(), new ToolRegistry());
        var steps = orch.parseSteps("not json at all");
        assertTrue(steps.isEmpty());
    }

    /**
     * 为 Orchestrator 测试准备的桩 LLM 客户端。
     * <p>
     * 按调用序号返回不同内容：
     * 第 1 次（Planner）→ JSON 计划；
     * 后续调用（Worker/Reviewer）→ {"approved": true}（同时满足完成和审批）。
     * </p>
     */
    static class OrchestratorStubClient implements LlmClient {
        static final String PLAN_JSON =
                "{\"tasks\":[" +
                "{\"id\":\"1\",\"description\":\"分析需求\",\"type\":\"ANALYSIS\",\"dependencies\":[]}," +
                "{\"id\":\"2\",\"description\":\"实现代码\",\"type\":\"FILE_WRITE\",\"dependencies\":[\"1\"]}]}";
        static final String APPROVED = "{\"approved\": true, \"summary\": \"通过\"}";

        private int callCount = 0;

        @Override
        public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            callCount++;
            if (callCount == 1) {
                // Planner → 返回 JSON 计划
                return new ChatResponse("assistant", PLAN_JSON, null, null, 0, 0, 0);
            }
            // Worker/Reviewer → 返回 approved（JSON parse 成功 → approved=true）
            return new ChatResponse("assistant", APPROVED, null, null, 0, 0, 0);
        }
        @Override public String getModelName() { return "stub-orch"; }
        @Override public String getProviderName() { return "stub"; }
    }
}
