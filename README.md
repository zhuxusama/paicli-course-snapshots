# s12: Multi-Agent 团队协作

本章把 s11 的“单 Agent 按计划执行”升级为一个最小可运行的团队编排器：

- `Planner`：把用户目标拆成 JSON 计划。
- `AgentOrchestrator`：解析计划、规范化步骤 ID、按依赖找出 ready 步骤。
- `Worker`：执行具体步骤；同一批 ready 步骤最多 4 个并行。
- `Reviewer`：审核 Worker 输出；不通过时给 Worker 一次修正机会。

核心目标不是做一个完整生产级 Multi-Agent 框架，而是让你先看懂“一个目标如何被拆分、分派、审核、汇总”这条主链路。

---

## 运行教学测试

```bash
mvn test -Dtest=TeamDemoTest -DskipTests=false
```

`TeamDemoTest` 会在控制台打印 3 个学习场景：

1. `demoPlannerWorkerReviewerPipeline`
   - 输入一个用户目标；
   - Planner 返回 3 步 JSON 计划；
   - Orchestrator 把原始 ID 规范化为 `step_1` / `step_2` / `step_3`；
   - Worker 执行，Reviewer 审核；
   - 最后打印每个角色的调用轨迹。

2. `demoParsePlanAndDependencyBatching`
   - 单独观察 `parseSteps()` 如何解析 `tasks` / `steps` JSON；
   - 展示依赖如何从 `"1"` 改写成 `"step_1"`；
   - 展示第一批 ready 步骤和第二批 ready 步骤。

3. `demoReviewerRejectionTriggersOneRetry`
   - 模拟 Reviewer 第一次驳回；
   - Orchestrator 让 Worker 修正一次；
   - Reviewer 第二次通过；
   - 控制台能看到“驳回 → 修正 → 通过”的完整闭环。

如果你只想确认本章 Multi-Agent 相关测试：

```bash
mvn test -Dtest=TeamDemoTest,AgentOrchestratorTest,SubAgentTest,AgentRoleTest,AgentMessageTest -DskipTests=false
```

---

## 本章新增文件

主要代码在：

- `src/main/java/ouccs/smy/paiclilearn/agent/AgentRole.java`
- `src/main/java/ouccs/smy/paiclilearn/agent/AgentMessage.java`
- `src/main/java/ouccs/smy/paiclilearn/agent/SubAgent.java`
- `src/main/java/ouccs/smy/paiclilearn/agent/AgentOrchestrator.java`

教学测试在：

- `src/test/java/ouccs/smy/paiclilearn/agent/TeamDemoTest.java`
- `src/test/java/ouccs/smy/paiclilearn/agent/AgentOrchestratorTest.java`
- `src/test/java/ouccs/smy/paiclilearn/agent/SubAgentTest.java`
- `src/test/java/ouccs/smy/paiclilearn/agent/AgentRoleTest.java`
- `src/test/java/ouccs/smy/paiclilearn/agent/AgentMessageTest.java`

---

## 和原项目还差什么

| 方面 | 原项目 | 本章实现 |
|---|---|---|
| 子 Agent 提示词 | 更完整的角色 prompt、上下文注入和工具约束 | 用角色名 + 角色描述构造最小 system prompt |
| 任务分派 | 可结合文件上下文、工具结果和历史状态做动态路由 | 按 ready 批次轮询分派给两个 Worker |
| 审核策略 | 可根据任务类型、风险等级和工具结果做更细判断 | Reviewer 返回 `approved` JSON，失败后只重试一次 |
| 状态展示 | TUI / 状态栏 / 任务进度动态刷新 | 返回文本汇总，教学测试用控制台展示流程 |
| 失败恢复 | 更完整的中断、恢复和持久化任务状态 | 当前只在内存中执行一次协作流程 |

这些差距是刻意保留的。本章只聚焦 Multi-Agent 的最小闭环：规划、并行分派、审核、重试和汇总。
