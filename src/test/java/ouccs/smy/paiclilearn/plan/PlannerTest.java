package ouccs.smy.paiclilearn.plan;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s10 新增] 验证 Planner 的计划创建和 JSON 解析。 */
class PlannerTest {

    @Test void createMinimalPlanHasThreeTasks() {
        var planner = new Planner(new StubLlmClient("{}"));
        ExecutionPlan plan = planner.createMinimalPlan("编写代码");
        assertEquals(3, plan.tasks().size());
        assertTrue(plan.tasks().containsKey("task_1"));
        assertTrue(plan.tasks().containsKey("task_2"));
        assertTrue(plan.tasks().containsKey("task_3"));
    }

    @Test void minimalPlanHasTopologicalOrder() {
        var planner = new Planner(new StubLlmClient("{}"));
        ExecutionPlan plan = planner.createMinimalPlan("测试");
        boolean acyclic = plan.computeExecutionOrder();
        assertTrue(acyclic);
    }

    @Test void parsePlanFromJson() throws Exception {
        var planner = new Planner(new StubLlmClient("{}"));
        String json = """
                {
                  "summary": "编写一个简单的 Java 程序",
                  "tasks": [
                    {"id": "1", "description": "创建 Main.java", "type": "FILE_WRITE", "dependencies": []},
                    {"id": "2", "description": "编写 HelloWorld", "type": "FILE_WRITE", "dependencies": ["1"]},
                    {"id": "3", "description": "编译验证", "type": "VERIFICATION", "dependencies": ["2"]}
                  ]
                }
                """;

        ExecutionPlan plan = planner.parsePlan("编写 Java 程序", json);

        assertEquals(3, plan.tasks().size());
        assertTrue(plan.tasks().containsKey("task_1"));
        assertTrue(plan.tasks().containsKey("task_2"));
        assertTrue(plan.tasks().containsKey("task_3"));
        assertEquals("task_1", plan.tasks().get("task_2").dependencies().get(0));
    }

    @Test void parsePlanDetectsDagEdges() throws Exception {
        var planner = new Planner(new StubLlmClient("{}"));
        String json = """
                {
                  "summary": "测试计划",
                  "tasks": [
                    {"id": "a", "description": "A", "type": "ANALYSIS", "dependencies": []},
                    {"id": "b", "description": "B", "type": "FILE_WRITE", "dependencies": ["a"]},
                    {"id": "c", "description": "C", "type": "COMMAND", "dependencies": ["b"]}
                  ]
                }
                """;

        ExecutionPlan plan = planner.parsePlan("测试", json);
        var order = plan.getExecutionOrder();
        assertTrue(order.indexOf("task_1") < order.indexOf("task_2"));
        assertTrue(order.indexOf("task_2") < order.indexOf("task_3"));
    }

    @Test void parsePlanRejectsCycle() throws Exception {
        var planner = new Planner(new StubLlmClient("{}"));
        String json = """
                {
                  "summary": "循环计划",
                  "tasks": [
                    {"id": "1", "description": "A", "type": "COMMAND", "dependencies": ["2"]},
                    {"id": "2", "description": "B", "type": "COMMAND", "dependencies": ["1"]}
                  ]
                }
                """;

        assertThrows(java.io.IOException.class, () -> planner.parsePlan("循环", json));
    }

    @Test void taskTypeParsing() throws Exception {
        var planner = new Planner(new StubLlmClient("{}"));
        String json = """
                {
                  "summary": "类型测试",
                  "tasks": [
                    {"id": "1", "description": "读文件", "type": "FILE_READ", "dependencies": []},
                    {"id": "2", "description": "写文件", "type": "FILE_WRITE", "dependencies": []},
                    {"id": "3", "description": "执行命令", "type": "COMMAND", "dependencies": []},
                    {"id": "4", "description": "分析", "type": "ANALYSIS", "dependencies": []},
                    {"id": "5", "description": "验证", "type": "VERIFICATION", "dependencies": []}
                  ]
                }
                """;

        ExecutionPlan plan = planner.parsePlan("类型", json);
        assertEquals(Task.TaskType.FILE_READ, plan.tasks().get("task_1").type());
        assertEquals(Task.TaskType.FILE_WRITE, plan.tasks().get("task_2").type());
        assertEquals(Task.TaskType.COMMAND, plan.tasks().get("task_3").type());
        assertEquals(Task.TaskType.ANALYSIS, plan.tasks().get("task_4").type());
        assertEquals(Task.TaskType.VERIFICATION, plan.tasks().get("task_5").type());
    }

    // ========== Stub ==========

    static class StubLlmClient implements LlmClient {
        private final String response;
        StubLlmClient(String r) { this.response = r; }
        @Override public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            return new ChatResponse("assistant", response, null, null, 0, 0, 0);
        }
        @Override public String getModelName() { return "stub-plan"; }
        @Override public String getProviderName() { return "stub"; }
    }
}
