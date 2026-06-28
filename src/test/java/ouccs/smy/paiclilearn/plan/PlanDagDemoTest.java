package ouccs.smy.paiclilearn.plan;

import ouccs.smy.paiclilearn.memory.TokenBudget;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 教学演示：Task 状态机 → DAG → 拓扑序 → 执行批次。
 *
 * <p>展示"输入 → 转换 → 输出"的完整链路，使用源码 getter/setter API。</p>
 *
 * @since s10
 */
class PlanDagDemoTest {

    @Test
    void demoTaskStateMachineAndDag() {
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
        List<List<Task>> batches = plan.getExecutionBatches();
        System.out.println("执行批次: " + batches.stream()
                .map(b -> b.stream().map(Task::getId).toList())
                .toList());

        // 3. 输出：模拟执行
        for (int batchIdx = 0; batchIdx < batches.size(); batchIdx++) {
            List<Task> batch = batches.get(batchIdx);
            for (Task task : batch) {
                System.out.println("  执行 [" + batchIdx + "] " + task.getId()
                        + ": " + task.getDescription());
                task.markStarted();
                task.markCompleted(task.getDescription() + " 完成");
            }
        }
        System.out.println("最终进度: " + plan.getProgress());
        assertEquals(1.0, plan.getProgress(), 0.01);
        System.out.println("===== 演示结束 =====");
    }

    @Test
    void demoTaskStateTransitions() {
        System.out.println("===== Task 状态机演示 =====");

        Task task = new Task("t1", "验证状态", Task.TaskType.COMMAND);
        assertEquals(Task.TaskStatus.PENDING, task.getStatus());

        task.markStarted();
        assertEquals(Task.TaskStatus.RUNNING, task.getStatus());
        long startTime = task.getStartTime();
        assertTrue(startTime > 0, "markStarted 应设置 startTime");
        System.out.println("  startTime: " + startTime);

        task.markCompleted("完成");
        assertEquals(Task.TaskStatus.COMPLETED, task.getStatus());
        assertEquals("完成", task.getResult());
        long endTime = task.getEndTime();
        assertTrue(endTime >= startTime, "endTime >= startTime");
        System.out.println("  endTime: " + endTime);

        // 验证耗时
        assertTrue(task.getDuration() >= 0);
        System.out.println("  duration: " + task.getDuration() + "ms");

        // 验证 toString
        String str = task.toString();
        assertTrue(str.contains("t1"));
        assertTrue(str.contains("COMPLETED"));
        System.out.println("  toString: " + str);

        // FAILED → SKIPPED 状态路径
        task.markFailed("失败了");
        assertEquals(Task.TaskStatus.FAILED, task.getStatus());
        assertEquals("失败了", task.getError());

        task.markSkipped();
        assertEquals(Task.TaskStatus.SKIPPED, task.getStatus());

        System.out.println("PENDING → RUNNING → COMPLETED → FAILED → SKIPPED: ✅");
    }

    @Test
    void demoPlanMetadataAndDependencies() {
        System.out.println("===== Plan 元数据与依赖演示 =====");

        // 1. 输入：构造有依赖的任务
        Task t1 = new Task("t1", "下载依赖", Task.TaskType.COMMAND);
        Task t2 = new Task("t2", "编译项目", Task.TaskType.COMMAND, List.of("t1"));

        System.out.println("  t2 依赖: " + t2.getDependencies());
        assertTrue(t2.getDependencies().contains("t1"));

        // addDependency 动态添加依赖
        t1.addDependency("t0");
        assertTrue(t1.getDependencies().contains("t0"));

        // 2. 转换：创建 Plan 并设置元数据
        ExecutionPlan plan = new ExecutionPlan("plan_meta", "验证 Plan 元数据访问");
        plan.setSummary("这是一个包含两个任务的演示计划");
        plan.addTask(t1);
        plan.addTask(t2);

        System.out.println("  goal: " + plan.getGoal());
        System.out.println("  summary: " + plan.getSummary());
        assertEquals("验证 Plan 元数据访问", plan.getGoal());
        assertEquals("这是一个包含两个任务的演示计划", plan.getSummary());

        // 3. 输出：验证 getTask / getAllTasks
        assertEquals(2, plan.getAllTasks().size());
        assertNotNull(plan.getTask("t1"));
        assertNotNull(plan.getTask("t2"));

        // dependents 链
        System.out.println("  t1 的被依赖者: " + t1.getDependents());
        assertTrue(t1.getDependents().contains("t2"));

        // visualize 不抛异常
        String viz = plan.visualize();
        System.out.println("  visualize 长度: " + viz.length());
        assertTrue(viz.contains("验证 Plan 元数据访问"));
        assertTrue(viz.contains("t1"));
        assertTrue(viz.contains("t2"));
    }

    @Test
    void demoTokenBudgetContextWindow() {
        System.out.println("===== TokenBudget 窗口演示 =====");

        TokenBudget budget = new TokenBudget(128000);
        System.out.println("  窗口大小: " + budget.contextWindow());
        assertEquals(128000, budget.contextWindow());

        budget.recordUsage(4000, 800, 500);
        System.out.println("  存入 4000 输入后窗口大小: " + budget.contextWindow());
        assertEquals(128000, budget.contextWindow());

        System.out.println("  ✅ 窗口大小不受 usage 影响");
    }
}
