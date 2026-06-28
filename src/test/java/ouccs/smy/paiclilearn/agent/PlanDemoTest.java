package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.cli.PlanReviewInputParser.Decision;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.plan.ExecutionPlan;
import ouccs.smy.paiclilearn.plan.Task;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s11 新增] 教学演示：Plan+Execute 完整闭环。 */
class PlanDemoTest {

    @Test void demoPlanAndExecute() {
        System.out.println("===== s11 Plan+Execute 演示 =====");
        var a = new PlanExecuteAgent(new StubClient("done"));
        String r = a.run("列出文件");
        System.out.println("结果: " + r);
        assertNotNull(r);
        assertFalse(r.contains("❌"));
        System.out.println("===== 结束 =====");
    }

    @Test void demoReviewWithSupplement() {
        System.out.println("===== 审阅闭环演示 =====");
        var h = new PlanExecuteAgent.PlanReviewHandler() {
            int c;
            public Decision review(String g, ExecutionPlan p) {
                c++;
                if (c == 1) return Decision.supplement("加测试");
                return Decision.execute();
            }
        };
        var a = new PlanExecuteAgent(new StubClient("{}"), h);
        String r = a.run("先读文件然后验证");
        System.out.println("最终: " + r);
        assertNotNull(r);
    }

    @Test void demoTaskDagTopology() {
        System.out.println("===== DAG 拓扑演示 =====");
        ExecutionPlan p = new ExecutionPlan("d1", "验证调度");
        Task t1 = new Task("t1", "读 pom.xml", Task.TaskType.FILE_READ);
        Task t2 = new Task("t2", "分析依赖", Task.TaskType.ANALYSIS, List.of("t1"));
        p.addTask(t1);
        p.addTask(t2);
        p.setSummary("线性 DAG");
        System.out.println("拓扑序: " + p.getExecutionOrder());
        System.out.println("可执行: " + p.getExecutableTasks().stream()
                .map(Task::getId).toList());
        assertEquals("t1", p.getExecutableTasks().get(0).getId());
    }

    @Test void demoCancel() {
        System.out.println("===== 取消演示 =====");
        var a = new PlanExecuteAgent(new StubClient("{}"),
                (g, p) -> Decision.cancel());
        assertTrue(a.run("any").contains("取消"));
    }

    static class StubClient implements LlmClient {
        final String c;
        StubClient(String c) { this.c = c; }
        public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            return new ChatResponse("assistant", c, null, null, 0, 0, 0);
        }
        public String getModelName() { return "stub"; }
        public String getProviderName() { return "stub"; }
    }
}
