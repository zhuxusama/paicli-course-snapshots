# s10: Planner 与 DAG 数据模型 — 先想好，再做

[上一章：s09 上下文压缩](../s09_context_budget/) -> `s10` -> [下一章：s11 Plan 审阅执行](../s11_plan_execute/)

> "想做什么？列出来，一条一条做。" — DAG 计划让 Agent 把模糊目标变成可执行的步骤。

**系统层**：规划 — 显式任务分解与依赖管理。

---

## 原项目里它在哪里

本章对应原项目 `src/main/java/com/paicli/plan/` 的三个核心类：

- **`Task`**：DAG 的原子节点。每个 Task 持有唯一 ID、人类可读描述、TaskType（6 种：PLANNING/FILE_READ/FILE_WRITE/COMMAND/ANALYSIS/VERIFICATION）、TaskStatus（PENDING→RUNNING→COMPLETED/FAILED/SKIPPED）、依赖列表 `dependencies` 和被依赖列表 `dependents`。`isExecutable(Map)` 遍历所有依赖——全部 COMPLETED 才返回 true。`volatile` 修饰 status/result/error/startTime/endTime，保证多线程可见。
- **`ExecutionPlan`**：DAG 容器。维护 `LinkedHashMap<String, Task>`（保持插入顺序）、PlanStatus 状态机（CREATED→RUNNING→COMPLETED/FAILED/CANCELLED）、DFS 拓扑排序（含 `onPath` 环检测）、执行批次划分（`getExecutionBatches()` 循环找所有依赖已满足的节点，每批内节点互不依赖可安全并行）、进度计算（`getProgress()` = completed / total）、根节点查询和无依赖节点查询。
- **`Planner`**：计划生成器。双路径：`createMinimalPlan(goal)` 不调 LLM，固定生成 分析→实现→验证 三节点链；`createPlan(goal)` 调真实 LLM 返回 JSON，`parsePlan(goal, json)` 解析为 ExecutionPlan。失败时回退到 minimalPlan。

```text
用户输入 "搭建 Spring Boot REST API 项目"
  -> Planner.createPlan(goal)
  -> 构造 planner.md prompt + goal
  -> llmClient.chat() → JSON: {summary: "...", tasks: [{id, description, type, dependencies}]}
  -> Planner.parsePlan(goal, jsonResponse)
  -> 遍历 JSON 数组构建 Task 对象
  -> new ExecutionPlan(planId, goal) → addTask() 逐个加入
  -> computeExecutionOrder() → DFS 拓扑排序 + 环检测
  -> getExecutionBatches() → 按批输出可并行任务组
```

---

## 为什么现在实现它

s09 解决了对话无限增长的问题，但 Agent 面对复杂目标仍然靠单轮推理硬撑。用户说"搭建整个项目"，Agent 应该先拆成 分析需求→创建目录结构→写 pom.xml→写 Application.java→写测试→验证编译 这一串有依赖关系的步骤，而不是一次性生成所有代码再手工挑错。

Task 的依赖 DAG 是后续三章的基础：s11 PlanExecuteAgent 按拓扑序遍历 DAG 执行每个 task，s12 AgentOrchestrator 按批次并行调度 Worker，s13 executeTools 并行执行工具调用。本章必须先把 DAG 数据结构和计划生成建立好。

---

## 本章你会实现什么

- **`Task`**：6 种 TaskType（PLANNING/FILE_READ/FILE_WRITE/COMMAND/ANALYSIS/VERIFICATION）、5 种 TaskStatus 状态机（PENDING→RUNNING→COMPLETED/FAILED/SKIPPED）、双向依赖管理（dependencies + dependents）、`isExecutable(Map)` 前置检查、`volatile` 线程安全。
- **`ExecutionPlan`**：`addTask()` 自动建立反向 dep 链接、DFS 拓扑排序含 `onPath` 环检测、`getExecutionBatches()` 贪心分批、`getProgress()` 进度比、`summarize()` 文本摘要。
- **`Planner`**：`createMinimalPlan()` 本地三节点模板 + `createPlan()` LLM JSON 解析、`parsePlan()` 容错（缺失字段填默认值）、失败回退。
- **`PlanDagDemoTest`**：教学演示——4 个 task 组成的 DAG（两个根节点各带一个子节点），展示拓扑序和批次划分的全过程。

---

## 本章源码覆盖契约

| Feature ID | 本章处理 | 推迟到 | 原因 |
|---|---|---|---|
| PLAN-001 | implemented | - | Task：6 TaskType、5 TaskStatus 状态机、双向依赖、volatile 线程安全 |
| PLAN-002 | implemented | - | ExecutionPlan：DFS 拓扑排序 + 环检测、批次划分、进度、摘要 |
| PLAN-003 | implemented | - | Planner：createMinimalPlan 本地模板 + createPlan LLM JSON 解析 |

