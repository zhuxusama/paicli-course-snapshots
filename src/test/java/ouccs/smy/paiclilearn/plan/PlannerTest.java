package ouccs.smy.paiclilearn.plan;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Planner 的计划创建、JSON 解析和 replan 行为。
 * 以原项目 {@code com.paicli.plan.PlannerTest} 为行为基线。
 *
 * @since s10
 */
class PlannerTest {

    @Test
    void createsMinimalPlanForSimpleGoalWithoutCallingLlm() throws Exception {
        Planner planner = new Planner(new FailingStubClient());

        ExecutionPlan plan = planner.createPlan("列出当前目录的文件");

        assertEquals("直接执行简单任务：列出当前目录的文件", plan.getSummary());
        assertEquals(List.of("task_1"), plan.getExecutionOrder());
        Task task = plan.getTask("task_1");
        assertEquals(Task.TaskType.COMMAND, task.getType());
        assertEquals("列出当前目录的文件", task.getDescription());
    }

    @Test
    void delegatesComplexGoalToLlmPlannerPath() throws Exception {
        Planner planner = new Planner(new StubLlmClient("""
                {
                  "summary": "复杂任务",
                  "tasks": [
                    {
                      "id": "task_a",
                      "description": "先读取 pom.xml",
                      "type": "FILE_READ",
                      "dependencies": []
                    },
                    {
                      "id": "task_b",
                      "description": "再验证项目结构",
                      "type": "VERIFICATION",
                      "dependencies": ["task_a"]
                    }
                  ]
                }
                """));

        ExecutionPlan plan = planner.createPlan("先读取 pom.xml 然后验证项目结构");

        assertEquals("复杂任务", plan.getSummary());
        assertEquals(2, plan.getAllTasks().size());
        assertTrue(plan.getTask("task_2").getDependencies().contains("task_1"));
    }

    @Test
    void parsePlanFromJson() throws Exception {
        Planner planner = new Planner(new StubLlmClient("{}"));
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

        assertEquals(3, plan.getAllTasks().size());
        assertNotNull(plan.getTask("task_1"));
        assertNotNull(plan.getTask("task_2"));
        assertNotNull(plan.getTask("task_3"));
        assertTrue(plan.getTask("task_2").getDependencies().contains("task_1"));
    }

    @Test
    void parsePlanDetectsDagEdges() throws Exception {
        Planner planner = new Planner(new StubLlmClient("{}"));
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
        List<String> order = plan.getExecutionOrder();
        assertTrue(order.indexOf("task_1") < order.indexOf("task_2"));
        assertTrue(order.indexOf("task_2") < order.indexOf("task_3"));
    }

    @Test
    void parsePlanRejectsCycle() {
        Planner planner = new Planner(new StubLlmClient("{}"));
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

    @Test
    void taskTypeParsing() throws Exception {
        Planner planner = new Planner(new StubLlmClient("{}"));
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
        assertEquals(Task.TaskType.FILE_READ, plan.getTask("task_1").getType());
        assertEquals(Task.TaskType.FILE_WRITE, plan.getTask("task_2").getType());
        assertEquals(Task.TaskType.COMMAND, plan.getTask("task_3").getType());
        assertEquals(Task.TaskType.ANALYSIS, plan.getTask("task_4").getType());
        assertEquals(Task.TaskType.VERIFICATION, plan.getTask("task_5").getType());
    }

    // ========== Stubs ==========

    /** 任何调用都失败——验证简单目标不会触发 LLM 调用。 */
    private static final class FailingStubClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener)
                throws IOException {
            throw new IOException("simple goal should not call llm");
        }

        @Override
        public String getModelName() { return "stub-fail"; }

        @Override
        public String getProviderName() { return "stub"; }
    }

    /** 返回固定 JSON 的桩客户端——验证复杂目标的 LLM 规划路径。 */
    static class StubLlmClient implements LlmClient {
        private final String response;

        StubLlmClient(String response) { this.response = response; }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return new ChatResponse("assistant", response, null, null, 0, 0, 0);
        }

        @Override
        public String getModelName() { return "stub-plan"; }

        @Override
        public String getProviderName() { return "stub"; }
    }
}
