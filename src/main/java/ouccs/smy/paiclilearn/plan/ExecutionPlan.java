package ouccs.smy.paiclilearn.plan;

import java.util.*;

/**
 * 执行计划——包含一组有依赖关系的 Task 节点组成的 DAG。
 *
 * <p>通过 {@link #addTask(Task)} 添加节点并自动维护依赖链；
 * {@link #computeExecutionOrder()} 进行拓扑排序（含环检测）；
 * {@link #getExecutionBatches()} 按 DAG 层次分批输出可并行批次。</p>
 *
 * <p>直接移植自原项目 {@code com.paicli.plan.ExecutionPlan}，仅调整课程包名。
 * 保留源项目的完整 API、状态机、依赖维护、可视化和摘要。</p>
 *
 * @since s10
 */
public class ExecutionPlan {

    /** 计划状态机——CREATED→RUNNING→COMPLETED/FAILED/CANCELLED。 @since s10 */
    public enum PlanStatus {
        CREATED,      // 刚创建
        RUNNING,      // 执行中
        COMPLETED,    // 全部完成
        FAILED,       // 有任务失败
        CANCELLED     // 被取消
    }

    private final String id;
    private final String goal;                    // 计划目标
    private final Map<String, Task> tasks;        // 所有任务（保持插入顺序）
    private final List<String> executionOrder;    // 拓扑排序后的执行顺序
    private PlanStatus status;
    private String summary;                       // 计划摘要
    private long startTime;
    private long endTime;

    public ExecutionPlan(String id, String goal) {
        this.id = id;
        this.goal = goal;
        this.tasks = new LinkedHashMap<>();
        this.executionOrder = new ArrayList<>();
        this.status = PlanStatus.CREATED;
    }

    // ========== Getters ==========

    public String getId() { return id; }

    public String getGoal() { return goal; }

    public PlanStatus getStatus() { return status; }

    public String getSummary() { return summary; }

    public long getStartTime() { return startTime; }

    public long getEndTime() { return endTime; }

    // ========== Setters ==========

    public void setSummary(String summary) { this.summary = summary; }

    public void setStatus(PlanStatus status) { this.status = status; }

    // ========== 任务管理 ==========

    /**
     * 添加任务并自动建立双向依赖关系。
     */
    public void addTask(Task task) {
        tasks.put(task.getId(), task);
        // 更新依赖关系——为前置任务注册反向依赖
        for (String depId : task.getDependencies()) {
            Task dep = tasks.get(depId);
            if (dep != null) {
                dep.addDependent(task.getId());
            }
        }
    }

    /** 按 ID 获取单个任务。 */
    public Task getTask(String id) {
        return tasks.get(id);
    }

    /** 获取所有任务（保持插入顺序）。 */
    public Collection<Task> getAllTasks() {
        return tasks.values();
    }

    /** 根任务——没有前置依赖的任务。 */
    public List<Task> getRootTasks() {
        return tasks.values().stream()
                .filter(t -> t.getDependencies().isEmpty())
                .toList();
    }

    /** 当前可执行的任务——PENDING 且所有前置已完成。 */
    public List<Task> getExecutableTasks() {
        return tasks.values().stream()
                .filter(t -> t.isExecutable(tasks))
                .toList();
    }

    // ========== 拓扑排序 ==========

    /**
     * 计算拓扑排序执行顺序（含环检测）。
     * @return true 表示无环，false 表示有环
     */
    public boolean computeExecutionOrder() {
        executionOrder.clear();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (Task task : tasks.values()) {
            if (!visited.contains(task.getId())) {
                if (!topologicalSort(task, visited, visiting)) {
                    return false;  // 有环
                }
            }
        }
        return true;
    }

    private boolean topologicalSort(Task task, Set<String> visited, Set<String> visiting) {
        String id = task.getId();

        if (visiting.contains(id)) {
            return false;  // 有环
        }
        if (visited.contains(id)) {
            return true;
        }

        visiting.add(id);

        for (String depId : task.getDependencies()) {
            Task dep = tasks.get(depId);
            if (dep != null) {
                if (!topologicalSort(dep, visited, visiting)) {
                    return false;
                }
            }
        }

        visiting.remove(id);
        visited.add(id);
        executionOrder.add(id);
        return true;
    }

    /** 获取执行顺序（懒计算）。 */
    public List<String> getExecutionOrder() {
        if (executionOrder.isEmpty()) {
            computeExecutionOrder();
        }
        return new ArrayList<>(executionOrder);
    }

    // ========== 批次与进度 ==========

    /**
     * 按 DAG 层次输出可并行执行的批次。
     * @return 每批 task 列表，同一批内的任务可并行执行
     */
    public List<List<Task>> getExecutionBatches() {
        if (tasks.isEmpty()) {
            return List.of();
        }

        Map<String, Task> remaining = new LinkedHashMap<>(tasks);
        Set<String> completed = new HashSet<>();
        List<List<Task>> batches = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<Task> batch = remaining.values().stream()
                    .filter(task -> completed.containsAll(task.getDependencies()))
                    .toList();

            if (batch.isEmpty()) {
                break;  // 无法继续（含环或死锁）
            }

            batches.add(batch);
            for (Task task : batch) {
                remaining.remove(task.getId());
                completed.add(task.getId());
            }
        }