---

## 真实依赖与配置

| 类型 | 名称 | 用途 |
|---|---|---|
| Maven | Jackson 2.16（已有） | LLM 返回 JSON 的 ObjectMapper 解析 |
| LLM | 真实 provider（s02 已配置） | createPlan() 调 LLM 生成计划 JSON |
| 文件 | `resources/prompts/modes/planner.md` | Planner 模式 system prompt |

---

## 从零实现

### 1. Task：为什么 dependents 是反向链接

`Task` 的构造不要求预先知道谁依赖自己。你在 `new Task("task_2", ..., List.of("task_1"))` 时只知道 task_2 依赖 task_1，不知道 task_1 后面还有谁。所以 `dependents` 在构造时为空，由 `ExecutionPlan.addTask()` 在 task_1 已存在时自动回填：

```java
public void addTask(Task task) {
    tasks.put(task.id(), task);
    for (String depId : task.dependencies()) {
        Task dep = tasks.get(depId);
        if (dep != null) dep.addDependent(task.id());  // 回填反向链接
    }
}
```

`addDependent()` 是包级私有的——外部代码不能随意修改依赖关系，只能通过 ExecutionPlan 统一管理。

状态字段全部 `volatile`：

```java
private volatile TaskStatus status = TaskStatus.PENDING;
private volatile String result;
private volatile String error;
private volatile long startTime;
private volatile long endTime;
```

这是因为 s12 的 AgentOrchestrator 会在多个线程中并发更新 Task 状态——一个线程 markStarted，另一个线程通过 `isExecutable()` 检查状态。`volatile` 保证跨线程可见性，不需要加锁。

### 2. ExecutionPlan：DFS 环检测的 onPath 技巧

拓扑排序的标准做法是 DFS 后序遍历。但怎么检测 DAG 中是否有环？诀窍是维护两套 visited 标记：

```java
private void dfs(String taskId, Set<String> visited, Set<String> onPath,
                 List<String> order, boolean[] hasCycle) {
    if (hasCycle[0]) return;
    visited.add(taskId);
    onPath.add(taskId);  // 进入当前 DFS 路径

    Task task = tasks.get(taskId);
    if (task != null) {
        for (String depId : task.dependencies()) {
            if (!visited.contains(depId)) {
                dfs(depId, visited, onPath, order, hasCycle);
            } else if (onPath.contains(depId)) {
                hasCycle[0] = true;  // 在当前路径上遇到第二次 = 环
                return;
            }
        }
    }

    onPath.remove(taskId);  // 离开当前 DFS 路径
    order.add(taskId);       // 后序加入
}
```

- `visited`：全局已访问——防止重复 DFS。
- `onPath`：当前 DFS 调用栈上的节点集合——如果在同一条路径上第二次遇到同一节点，说明存在环。
- `boolean[]` 而非 `AtomicBoolean`：因为 DFS 是单线程递归，用数组引用传递就够了。

批次划分 `getExecutionBatches()` 的算法是贪心的：

```java
while (completed.size() < taskMap.size()) {
    List<String> batch = new ArrayList<>();
    for (Task task : tasks.values()) {
        if (completed.contains(task.id())) continue;
        if (task.status() == TaskStatus.SKIPPED) {
            completed.add(task.id());  // SKIPPED 直接标记完成
            continue;
        }
        boolean allDepsCompleted = task.dependencies().stream()
                .allMatch(completed::contains);
        if (allDepsCompleted) batch.add(task.id());
    }
    if (batch.isEmpty()) break;  // 死锁：有未完成节点但都不满足依赖
    batches.add(batch);
    completed.addAll(batch);
}
```

每轮迭代找出所有"依赖已全部在 completed 中"的节点，放入同一批。同一批内的节点互不依赖——它们可以安全并行执行。

### 3. Planner：LLM JSON 解析的容错设计

`createPlan()` 让 LLM 返回如下 JSON：

```json
{
  "summary": "搭建 Spring Boot REST API 项目",
  "tasks": [
    {"id": "task_1", "description": "分析项目需求", "type": "ANALYSIS", "dependencies": []},
    {"id": "task_2", "description": "创建 pom.xml", "type": "FILE_WRITE", "dependencies": ["task_1"]},
    {"id": "task_3", "description": "编写 Application.java", "type": "FILE_WRITE", "dependencies": ["task_2"]},
    {"id": "task_4", "description": "编译验证", "type": "VERIFICATION", "dependencies": ["task_3"]}
  ]
}
```

`parsePlan()` 需要容错——LLM 可能返回格式不标准的 JSON：

