package ouccs.smy.paiclilearn.plan;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 可执行计划——由 Task 节点组成的 DAG。
 * <p>
 * [s10 新增] {@link #addTask(Task)} 添加节点，
 * {@link #computeExecutionOrder()} 做拓扑排序，
 * {@link #getExecutionBatches()} 输出可并行执行的批次。</p>
 *
 * @since s10
 */
public class ExecutionPlan {
    public enum PlanStatus { CREATED, RUNNING, COMPLETED, FAILED, CANCELLED }

    private final String id;
    private final String goal;
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private List<String> executionOrder = new ArrayList<>();
    private PlanStatus status = PlanStatus.CREATED;
    private String summary;
    private long startTime;
    private long endTime;

    public ExecutionPlan(String id, String goal) {
        this.id = id;
        this.goal = goal;
    }

    // ========== 构造 ==========

    public void addTask(Task task) {
        tasks.put(task.id(), task);
        // 反向链接依赖关系
        for (String depId : task.dependencies()) {
            Task dep = tasks.get(depId);
            if (dep != null) dep.addDependent(task.id());
        }
    }

    /** 拓扑排序（DFS）——含环检测。 */
    public boolean computeExecutionOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> onPath = new HashSet<>();

        boolean[] hasCycle = new boolean[]{false};

        for (String taskId : tasks.keySet()) {
            if (!visited.contains(taskId)) {
                dfs(taskId, visited, onPath, order, hasCycle);
            }
        }

        if (hasCycle[0]) return false;
        this.executionOrder = order;
        return true;
    }

    private void dfs(String taskId, Set<String> visited, Set<String> onPath,
                     List<String> order, boolean[] hasCycle) {
        if (hasCycle[0]) return;
        visited.add(taskId);
        onPath.add(taskId);

        Task task = tasks.get(taskId);
        if (task != null) {
            for (String depId : task.dependencies()) {
                if (!visited.contains(depId)) {
                    dfs(depId, visited, onPath, order, hasCycle);
                } else if (onPath.contains(depId)) {
                    hasCycle[0] = true;
                    return;
                }
            }
        }

        onPath.remove(taskId);
        order.add(taskId);
    }

    /** 获取拓扑序（懒计算）。 */
    public List<String> getExecutionOrder() {
        if (executionOrder.isEmpty()) computeExecutionOrder();
        return List.copyOf(executionOrder);
    }

    /** 按可并行批次输出。 */
    public List<List<String>> getExecutionBatches() {
        Map<String, Task> taskMap = new HashMap<>(tasks);
        List<List<String>> batches = new ArrayList<>();
        Set<String> completed = new HashSet<>();

        while (completed.size() < taskMap.size()) {
            List<String> batch = new ArrayList<>();
            for (Task task : tasks.values()) {
                if (completed.contains(task.id())) continue;
                if (task.status() == Task.TaskStatus.SKIPPED) {
                    completed.add(task.id());
                    continue;
                }
                boolean allDepsCompleted = task.dependencies().stream()
                        .allMatch(completed::contains);
                if (allDepsCompleted) {
                    batch.add(task.id());
                }
            }
            if (batch.isEmpty()) break;
            batches.add(batch);
            completed.addAll(batch);
        }
        return batches;
    }

    /** 根节点（无前置依赖）。 */
    public List<Task> getRootTasks() {
        return tasks.values().stream()
                .filter(t -> t.dependencies().isEmpty())
                .toList();
    }

    /** 当前可执行的任务。 */
    public List<Task> getExecutableTasks() {
        return tasks.values().stream()
                .filter(t -> t.status() == Task.TaskStatus.PENDING && t.isExecutable(tasks))
                .toList();
    }

    /** 进度比例（0.0 ~ 1.0）。 */
    public double getProgress() {
        if (tasks.isEmpty()) return 1.0;
        long completed = tasks.values().stream()
                .filter(t -> t.status() == Task.TaskStatus.COMPLETED)
                .count();
        return (double) completed / tasks.size();
    }

    /** 简化为多行文本摘要。 */
    public String summarize() {
        StringBuilder sb = new StringBuilder();
        sb.append("计划 ").append(id).append(": ").append(goal).append("\n");
        sb.append("状态: ").append(status).append(" (").append(tasks.size()).append(" 任务)\n");
        for (Task t : tasks.values()) {
            sb.append("  [").append(t.status()).append("] ")
                    .append(t.id()).append(": ").append(t.description());
            if (!t.dependencies().isEmpty()) {
                sb.append(" (依赖: ").append(String.join(", ", t.dependencies())).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ========== 访问器 ==========

    public String id() { return id; }
    public String goal() { return goal; }
    public Map<String, Task> tasks() { return Collections.unmodifiableMap(tasks); }
    public PlanStatus status() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public String summary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public void markStarted() { this.startTime = System.currentTimeMillis(); this.status = PlanStatus.RUNNING; }
    public void markCompleted() { this.endTime = System.currentTimeMillis(); this.status = PlanStatus.COMPLETED; }
}