        return batches;
    }

    /** 任务完成比例（0.0 ~ 1.0）。 */
    public double getProgress() {
        if (tasks.isEmpty()) return 1.0;
        long completed = tasks.values().stream()
                .filter(t -> t.getStatus() == Task.TaskStatus.COMPLETED)
                .count();
        return (double) completed / tasks.size();
    }

    /** 是否全部完成。 */
    public boolean isAllCompleted() {
        return tasks.values().stream()
                .allMatch(t -> t.getStatus() == Task.TaskStatus.COMPLETED);
    }

    /** 是否有失败任务。 */
    public boolean hasFailed() {
        return tasks.values().stream()
                .anyMatch(t -> t.getStatus() == Task.TaskStatus.FAILED);
    }

    // ========== 状态迁移 ==========

    /** 标记开始执行。 */
    public void markStarted() {
        this.status = PlanStatus.RUNNING;
        this.startTime = System.currentTimeMillis();
    }

    /** 标记完成。 */
    public void markCompleted() {
        this.status = PlanStatus.COMPLETED;
        this.endTime = System.currentTimeMillis();
    }

    /** 标记失败。 */
    public void markFailed() {
        this.status = PlanStatus.FAILED;
        this.endTime = System.currentTimeMillis();
    }

    /** 获取计划总耗时（毫秒）。 */
    public long getDuration() {
        if (startTime == 0) return 0;
        if (endTime == 0) return System.currentTimeMillis() - startTime;
        return endTime - startTime;
    }

    // ========== 展示 ==========

    /**
     * 可视化计划——输出 ASCII 表格。
     */
    public String visualize() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  执行计划: %-46s║%n",
                goal.length() > 46 ? goal.substring(0, 43) + "..." : goal));
        sb.append("╠══════════════════════════════════════════════════════════╣\n");

        List<String> order = getExecutionOrder();
        for (int i = 0; i < order.size(); i++) {
            String taskId = order.get(i);
            Task task = tasks.get(taskId);
            String statusIcon = getStatusIcon(task.getStatus());
            String deps = task.getDependencies().isEmpty() ? "无"
                    : String.join(",", task.getDependencies());

            sb.append(String.format("║  %d. %s %-20s ", i + 1, statusIcon, task.getId()));
            sb.append(String.format("[%-10s] 依赖: %-15s║%n",
                    task.getType(), deps));
            String desc = task.getDescription().length() > 50
                    ? task.getDescription().substring(0, 47) + "..."
                    : task.getDescription();
            sb.append(String.format("║     %-53s║%n", desc));
        }

        sb.append("╚══════════════════════════════════════════════════════════╝\n");
        sb.append(String.format("   进度: %.0f%% | 状态: %s%n",
                getProgress() * 100, status));
        return sb.toString();
    }

    /**
     * 紧凑摘要——不占满终端，适合嵌入流式输出或状态栏。
     */
    public String summarize() {
        List<List<Task>> batches = getExecutionBatches();
        List<Task> readyTasks = getExecutableTasks();
        StringBuilder sb = new StringBuilder();
        sb.append("📋 计划摘要\n");
        sb.append("   - 目标: ").append(compactGoal(goal, 48)).append('\n');
        sb.append("   - 任务数: ").append(tasks.size())
                .append(" | 并行批次: ").append(batches.size())
                .append(" | 当前可执行: ").append(readyTasks.size())
                .append(" | 状态: ").append(status).append('\n');

        if (!batches.isEmpty()) {
            sb.append("   - 首批执行: ").append(formatTaskList(batches.get(0), 5)).append('\n');
            if (batches.size() > 1) {
                sb.append("   - 最终收敛: ")
                        .append(formatTaskList(batches.get(batches.size() - 1), 5))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    // ========== 内部辅助 ==========

    private String compactGoal(String rawGoal, int maxLength) {
        String singleLineGoal = rawGoal
                .replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
                .replaceAll(" {2,}", " ");
        if (singleLineGoal.length() <= maxLength) {
            return singleLineGoal;
        }
        return singleLineGoal.substring(0, maxLength - 3) + "...";
    }

    private String formatTaskList(List<Task> batch, int limit) {
        if (batch.isEmpty()) {
            return "无";
        }
        List<String> taskIds = batch.stream()
                .map(Task::getId)
                .toList();
        if (taskIds.size() <= limit) {
            return String.join(", ", taskIds);
        }
        return String.join(", ", taskIds.subList(0, limit))
                + " 等 " + taskIds.size() + " 个任务";
    }

    private String getStatusIcon(Task.TaskStatus status) {
        return switch (status) {
            case PENDING -> "⏳";
            case RUNNING -> "▶️";
            case COMPLETED -> "✅";
            case FAILED -> "❌";
            case SKIPPED -> "⏭️";
        };
    }

    @Override
    public String toString() {
        return String.format("ExecutionPlan[%s: %s] (%d tasks, %s)",
                id, goal, tasks.size(), status);
    }
}
