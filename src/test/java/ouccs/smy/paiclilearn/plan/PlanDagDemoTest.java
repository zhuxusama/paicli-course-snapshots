package ouccs.smy.paiclilearn.plan;

import ouccs.smy.paiclilearn.memory.TokenBudget;
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

    @Test void demoTaskStateTransitions() {
        System.out.println("===== Task 状态机演示 =====");

        Task task = new Task("t1", "验证状态", Task.TaskType.COMMAND);
        assertEquals(Task.TaskStatus.PENDING, task.status());

        task.markStarted();
        assertEquals(Task.TaskStatus.RUNNING, task.status());
        // s10 修复: 验证 startTime getter
        System.out.println("  startTime: " + task.startTime());

        task.markCompleted("完成");
        assertEquals(Task.TaskStatus.COMPLETED, task.status());
        // s10 修复: 验证 endTime getter
        System.out.println("  endTime: " + task.endTime());
        assertTrue(task.endTime() >= task.startTime(), "endTime >= startTime");

        task.markFailed("失败了");
        assertEquals(Task.TaskStatus.FAILED, task.status());
        assertEquals("失败了", task.error());

        task.markSkipped();
        assertEquals(Task.TaskStatus.SKIPPED, task.status());

        System.out.println("PENDING → RUNNING → COMPLETED → FAILED → SKIPPED: ✓");
    }

    @Test void demoPlanMetadataAndDependencies() {
        System.out.println("===== Plan 元数据与依赖演示 =====");

        // 1. 输入：构造有依赖的任务
        Task t1 = new Task("t1", "下载依赖", Task.TaskType.COMMAND);
        Task t2 = new Task("t2", "编译项目", Task.TaskType.COMMAND, List.of("t1"));

        // s10 修复: dependents() 展示依赖链
        System.out.println("  t2 依赖: " + t2.dependents());
        assertTrue(t2.dependents().contains("t1"));

        // 2. 转换：创建 Plan 并设置元数据
        ExecutionPlan plan = new ExecutionPlan("plan_meta", "验证 Plan 元数据访问");
        plan.setSummary("这是一个包含两个任务的演示计划");
        plan.addTask(t1);
        plan.addTask(t2);

        // s10 修复: goal() + summary() 展示 Plan 级元数据
        System.out.println("  goal: " + plan.goal());
        System.out.println("  summary: " + plan.summary());
        assertEquals("验证 Plan 元数据访问", plan.goal());
        assertEquals("这是一个包含两个任务的演示计划", plan.summary());

        // 3. 输出：验证
        System.out.println("  任务数: " + plan.tasks().size());
        assertEquals(2, plan.tasks().size());
    }

    @Test void demoTokenBudgetContextWindow() {
        System.out.println("===== TokenBudget 窗口演示 =====");

        // s10 修复: contextWindow() 展示窗口大小读取
        TokenBudget budget = new TokenBudget(128000);
        System.out.println("  窗口大小: " + budget.contextWindow());
        assertEquals(128000, budget.contextWindow());

        // 存入一些用量后检查窗口仍不变
        budget.recordUsage(4000, 800, 500);
        System.out.println("  存入 4000 输入后窗口大小: " + budget.contextWindow());
        assertEquals(128000, budget.contextWindow());

        System.out.println("  ✓ 窗口大小不受 usage 影响");
    }
}
