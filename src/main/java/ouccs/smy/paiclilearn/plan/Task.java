package ouccs.smy.paiclilearn.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务节点——DAG 的基本执行单元。
 *
 * <p>每个 Task 有唯一 ID、描述、类型和状态。依赖关系通过
 * {@link #addDependency(String)} 与 {@link #addDependent(String)} 维护。
 * {@link #isExecutable(Map)} 检查所有前置是否 COMPLETED 后才可执行。</p>
 *
 * <p>直接移植自原项目 {@code com.paicli.plan.Task}，仅调整课程包名。
 * 保留源项目的 getter/setter、状态机、依赖维护和 toString。</p>
 *
 * @since s10
 */
public class Task {

    /** 任务类型——决定执行器分发路径。 @since s10 */
    public enum TaskType {
        PLANNING,      // 规划任务
        FILE_READ,     // 读取文件
        FILE_WRITE,    // 写入文件
        COMMAND,       // 执行命令
        ANALYSIS,      // 分析结果
        VERIFICATION   // 验证结果
    }

    /** 任务状态机——PENDING→RUNNING→COMPLETED/FAILED/SKIPPED。 @since s10 */
    public enum TaskStatus {
        PENDING,       // 等待执行
        RUNNING,       // 执行中
        COMPLETED,     // 已完成
        FAILED,        // 失败
        SKIPPED        // 跳过
    }

    private final String id;
    private final String description;
    private final TaskType type;
    private volatile TaskStatus status;
    private volatile String result;
    private volatile String error;
    private final List<String> dependencies;  // 依赖的其他任务 ID
    private final List<String> dependents;    // 依赖此任务的其他任务 ID
    private volatile long startTime;
    private volatile long endTime;

    public Task(String id, String description, TaskType type) {
        this.id = id;
        this.description = description;
        this.type = type;
        this.status = TaskStatus.PENDING;
        this.dependencies = new ArrayList<>();
        this.dependents = new ArrayList<>();
    }

    public Task(String id, String description, TaskType type, List<String> dependencies) {
        this(id, description, type);
        if (dependencies != null) {
            this.dependencies.addAll(dependencies);
        }
    }

    // ========== Getters ==========

    public String getId() { return id; }

    public String getDescription() { return description; }

    public TaskType getType() { return type; }

    public TaskStatus getStatus() { return status; }

    public String getResult() { return result; }

    public String getError() { return error; }

    /** 返回依赖 ID 的防御性拷贝。 */
    public List<String> getDependencies() { return new ArrayList<>(dependencies); }

    /** 返回反向依赖 ID 的防御性拷贝。 */
    public List<String> getDependents() { return new ArrayList<>(dependents); }

    public long getStartTime() { return startTime; }

    public long getEndTime() { return endTime; }

    // ========== Setters ==========

    public void setStatus(TaskStatus status) { this.status = status; }

    public void setResult(String result) { this.result = result; }

    public void setError(String error) { this.error = error; }

    // ========== 依赖维护 ==========

    /** 注册一个依赖此任务的前置任务 ID。由 {@link ExecutionPlan#addTask(Task)} 内部调用。 */
    public void addDependent(String taskId) {
        if (!dependents.contains(taskId)) {
            dependents.add(taskId);
        }
    }

    /** 添加此任务依赖的前置任务 ID。 */
    public void addDependency(String taskId) {
        if (!dependencies.contains(taskId)) {
            dependencies.add(taskId);
        }
    }

    // ========== 状态迁移 ==========

    /** 标记开始执行，设置 startTime 并切换状态为 RUNNING。 */
    public void markStarted() {
        this.status = TaskStatus.RUNNING;
        this.startTime = System.currentTimeMillis();
    }

    /** 标记完成，记录结果和 endTime。 */
    public void markCompleted(String result) {
        this.status = TaskStatus.COMPLETED;
        this.result = result;
        this.endTime = System.currentTimeMillis();
    }

    /** 标记失败，记录错误信息和 endTime。 */
    public void markFailed(String error) {
        this.status = TaskStatus.FAILED;
        this.error = error;
        this.endTime = System.currentTimeMillis();
    }

    /** 标记跳过。 */
    public void markSkipped() {
        this.status = TaskStatus.SKIPPED;
        this.endTime = System.currentTimeMillis();
    }

    // ========== 工具方法 ==========

    /**
     * 获取执行耗时（毫秒）。如果尚未开始返回 0，执行中则返回已用时间。
     */
    public long getDuration() {
        if (startTime == 0) return 0;
        if (endTime == 0) return System.currentTimeMillis() - startTime;
        return endTime - startTime;
    }

    /**
     * 是否可执行——处于 PENDING 状态且所有前置任务都已完成。
     */
    public boolean isExecutable(Map<String, Task> allTasks) {
        if (status != TaskStatus.PENDING) return false;
        for (String depId : dependencies) {
            Task dep = allTasks.get(depId);
            if (dep == null || dep.getStatus() != TaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("Task[%s: %s] (%s)", id, description, status);
    }
}
