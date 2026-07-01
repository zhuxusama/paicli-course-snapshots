package ouccs.smy.paiclilearn.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * s10 的控制台教程测试：用一条完整任务流学习 Task、ExecutionPlan、DAG 排序和执行批次。
 * <p>
 * 这个测试不是只证明“能编译”。它会把输入、调用的 API、输出结构都打印出来，
 * 让学习者可以照着控制台结果理解本章新增模型应该怎么用。
 */
class PlanDagDemoTest {

    @Test
    void demoBuildAndRunTaskDag() {
        System.out.println("【场景】复杂目标不能只靠一轮 ReAct 硬想，需要先拆成有依赖关系的 Task DAG。");
        System.out.println("本章新增 Task 表示单个步骤，ExecutionPlan 表示整张有向无环图。");

        System.out.println();
        System.out.println("【输入】构造 4 个任务：先分析需求；设计方案和编写代码可并行；最后集成测试。");
        Task analyze = new Task("t1", "分析需求", Task.TaskType.ANALYSIS);
        Task design = new Task("t2", "设计方案", Task.TaskType.FILE_WRITE, List.of("t1"));
        Task code = new Task("t3", "编写代码", Task.TaskType.COMMAND, List.of("t1"));
        Task verify = new Task("t4", "集成测试", Task.TaskType.VERIFICATION, List.of("t2", "t3"));
        List<Task> tasks = List.of(analyze, design, code, verify);
        tasks.forEach(task -> System.out.println("  " + task.getId()
                + " | type=" + task.getType()
                + " | deps=" + task.getDependencies()
                + " | " + task.getDescription()));

        System.out.println();
        System.out.println("【执行】把任务加入 ExecutionPlan；addTask 会自动维护反向依赖 dependents。");
        ExecutionPlan plan = new ExecutionPlan("plan_demo", "构建一个可验证的功能切片");
        tasks.forEach(plan::addTask);
        System.out.println("  t1.dependents = " + analyze.getDependents());
        assertEquals(List.of("t2", "t3"), analyze.getDependents());

        System.out.println();
        System.out.println("【执行】调用 computeExecutionOrder() 得到拓扑序，确认这张图没有环。");
        boolean acyclic = plan.computeExecutionOrder();
        System.out.println("  acyclic = " + acyclic);
        System.out.println("  executionOrder = " + plan.getExecutionOrder());
        assertTrue(acyclic);
        assertEquals(List.of("t1", "t2", "t3", "t4"), plan.getExecutionOrder());

        System.out.println();
        System.out.println("【执行】调用 getExecutionBatches()，看哪些任务可以同批并行。");
        List<List<Task>> batches = plan.getExecutionBatches();
        List<List<String>> batchIds = batches.stream()
                .map(batch -> batch.stream().map(Task::getId).toList())
                .toList();
        System.out.println("  batches = " + batchIds);
        assertEquals(List.of(List.of("t1"), List.of("t2", "t3"), List.of("t4")), batchIds);

        System.out.println();
        System.out.println("【输出】模拟执行每个批次：任务状态从 PENDING -> RUNNING -> COMPLETED，计划进度逐步增加。");
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<Task> batch = batches.get(batchIndex);
            System.out.println("  batch " + batchIndex + " 可并行执行: "
                    + batch.stream().map(Task::getId).toList());
            for (Task task : batch) {
                task.markStarted();
                System.out.println("    started  " + task.getId() + " status=" + task.getStatus());
                task.markCompleted(task.getDescription() + "完成");
                System.out.println("    finished " + task.getId() + " result=" + task.getResult());
            }
            System.out.println("  progress = " + String.format("%.0f%%", plan.getProgress() * 100));
        }

        System.out.println();
        System.out.println("【观察】摘要适合给终端或下一章 PlanExecuteAgent 展示；它不会刷出一大坨图形。");
        System.out.println(plan.summarize());
        String visualized = plan.visualize();
        System.out.println("【观察】visualize() 适合调试完整 DAG，能看到每个任务的类型、依赖和状态。");
        System.out.println("  visualize 字符数 = " + visualized.length());
        System.out.println("  visualize 预览:");
        System.out.println(visualized.lines().limit(6).reduce("", (left, right) -> left + right + System.lineSeparator()));

