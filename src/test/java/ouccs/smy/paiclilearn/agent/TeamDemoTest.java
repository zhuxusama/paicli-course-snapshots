package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * [s12 新增] Multi-Agent 团队协作完整流程演示。
 *
 * <p>展示 Planner→Worker→Reviewer 三角色的消息流：
 * <ol>
 *   <li>【输入】用户目标 → Planner 生成 JSON 计划</li>
 *   <li>【转换】编排器解析 JSON → 按依赖分派给 Worker → Reviewer 审核</li>
 *   <li>【输出】所有步骤的执行状态汇总</li>
 * </ol>
 * </p>
 */
class TeamDemoTest {
    @Test
    void demoPlannerWorkerPipeline() {
        System.out.println("===== s12 Multi-Agent 团队演示 =====");

        System.out.println("【输入】用户目标: '创建登录页面'");
        System.out.println("  → Planner 生成 JSON 计划: 2 个步骤 (step_2 依赖 step_1)");

        var stub = new TeamDemoStubClient();
        var orch = new AgentOrchestrator(stub, new ToolRegistry());

        System.out.println("【转换】编排器解析计划 → 并行执行 ready 步骤 → Reviewer 审核");
        String result = orch.run("创建登录页面");

        System.out.println("【输出】执行结果汇总:");
        System.out.println(result);
        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("[step_1]"), "结果应包含 step_1");
        assertTrue(result.contains("[step_2]"), "结果应包含 step_2");

        System.out.println("===== 演示结束 =====");
    }

    @Test
    void demoSingleStepTask() {
        System.out.println("===== s12 单步骤任务演示 =====");
        System.out.println("【输入】简单任务: '查看项目结构'");

        var stub = new SingleStepStubClient();
        var orch = new AgentOrchestrator(stub, new ToolRegistry());

        System.out.println("【转换】单步骤无需依赖，直接执行");
        String result = orch.run("查看项目结构");

        System.out.println("【输出】");
        System.out.println(result);
        assertNotNull(result);
        assertTrue(result.contains("[step_1]"));

        System.out.println("===== 演示结束 =====");
    }

    /**
     * 返回 2 步骤 JSON 计划（有依赖关系）的桩 LLM。
     */
    static class TeamDemoStubClient implements LlmClient {
        private int callCount = 0;
        @Override
        public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            callCount++;
            if (callCount == 1) {
                return new ChatResponse("assistant",
                        "{\"tasks\":[" +
                        "{\"id\":\"1\",\"description\":\"设计页面结构\",\"type\":\"ANALYSIS\",\"dependencies\":[]}," +
                        "{\"id\":\"2\",\"description\":\"实现 HTML 代码\",\"type\":\"FILE_WRITE\",\"dependencies\":[\"1\"]}]}",
                        null, null, 0, 0, 0);
            }
            return new ChatResponse("assistant", "{\"approved\": true, \"summary\": \"通过\"}", null, null, 0, 0, 0);
        }
        @Override public String getModelName() { return "stub-team"; }
        @Override public String getProviderName() { return "stub"; }
    }

    /**
     * 返回单步骤简单计划的桩 LLM。
     */
    static class SingleStepStubClient implements LlmClient {
        private int callCount = 0;
        @Override
        public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            callCount++;
            if (callCount == 1) {
                return new ChatResponse("assistant",
                        "{\"tasks\":[" +
                        "{\"id\":\"1\",\"description\":\"列出目录结构\",\"type\":\"COMMAND\",\"dependencies\":[]}]}",
                        null, null, 0, 0, 0);
            }
            return new ChatResponse("assistant", "{\"approved\": true}", null, null, 0, 0, 0);
        }
        @Override public String getModelName() { return "stub-single"; }
        @Override public String getProviderName() { return "stub"; }
    }
}
