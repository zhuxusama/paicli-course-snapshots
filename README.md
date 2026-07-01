# s11：Plan 审阅、执行与重规划

本快照接在 s10 的 `Task` / `ExecutionPlan` / `Planner` 之后，把“静态计划”变成“可审阅、可执行、可补充重规划”的闭环。

## 本章新增能力

| 能力 | 文件 | 说明 |
|---|---|---|
| Plan 执行器 | `agent/PlanExecuteAgent.java` | 生成计划、审阅、按拓扑序执行任务、失败时尝试重规划 |
| 审阅输入解析 | `cli/PlanReviewInputParser.java` | Enter/run 执行，普通文本补充，cancel/ESC 取消 |
| Plan prompt | `prompts/modes/plan.md` | 单个计划任务执行时使用的 system prompt |
| 教程测试 | `agent/PlanDemoTest.java` | 用控制台输出学习审阅、执行、补充和取消 |

## Run DemoTest

先跑教程测试：

```powershell
mvn test -Dtest=PlanDemoTest -DskipTests=false
```

你会看到四类场景：

```text
【场景】计划生成后为什么需要审阅/补充/取消
【输入】用户目标、审阅输入、LLM 计划 JSON、任务执行响应
【执行】PlanReviewInputParser.parse(...) 与 PlanExecuteAgent.run(...)
【输出】审阅决策、拓扑执行、补充重规划、取消结果
```

重点观察：

1. `PlanReviewInputParser` 如何把 Enter/run/普通文本/cancel/ESC 转成决策；
2. `PlanExecuteAgent.run(goal)` 如何串起 `Planner -> review -> executePlan`；
3. 用户补充要求如何触发重新规划；
4. 取消如何阻止任务进入执行阶段。

## 验证

```powershell
mvn test -Dtest=PlanDemoTest,PlanExecuteAgentTest,PlanReviewInputParserTest -DskipTests=false
mvn test -DskipTests=false
```

## 和 s10 的关系

s10 只证明 DAG 数据结构能表达依赖和批次；s11 开始让 DAG 真正驱动任务执行。并行执行暂不在本章做，会在 s12 通过 Multi-Agent 继续推进。
