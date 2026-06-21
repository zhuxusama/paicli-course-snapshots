package ouccs.smy.paiclilearn.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计划中的单个任务节点——s10 的 DAG 基本单元。
 * <p>
 * [s10 新增] 每个 Task 有唯一 ID、描述、类型和状态。
 * dependencies 记录前置任务 ID，通过 {@link #isExecutable(Map)}
 * 判断前置是否全部 COMPLETED 后才可执行。</p>
 *
 * @since s10
 */
public class Task {
    public enum TaskType { PLANNING, FILE_READ, FILE_WRITE, COMMAND, ANALYSIS, VERIFICATION }
    public enum TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

    private final String id;
    private final String description;
    private final TaskType type;
    private volatile TaskStatus status = TaskStatus.PENDING;
    private volatile String result;
    private volatile String error;
    private final List<String> dependencies = new ArrayList<>();
    private final List<String> dependents = new ArrayList<>();
    private volatile long startTime;
    private volatile long endTime;

    public Task(String id, String description, TaskType type) {
        this.id = id;
        this.description = description;
        this.type = type;
    }

    public Task(String id, String description, TaskType type, List<String> dependencies) {
        this(id, description, type);
        if (dependencies != null) this.dependencies.addAll(dependencies);
    }

    public String id() { return id; }
    public String description() { return description; }
    public TaskType type() { return type; }
    public TaskStatus status() { return status; }
    public String result() { return result; }
    public String error() { return error; }
    public List<String> dependencies() { return List.copyOf(dependencies); }
    public List<String> dependents() { return List.copyOf(dependents); }
    public long startTime() { return startTime; }
    public long endTime() { return endTime; }

    void addDependent(String taskId) { dependents.add(taskId); }

    public void markStarted() { this.status = TaskStatus.RUNNING; this.startTime = System.currentTimeMillis(); }
    public void markCompleted(String result) { this.status = TaskStatus.COMPLETED; this.result = result; this.endTime = System.currentTimeMillis(); }
    public void markFailed(String error) { this.status = TaskStatus.FAILED; this.error = error; this.endTime = System.currentTimeMillis(); }
    public void markSkipped() { this.status = TaskStatus.SKIPPED; this.endTime = System.currentTimeMillis(); }

    /** 检查前置任务是否全部完成。 */
    public boolean isExecutable(Map<String, Task> taskMap) {
        for (String depId : dependencies) {
            Task dep = taskMap.get(depId);
            if (dep == null || dep.status() != TaskStatus.COMPLETED) return false;
        }
        return true;
    }

    public long getDuration() {
        if (endTime == 0) return System.currentTimeMillis() - startTime;
        return endTime - startTime;
    }
}
