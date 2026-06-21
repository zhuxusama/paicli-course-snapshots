package ouccs.smy.paiclilearn.plan;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s10 新增] 教学演示：Task 状态机 → DAG → 拓扑序 → 执行批次。 */
class PlanDagDemoTest {

    @Test void demoTaskStateMachineAndDag() {
        System.out.println("===== s10 Task DAG 演示 =====");

        // 1. 输入：定义 4 个任务形成 DAG
        Task t1 = new Task("t1", "分析需求", Task.TaskType.ANALYSIS);
        Task t2 = new Task("t2", "设计方案", Task.TaskType.FILE_WRITE, List.of("t1"));
        Task t3 = new Task("t3", "编写代码", Task.TaskType.COMMAND, List.of("t1"));
        Task t4 = new Task("t4", "集成测试", Task.TaskType.VERIFICATION, List.of("t2", "t3"));

        // 2. 转换：构建 DAG
        ExecutionPlan plan = new ExecutionPlan("plan_demo", "构建 Demo");
        plan.addTask(t1);
        plan.addTask(t2);
        plan.addTask(t3);
        plan.addTask(t4);

        System.out.println("DAG 摘要:");
        System.out.println(plan.summarize());

        // 拓扑排序
        boolean acyclic = plan.computeExecutionOrder();
        System.out.println("拓扑序: " + plan.getExecutionOrder() + " (无环=" + acyclic + ")");
        assertTrue(acyclic);

        // 执行批次
        var batches = plan.getExecutionBatches();
        System.out.println("执行批次: " + batches);

        // 3. 输出：模拟执行
        for (int batchIdx = 0; batchIdx < batches.size(); batchIdx++) {
            List<String> batch = batches.get(batchIdx);
            for (String taskId : batch) {
                Task task = plan.tasks().get(taskId);
                System.out.println("  执行 [" + batchIdx + "] " + task.id() + ": " + task.description());
                task.markStarted();
                // 模拟任务执行
                task.markCompleted(task.description() + " 完成");
            }
        }
        System.out.println("最终进度: " + plan.getProgress());
        assertEquals(1.0, plan.getProgress(), 0.01);
        System.out.println("===== 演示结束 =====");
    }

    @Test void demoTaskStatusTransitions() {
        System.out.println("===== Task 状态机演示 =====");

        Task task = new Task("t1", "验证状态", Task.TaskType.COMMAND);
        assertEquals(Task.TaskStatus.PENDING, task.status());

        task.markStarted();
        assertEquals(Task.TaskStatus.RUNNING, task.status());

        task.markCompleted("完成");
        assertEquals(Task.TaskStatus.COMPLETED, task.status());

        task.markFailed("失败了");
        assertEquals(Task.TaskStatus.FAILED, task.status());
        assertEquals("失败了", task.error());

        task.markSkipped();
        assertEquals(Task.TaskStatus.SKIPPED, task.status());

        System.out.println("PENDING → RUNNING → COMPLETED → FAILED → SKIPPED: ✓");
    }
}