        assertEquals(1.0, plan.getProgress(), 0.01);
        assertTrue(plan.isAllCompleted());
        assertTrue(visualized.contains("t1"));
        assertTrue(visualized.contains("t4"));
    }

    @Test
    void demoTaskStateMachine() {
        System.out.println("【场景】执行器需要知道一个任务处于等待、运行、完成、失败还是跳过。");

        System.out.println("【输入】创建一个 COMMAND 类型任务。");
        Task task = new Task("build", "运行 mvn test", Task.TaskType.COMMAND);
        System.out.println("  初始状态 = " + task.getStatus());
        assertEquals(Task.TaskStatus.PENDING, task.getStatus());

        System.out.println("【执行】依次调用 markStarted、markCompleted、markFailed、markSkipped。");
        task.markStarted();
        System.out.println("  markStarted  -> status=" + task.getStatus()
                + ", startTime=" + task.getStartTime());
        assertEquals(Task.TaskStatus.RUNNING, task.getStatus());
        assertTrue(task.getStartTime() > 0);

        task.markCompleted("测试通过");
        System.out.println("  markCompleted -> status=" + task.getStatus()
                + ", result=" + task.getResult()
                + ", duration=" + task.getDuration() + "ms");
        assertEquals(Task.TaskStatus.COMPLETED, task.getStatus());
        assertEquals("测试通过", task.getResult());

        task.markFailed("测试失败");
        System.out.println("  markFailed    -> status=" + task.getStatus()
                + ", error=" + task.getError());
        assertEquals(Task.TaskStatus.FAILED, task.getStatus());
        assertEquals("测试失败", task.getError());

        task.markSkipped();
        System.out.println("  markSkipped   -> status=" + task.getStatus());
        assertEquals(Task.TaskStatus.SKIPPED, task.getStatus());

        System.out.println("【输出】Task.toString() 给调试日志一个短摘要: " + task);
        assertTrue(task.toString().contains("build"));
        assertTrue(task.toString().contains("SKIPPED"));
    }

    @Test
    void demoCycleDetectionAndExecutableTasks() {
        System.out.println("【场景】Plan 是 DAG，不允许 A 依赖 B、B 又依赖 A；否则执行器永远找不到起点。");

        System.out.println("【输入】先构造一个合法计划，看 getExecutableTasks() 如何只返回依赖已满足的任务。");
        ExecutionPlan validPlan = new ExecutionPlan("valid", "验证可执行任务");
        Task read = new Task("read", "读取 pom.xml", Task.TaskType.FILE_READ);
        Task summarize = new Task("summarize", "总结项目结构", Task.TaskType.ANALYSIS, List.of("read"));
        validPlan.addTask(read);
        validPlan.addTask(summarize);
        System.out.println("  初始可执行 = " + validPlan.getExecutableTasks().stream().map(Task::getId).toList());
        assertEquals(List.of(read), validPlan.getExecutableTasks());

        System.out.println("【执行】完成 read 后，summarize 才会变成可执行。");
        read.markCompleted("pom.xml 已读取");
        System.out.println("  完成 read 后可执行 = "
                + validPlan.getExecutableTasks().stream().map(Task::getId).toList());
        assertEquals(List.of(summarize), validPlan.getExecutableTasks());

        System.out.println("【输入】再构造一个有环计划：a 依赖 b，b 依赖 a。");
        ExecutionPlan cyclePlan = new ExecutionPlan("cycle", "错误的循环依赖");
        cyclePlan.addTask(new Task("a", "任务 A", Task.TaskType.COMMAND, List.of("b")));
        cyclePlan.addTask(new Task("b", "任务 B", Task.TaskType.COMMAND, List.of("a")));

        System.out.println("【执行】调用 computeExecutionOrder()。");
        boolean acyclic = cyclePlan.computeExecutionOrder();

        System.out.println("【输出】acyclic = " + acyclic + "，说明计划生成器必须拒绝或重写这类计划。");
        assertFalse(acyclic);
    }
}
