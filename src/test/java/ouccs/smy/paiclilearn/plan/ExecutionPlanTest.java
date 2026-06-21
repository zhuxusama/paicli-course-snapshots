package ouccs.smy.paiclilearn.plan;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s10 新增] 验证 ExecutionPlan 的 DAG 结构、拓扑排序和执行批次。 */
class ExecutionPlanTest {

    @Test void addTaskIncreasesCount() {
        var plan = new ExecutionPlan("p1", "测试");
        plan.addTask(new Task("t1", "任务1", Task.TaskType.COMMAND));
        assertEquals(1, plan.tasks().size());
    }

    @Test void topologicalOrderRespectsDependencies() {
        var plan = new ExecutionPlan("p1", "依赖测试");
        plan.addTask(new Task("t1", "第一步", Task.TaskType.ANALYSIS));
        plan.addTask(new Task("t2", "第二步", Task.TaskType.FILE_WRITE, List.of("t1")));
        plan.addTask(new Task("t3", "第三步", Task.TaskType.VERIFICATION, List.of("t2")));

        boolean acyclic = plan.computeExecutionOrder();
        assertTrue(acyclic);

        List<String> order = plan.getExecutionOrder();
        assertTrue(order.indexOf("t1") < order.indexOf("t2"),
                "t1 (分析) 应在 t2 (写入) 之前");
        assertTrue(order.indexOf("t2") < order.indexOf("t3"),
                "t2 (写入) 应在 t3 (验证) 之前");
    }

    @Test void cycleDetectionReturnsFalse() {
        var plan = new ExecutionPlan("p1", "循环测试");
        plan.addTask(new Task("t1", "A", Task.TaskType.COMMAND, List.of("t2")));
        plan.addTask(new Task("t2", "B", Task.TaskType.COMMAND, List.of("t1")));

        assertFalse(plan.computeExecutionOrder());
    }

    @Test void rootTasksHaveNoDependencies() {
        var plan = new ExecutionPlan("p1", "根测试");
        plan.addTask(new Task("t1", "根", Task.TaskType.ANALYSIS));
        plan.addTask(new Task("t2", "依赖", Task.TaskType.COMMAND, List.of("t1")));

        var roots = plan.getRootTasks();
        assertEquals(1, roots.size());
        assertEquals("t1", roots.get(0).id());
    }

    @Test void executableTasksExcludeBlockedOnes() {
        var plan = new ExecutionPlan("p1", "可执行测试");
        Task t1 = new Task("t1", "根", Task.TaskType.ANALYSIS);
        Task t2 = new Task("t2", "依赖", Task.TaskType.COMMAND, List.of("t1"));
        plan.addTask(t1);
        plan.addTask(t2);

        var executable = plan.getExecutableTasks();
        assertEquals(1, executable.size());
        assertEquals("t1", executable.get(0).id());
    }

    @Test void progressReportsCorrectFraction() {
        var plan = new ExecutionPlan("p1", "进度测试");
        plan.addTask(new Task("t1", "A", Task.TaskType.COMMAND));
        plan.addTask(new Task("t2", "B", Task.TaskType.COMMAND));

        assertEquals(0.0, plan.getProgress(), 0.01);
    }

    @Test void getExecutionBatchesProducesOrderedGroups() {
        var plan = new ExecutionPlan("p1", "批次测试");
        plan.addTask(new Task("t1", "A", Task.TaskType.ANALYSIS));
        plan.addTask(new Task("t2", "B", Task.TaskType.FILE_READ));
        plan.addTask(new Task("t3", "C", Task.TaskType.COMMAND, List.of("t1", "t2")));

        var batches = plan.getExecutionBatches();
        assertTrue(batches.size() >= 2, "应有至少 2 个批次");
        assertTrue(batches.get(0).contains("t1"), "第一批应有 t1");
        assertTrue(batches.get(0).contains("t2"), "第一批应有 t2");
    }

    @Test void summarizeContainsGoalAndTaskCount() {
        var plan = new ExecutionPlan("p1", "摘要测试");
        plan.addTask(new Task("t1", "任务1", Task.TaskType.COMMAND));
        plan.addTask(new Task("t2", "任务2", Task.TaskType.FILE_READ, List.of("t1")));

        String summary = plan.summarize();
        assertTrue(summary.contains("摘要测试"));
        assertTrue(summary.contains("t1"));
        assertTrue(summary.contains("t2"));
    }
}