```java
// 对每个 task JSON 节点：
String id = taskNode.has("id") ? taskNode.get("id").asText() : "task_" + index;
String desc = taskNode.has("description") ? taskNode.get("description").asText() : "";
String typeStr = taskNode.has("type") ? taskNode.get("type").asText() : "ANALYSIS";
List<String> deps = new ArrayList<>();
if (taskNode.has("dependencies")) {
    for (JsonNode dep : taskNode.get("dependencies")) deps.add(dep.asText());
}
```

缺失字段填默认值，不因为 LLM 的一个小错误丢弃整个计划。

---

## 相对上一节的变化

| 组件 | s09 | s10 |
|---|---|---|
| 任务模型 | Agent 线性推理，无结构化任务概念 | Task：类型 + 状态机 + 双向依赖 DAG |
| 执行计划 | 无计划概念 | ExecutionPlan：拓扑排序 + 批次划分 + 进度 |
| 目标拆解 | 完全靠 LLM 自主推理 | Planner 显式生成结构化计划 JSON |
| 新增包 | context/ + memory/增强 | plan/（Task + ExecutionPlan + Planner） |
| 可测试性 | 无独立 DAG 测试 | PlanDagDemoTest 演示完整 DAG 流水线 |

---

## 和原项目还差什么

| 方面 | 原项目 | 本节实现 |
|---|---|---|
| 计划执行 | PlanExecuteAgent 完整执行循环 + 审阅 | s11 |
| 并行调度 | AgentOrchestrator 多 Worker 并行批次 | s12 |
| 失败重规划 | 进度 < 50% 自动 replan | s11 |
| LLM 计划质量 | 专用 planner.md + few-shot 示例 | 基础 prompt |

---

## 试一下

### 运行测试

```powershell
$env:JAVA_HOME='C:\Users\86182\.jdks\temurin-21'
cd chapters/s10_task_planner/project

# DAG 结构测试——拓扑排序、环检测、批次划分
mvn test -Dtest=ExecutionPlanTest -DskipTests=false

# Planner 测试——双路径计划生成
mvn test -Dtest=PlannerTest -DskipTests=false

# ★ DAG 演示测试（推荐先看这个）
mvn test -Dtest=PlanDagDemoTest -DskipTests=false
# 输出示例：
# === Task DAG 演示 ===
# 构建计划: 4 个任务
#   task_1 [ANALYSIS] 分析需求 (无依赖)
#   task_2 [FILE_WRITE] 创建配置 (依赖: task_1)
#   task_3 [FILE_WRITE] 实现功能 (依赖: task_1)
#   task_4 [VERIFICATION] 运行测试 (依赖: task_2, task_3)
#
# 拓扑序: [task_1, task_2, task_3, task_4]
#
# 执行批次:
#   批次 1: [task_1]
#   批次 2: [task_2, task_3]    ← 互不依赖，可并行！
#   批次 3: [task_4]
#
# 进度: 0/4 (0.0%)

# 全量测试
mvn test -DskipTests=false
```

### 验证 DAG 算法正确性

在 PlanDagDemoTest 中可以观察到：

1. **拓扑序正确性**：task_1 在 task_2/task_3 之前，task_2/task_3 在 task_4 之前
2. **批次并行性**：task_2 和 task_3 同批次——它们都只依赖 task_1，互不依赖
3. **环检测**：如果手动构造 task_A→task_B→task_A 的环，`computeExecutionOrder()` 返回 false
4. **进度计算**：COMPLETED 0/4 = 0%，标记 task_1 COMPLETED 后 1/4 = 25%

---

## 本章快照

```text
course_full/chapters/s10_task_planner/
  README.md
  project/
  diff-from-previous.patch
```

- 上一章快照: `chapters/s09_context_budget/project/`
- 当前快照: `chapters/s10_task_planner/project/`
- 本章 diff: `chapters/s10_task_planner/diff-from-previous.patch`
- 主要变更文件:
  - `plan/Task.java` [新增] — 6 TaskType + 5 TaskStatus + 双向依赖 + volatile
  - `plan/ExecutionPlan.java` [新增] — DFS 拓扑 + onPath 环检测 + 批次 + 进度
  - `plan/Planner.java` [新增] — minimalPlan + createPlan LLM + parsePlan 容错
  - `resources/prompts/modes/planner.md` [新增] — Planner 模式 system prompt

---

## 接下来

s10 建立了计划的数据结构——Task 知道谁依赖谁，ExecutionPlan 知道排序和分批，Planner 能从目标生成计划。但计划还只是数据，没有执行者——task 的 status 永远停在 PENDING。

s11 将实现 PlanExecuteAgent：把 DAG 变成可审阅、可执行、失败后自动重规划的完整流水线。用户能看到 Agent 打算做什么，在执行前有机会干预（Enter 执行/ESC 取消/文本补充重规划），每个 task 由真实 Agent + 工具链逐条执行。
