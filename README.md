# s09: 长上下文压缩与 Context 状态 — 窗口有限，预算要算清楚

[上一章：s08 记忆系统](../s08_memory/) -> `s09` -> [下一章：s10 Planner DAG](../s10_task_planner/)

> "128k 上下文看起来很大，但多轮对话很快就不够用了。" — Token 预算和对话压缩是长任务的基础设施。

**系统层**：上下文管理 — 预算跟踪、压缩触发、Token 用量展示。

---

## 原项目里它在哪里

本章对应原项目 `src/main/java/com/paicli/memory/` 的压缩主链和 `src/main/java/com/paicli/context/` 的配置层：
这四个文件构成一条完整的"感知→判断→压缩→展示"链路：

- `TokenBudget`（memory 包）：对话预算的唯一真相源。它从模型的上下文窗口扣除 system prompt、工具定义和预留回复空间后，给出真正可分配给对话的 token 容量。每轮 LLM 调用后累积 input/output/cached token 计数，同时提供字符级别的消息列表 token 估算——用 CJK 字符 /1.5 + 其他字符 /4.0 的混合比实现对中英文混合消息的近似估值。
- `ConversationHistoryCompactor`（memory 包）：在对话历史接近触发阈值时，把早期 user→assistant→tool→assistant 轮次替换为一条 LLM 生成的摘要消息。它从后往前扫描消息列表，保留最近 N 轮（默认 3 轮）的原始消息，把前面的消息拼接成文本喂给 LLM 做总结。压缩失败时不清空历史，安全回退。
- `ContextProfile`（context 包）：从真实 LLM 客户端读取 maxContextWindow，按固定比例导出 agentTokenBudget（窗口 × 0.8）、shortTermMemoryBudget（窗口 × 0.45）、compressionTriggerRatio（0.90）和 memoryContextTokens（窗口 / 200，夹在 500~5000 之间）。
- `TokenUsageFormatter`（context 包）：把 TokenBudget 和 ContextProfile 的数据格式化为终端友好的单行字符串 `📊 Token: in=4500 out=800 cache=✓ | 已用 5300 / 128000 | 费用: ¥0.0034 | 2.1s`，按 provider 区分费率（DeepSeek、GLM 等）。

```text
Agent.run() 每轮迭代
  -> AgentBudget.check(history) → 迭代/token/停滞三重检查
  -> ContextProfile.compressionTriggerTokens() → 是否接近窗口 90%？
  -> TokenBudget.needsCompression(memory, 0.90) → 是
  -> ConversationHistoryCompactor.compactIfNeeded(history, triggerTokens)
    -> findCompressBoundary() → 从后往前找到第 N 个 user 消息作为切割点
    -> summarize(待压缩消息) → 真实 LLM 调用生成摘要
    -> history.clear() + 重建：system + [已压缩] + 确认 + 最近的尾部
  -> LLM chat 调用
  -> TokenBudget.recordUsage(input, output, cached)
  -> TokenUsageFormatter.format() → 终端状态行
```

这条链路的核心设计意图：**压缩是防御性的，不是主动优化**。不到 90% 窗口占用不触发；触发了也只压缩早期轮次，保留最近的完整上下文；压缩失败时静默降级，不打断用户当前任务。

---

## 为什么现在实现它

s08 建立了长期记忆和检索——从现在开始对话内容可以被持久化和按需召回。但对话在不断增长：每次 LLM 调用都追加 user + assistant（+ tool + tool_result）消息到 conversationHistory 里。以 128k 窗口为例，每条消息平均 200 token 的话，约 600+ 轮对话就会碰壁。对于更小的 32k/16k 模型，可能几十轮就不够了。

本章要解决的核心问题不是"怎么压缩"，而是"在什么时机、用什么策略、保什么丢什么"。s08 已经有两类记忆：短期（ConversationMemory，进程内）和长期（LongTermMemory，JSON 持久化）。本章引入第三类操作——conversationHistory 压缩——它和短期记忆压缩是两回事：
- **短期记忆压缩**：对 ConversationMemory 中的条目做摘要，缩减短期存储占用。
- **对话历史压缩**：对发给 LLM 的 `List<Message>` 做截断+摘要，保持 LLM 请求不超窗口。

本节的实现既要接在 Agent 主循环里（每轮前后检查），又不能把压缩逻辑塞进 Agent 类——那样 Agent 会变成一个上帝类。所以我们用 TokenBudget（预算计算）、ConversationHistoryCompactor（压缩执行）、ContextProfile（参数来源）三个独立组件协作，Agent 只负责调用时机。

---

## 本章你会实现什么

- `TokenBudget`：**预算计算器**。构造时从窗口扣除预留，提供 `getAvailableForConversation()`、`isWithinBudget()`、`needsCompression()`、`recordUsage()` 和静态 `estimateMessagesTokens()`。CJK 字符按 /1.5、其他字符按 /4.0 估算——因为中文一个汉字约等于 1.5 个英文 token。
- `ConversationHistoryCompactor`：**压缩执行器**。持有 LlmClient 引用和 retainRecentRounds 配置（默认 3）。`compactIfNeeded(history, triggerTokens)` 做四件事：判断是否超阈值→找切割点→调 LLM 生成摘要→重建 history 列表。压缩失败返回 false，原 history 不变。
- `ContextProfile`：**参数画像**。Java record，从 LlmClient 按固定比例导出 8 个预算参数。`from(LlmClient)` 是工厂方法；`custom(int, int)` 给测试用。
- `TokenUsageFormatter`：**终端格式化**。静态工具类，按 provider 区分费率（DeepSeek 输入 ¥2/M、输出 ¥0.5/M、缓存 ¥8/M；GLM 输入 ¥5/M、输出 ¥1/M、缓存 ¥15/M），输出 `📊 Token: in=... out=... | 已用 ... / ... | 费用: ¥... | ...s`。
- Agent 主循环接入预算检查和压缩——每轮前检查 TokenBudget，每轮后 recordUsage。

---

## 本章源码覆盖契约

| Feature ID | 本章处理 | 推迟到 | 原因 |
|---|---|---|---|
| CTX-001 | implemented | - | TokenBudget：窗口扣除、CJK/英文混合估算、压缩触发、用量累积 |
| CTX-002 | implemented | - | ConversationHistoryCompactor：切割点查找、LLM 摘要、history 重建、失败回退 |
| CTX-003 | implemented | - | ContextProfile：8 参数画像、from() 工厂、custom() 测试构造 |
| CTX-004 | implemented | - | TokenUsageFormatter：单行格式化、provider 费率区分、耗时展示 |
| MEM-004 | implemented | - | 两类压缩区分：conversationHistory 压缩 ≠ shortTermMemory 压缩 |
| MEM-005 | deferred | s15 | 中文查询专用 tokenizer 和语义级压缩 |

`TokenBudget` 和 `ConversationHistoryCompactor` 在本章完整可运行。`ContextProfile` 和 `TokenUsageFormatter` 提供参数和展示层，Agent 主循环在本章接入。

---

## 真实依赖与配置

| 类型 | 名称 | 用途 |
|---|---|---|
| LLM | 真实 provider（s02 已配置） | ConversationHistoryCompactor 的 summarize() 调用真实 LLM |
| JDK | 无新增外部 Maven 依赖 | String.codePoints() 做 CJK 检测，纯 JDK |
| 已有模块 | MemoryEntry（s08） | needsCompression 遍历 memory.getAll().tokenCount() |
| 已有模块 | LlmClient（s02） | 获取 maxContextWindow() 和 chat() |

---

## 从零实现

### 1. TokenBudget：为什么预留要分成三块

普通的 token 计数很简单——把每条消息的字符数除以 4 加起来就行。但实际的 LLM 调用里，system prompt 占用固定空间，工具定义也要占空间，模型回复还需要一段缓冲。如果把这些都算进"可用对话预算"，压缩触发时机就会偏晚——等压缩执行时，system prompt + 工具定义 + 回复缓冲已经把窗口挤爆了。

所以 `TokenBudget` 的构造把窗口分成两块：

```java
// 可用对话预算 = 模型窗口 - system prompt（500） - 工具定义（800） - 回复预留（2000）
public int getAvailableForConversation() {
    return contextWindow - reservedForSystem - reservedForTools - reservedForResponse;
}
```

这三个预留值不是从模型 API 动态获取的，而是基于经验的固定估算：system prompt 通常 300~800 token，工具定义按每工具 100 token × 8~10 个工具，回复预留保证模型至少能输出 2000 token 的回答。

CJK 字符的 token 估算需要特别处理：

```java
private static int estimateTextTokens(String text) {
    if (text == null || text.isEmpty()) return 0;
    long cjk = text.codePoints().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
    long other = text.length() - cjk;
    return Math.max(1, (int) Math.ceil(cjk / 1.5 + other / 4.0));
}
```

这里 `text.length()` 对 CJK 字符返回的是 char 数（Java 内部 UTF-16，一个 CJK 字符 = 1 个 char），codePoints 过滤出真正的 CJK 码点。一个中文字符约等于 1.5 个 token，一个英文字符约等于 0.25 个 token（4 个英文字符 = 1 个 token）。这比简单用 `length() / 4` 对中文消息更准确。

`estimateMessagesTokens()` 遍历消息列表时还考虑了 tool calls 和 reasoning content——每项 tool call 的函数名和参数都计入估算，每条消息额外加 4 token 的消息分隔开销。

### 2. ConversationHistoryCompactor：切割点怎么找

压缩最难的不是调 LLM 做摘要，而是**决定从哪一刀切**。切得太靠后——保留太多原始消息，压缩没效果。切得太靠前——把最近的上下文也丢了，模型丢失关键信息。

`findCompressBoundary()` 的策略是从后往前扫描，数 user 消息：

```java
private int findCompressBoundary(List<Message> history) {
    int userCount = 0;
    for (int i = history.size() - 1; i >= 0; i--) {
        if ("user".equals(history.get(i).role())) userCount++;
        if (userCount >= retainRecentRounds) return i;
    }
    // 如果保留轮次不够，至少保留最后一条 user 消息
    for (int i = history.size() - 1; i >= 0; i--) {
        if ("user".equals(history.get(i).role())) return i;
    }
    return history.size() - 1;
}
```

为什么按 user 消息而不是按轮次数？因为一轮对话可能有多条 assistant/tool 消息——user→assistant（tool call）→tool（结果）→assistant（总结）——如果按消息总数切，切出来的尾巴可能只包含半轮对话，语义不完整。按 user 消息切保证每一轮都是完整的。

压缩后 history 的重建也十分讲究：

```java
// 压缩后 = system + [已压缩]摘要 + "好的，已理解前面的对话摘要。" + 保留的尾部
history.clear();
history.add(systemMsg);
history.add(LlmClient.Message.user("[已压缩] " + summary));
history.add(LlmClient.Message.assistant("好的，已理解前面的对话摘要。"));
history.addAll(tail);
```

在压缩消息后面跟一条 assistant 确认消息是一个重要的技巧：它告诉模型"前面的摘要已经被理解了，继续处理当前问题"。如果没有这条确认消息，部分模型会把 `[已压缩]` 消息当成普通用户输入，可能会追问细节或重新展开摘要。

`summarize()` 方法用拼接文本 + system prompt 的方式调 LLM：

```java
protected String summarize(List<LlmClient.Message> messages) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (var msg : messages) {
        sb.append("[").append(msg.role()).append("] ");
        // 每条消息截断 2000 字符，总长度截断 60000 字符
    }
    var response = llmClient.chat(
        List.of(
            LlmClient.Message.system("请用一句话概括以下对话的关键信息和决策。"),
            LlmClient.Message.user(text)
        ), List.of()
    );
    return response.content() != null ? response.content() : "";
}
```

这里有两层截断保护：单条消息超过 2000 字符截断，总拼接文本超过 60000 字符截断。压缩本身也要消耗 token——不能为了省 token 反而花了更多 token 在压缩上。

压缩失败时（LLM 调用异常），`compactIfNeeded()` 返回 false，history 保持原样。这是关键的防御策略：**宁可带着完整历史撞墙，也不要在压缩过程中丢数据**。

### 3. ContextProfile：参数从哪来

`ContextProfile` 是 Java record，不可变。它的工厂方法 `from(LlmClient)` 从真实模型客户端导出所有预算参数：

```java
public static ContextProfile from(LlmClient llmClient) {
    int window = llmClient.maxContextWindow();
    int agentBudget = Math.max(4000, (int) (window * 0.8));   // 至少 4000
    int shortTermBudget = Math.max(4000, (int) (window * 0.45)); // 至少 4000
    int memoryCtx = Math.max(500, Math.min(5000, window / 200)); // 夹在 500~5000
    return new ContextProfile(window, agentBudget, 0.90,
            shortTermBudget, memoryCtx,
            false, llmClient.supportsPromptCaching(), llmClient.promptCacheMode());
}
```

每个参数都有下界保护，防止极小窗口（如测试用的 8000 token 窗口）导出 0 或负数的预算。compressionTriggerRatio = 0.90 意味着对话占用达到可用预算的 90% 时触发压缩——留 10% 的缓冲空间给 LLM 的回复和可能的 tool call 扩展。

`custom(int, int)` 是测试专用的快捷构造：

```java
public static ContextProfile custom(int contextWindow, int shortTermMemoryBudget) {
    return new ContextProfile(contextWindow, Math.max(4000, (int) (contextWindow * 0.8)),
            0.90, shortTermMemoryBudget, Math.max(500, contextWindow / 200),
            false, false, "none");
}
```

测试里不需要真实的 LlmClient——直接用 `ContextProfile.custom(128000, 8000)` 就能创建一个 128k 窗口、8k 短期预算的画像。

### 4. TokenUsageFormatter：为什么按 provider 区分费率

Token 统计不仅要告诉用户用了多少，还要告诉花了多少钱。不同 provider 的费率差异很大：

```java
switch (provider) {
    case "deepseek" -> { inRate = 2.0; outRate = 0.5; cacheRate = 8.0; }
    case "glm" -> { inRate = 5.0; outRate = 1.0; cacheRate = 15.0; }
    default -> { return ""; }  // 未知 provider 不估算
}
```

费率单位是 元/百万 token。DeepSeek 缓存命中（cacheRate）比普通输入贵 4 倍——这看似反直觉，实际上 DeepSeek 的缓存命中 token 按更高的价格计费是因为缓存机制本身消耗了额外的推理资源。GLM 同理。未知 provider 直接返回空字符串，避免给出错误估算。

`format()` 的总耗时判断也值得注意：

```java
long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
if (elapsed > 1000) {
    sb.append(" | ").append(String.format("%.1fs", elapsed / 1000.0));
} else {
    sb.append(" | ").append(elapsed).append("ms");
}
```

超过 1 秒显示秒，不到 1 秒显示毫秒——避免 `0.1s` 看起来像瞬时完成误导用户。

### 5. Agent 主循环接入

Agent.run() 在每轮迭代前后接入预算系统：

```java
// 每轮开始：三重检查
var status = budget.check(conversationHistory);
if (status != WITHIN_BUDGET) return "⚠ 预算耗尽: " + status;

// Token 压缩检查——注意这里是 conversationHistory（发给 LLM 的消息列表），
// 不是 ConversationMemory（s08 的短期存储）
int triggerTokens = profile.compressionTriggerTokens();
if (budget.needsCompression(conversationMemory, 0.90)) {
    compactor.compactIfNeeded(conversationHistory, triggerTokens);
}

// LLM 调用
var response = llmClient.chat(conversationHistory, tools);

// 每轮结束：记录消耗
budget.recordUsage(response.inputTokens(), response.outputTokens(),
    response.cachedInputTokens());

// 格式化展示
String stats = TokenUsageFormatter.format(llmClient, budget, profile, startNanos);
System.out.println(stats);
```

这里有一个容易搞混的点：`budget.needsCompression()` 的参数是 `ConversationMemory`（来自 s08），它用 MemoryEntry.tokenCount() 做快速判断；但 `compactor.compactIfNeeded()` 的参数是 `List<Message>`（发给 LLM 的消息列表）。两者估算的值可能不同——ConversationMemory 的 tokenCount 是保存时计算好的，而 estimateMessagesTokens 是实时遍历计算的。在实际运行中，ConversationMemory 的总 token 数通常略高于 estimateMessagesTokens（因为 MemoryEntry 存储了额外的元数据字段），所以用 ConversationMemory 做触发判断更保守——更早触发压缩。

---

## 相对上一节的变化

| 组件 | s08 | s09 |
|---|---|---|
| Token 感知 | MemoryEntry.tokenCount()：单条消息的静态估算 | TokenBudget：窗口扣除 + CJK/EN 混合估算 + 累积跟踪 |
| 对话管理 | ConversationMemory：按条目数限制（默认 200 条） | ConversationHistoryCompactor：按 token 数触发 + 真实 LLM 摘要 |
| 上下文配置 | 无统一参数来源 | ContextProfile：8 参数画像，从 LlmClient 自动导出 |
| 终端展示 | 无 Token 统计 | TokenUsageFormatter：in/out/cache/费用/耗时 单行 |
| Agent 循环 | 无预算感知 | 每轮前 budget.check()，每轮后 budget.recordUsage() |
| 两类压缩 | 无区分 | conversationHistory 压缩（本条）≠ shortTermMemory 压缩（s08） |

s08 解决了"记住什么"；s09 解决"在窗口有限的情况下，哪些东西值得带进下一轮 LLM 请求"。

---

## 和原项目还差什么

| 方面 | 原项目 | 本节实现 | 后续状态 |
|---|---|---|---|
| 预算模型 | LLM API 返回的真实 token 数 + prompt caching 命中率 | 字符 / 3.5（EN）或 /1.5（CJK）估算，并在 `recordUsage()` 中保留真实 input/output/cache 入口 | s09 已打通基础链路；后续 provider / 状态栏章节继续把真实 token 与缓存命中展示得更完整 |
| 压缩触发 | ContextMode 动态切换（COMPACT/BALANCED/FULL_CONTEXT） | 固定 90% 单一阈值 | 已补入 SQLite 计划：`CTX-005`，后续在上下文策略与渲染集成阶段补齐 |
| 摘要质量 | 专用 system prompt + 多轮压缩层次 + 关键信息保留策略 | history 压缩使用简化 prompt；短期记忆压缩已使用 MAP-REDUCE | 已补入 SQLite 计划：`CTX-006`，后续补专用压缩 prompt 与分层摘要策略 |
| 状态行 | JLine Status 托管的底部 dock | System.out.println 每轮一行 | 已在后续渲染章节计划中跟踪：s27 `RENDER-*` / s28 `CLI-*` |
| 预算可视化 | ctx 百分比实时更新 + 剩余 token 预估 | 每轮结束后静态打印 | 已在后续渲染章节计划中跟踪：s27/s28 |
| 停滞检测 | 连续 N 轮内容相似度分析 | s09 未做 | 已在 s13 `AgentBudget` 补齐为迭代 / token / 停滞三重预算检查 |

这些差距不是遗漏，而是分层教学的边界：s09 只交付"token 预算 + 压缩触发 + 可运行压缩链路"；高级上下文策略、状态栏实时展示和停滞预算分别交给后续章节，并在 `course-state.sqlite` 中可追踪。

---

## 试一下

### 运行测试

```powershell
$env:JAVA_HOME='C:\Users\86182\.jdks\temurin-21'
cd chapters/s09_context_budget/project

# ===== 1. TokenBudget 单元测试 =====
mvn test -Dtest=TokenBudgetTest -DskipTests=false
# 验证：
#   - 128k 窗口扣除预留后可用 = 128000 - 500 - 800 - 2000 = 124700
#   - CJK 字符 "中文测试" 估算约 ceil(4/1.5 + 0/4.0) = 3 token
#   - 英文 "hello world" 估算约 ceil(0/1.5 + 11/4.0) = 3 token
#   - needsCompression() 在 memory 总 token >= 可用 × 0.9 时返回 true

# ===== 2. ConversationHistoryCompactor 测试 =====
mvn test -Dtest=ConversationHistoryCompactorTest -DskipTests=false
# 验证：
#   - 未超阈值时 compactIfNeeded 返回 false，history 不变
#   - 保留最近 2 轮 user 消息时，第 3 轮及以前被压缩
#   - 5000 字符消息估算约 1250 token，4 条约 5000 token

# ===== 3. ContextBudget 演示测试（推荐先看这个） =====
mvn test -Dtest=ContextBudgetDemoTest -DskipTests=false
# 输出示例：
# ===== s09 TokenBudget + ContextProfile 演示 =====
# 模型窗口: 128000
# Agent 预算: 102400
# 短期预算: 57600
# 触发压缩阈值: 115200
#
# Token 统计: 📊 Token: in=4500 out=870 cache=✓ | 已用 5370 / 128000 | 费用: ¥0.0098 | 123ms

# ===== 4. 全量测试 =====
mvn test -DskipTests=false
```

### 运行真实 CLI

```powershell
# 1. 确保 API Key 已配置（s02 步骤）
$env:GLM_API_KEY='your-key'

# 2. 打包并启动
mvn package -DskipTests
java -jar target/paicli-learn-0.1.0-SNAPSHOT.jar
```

进入后按以下顺序观察 Token 系统的完整行为链：

```text
# ===== A. 观察 Token 统计基线 =====
你好
# 每轮 LLM 回复后，底部应打印一行 Token 统计：
# 📊 Token: in=234 out=156 | 已用 390 / 128000 | 费用: ¥0.0013 | 1.2s
# 
# 理解每个字段：
#   in=234    → 本轮发送给 LLM 的 token 数（system prompt + 历史 + 当前问题）
#   out=156   → LLM 返回的 token 数
#   cache=✓   → 如果出现，说明部分输入命中了 prompt cache（节省了费用）
#   已用 390 / 128000 → 累计 in+out 和窗口总量
#   费用: ¥0.0013 → 按 provider 费率估算的费用
#   1.2s → 本轮 LLM 调用耗时

# ===== B. 连续对话，观察 Token 累计 =====
请用 Java 写一个 Hello World
# 📊 Token: in=456 out=234 | 已用 1080 / 128000 | 费用: ¥0.0036 | 2.3s
# 注意：已用从 390 变成了 1080——每轮累加

请解释 System.out.println 的原理
# 📊 Token: in=789 out=345 | 已用 2214 / 128000 | 费用: ¥0.0061 | 3.1s
# in 越来越大——因为对话历史在增长

# ===== C. /clear 清空对话 =====
/clear
# 短期记忆和对话历史被清空

# 再发一条消息验证
你好
# 📊 Token: in=210 out=145 | 已用 355 / 128000 | 费用: ¥0.0012 | 1.1s
# in 回到了接近第一次的水平（略低，因为 system prompt 可能被缓存）

# ===== D. 验证 /clear 不影响长期记忆 =====
/save 项目必须用 Java 17 构建
/memory list
# 应看到刚才保存的事实

/clear
/memory list
# 长期记忆仍然保留——/clear 只清短期状态

# ===== E. 观察压缩行为（需要较长对话） =====
# 连续发送 20-30 条较长的消息，观察：
# 1. 当已用 Token 接近 115200（128k × 0.9）时，触发压缩
# 2. 压缩后对话历史中会出现 "[已压缩]" 前缀的消息
# 3. 压缩后的 in 值会下降——因为早期消息被摘要替代
# 
# 快速触发压缩的方法：
# 连续发送包含大段代码的问题，如：
# "请分析以下代码：[粘贴 200+ 行 Java 代码]"
# 重复 5-6 次，观察已用 Token 的增长和压缩触发
```

观察重点总结：

| 观察项 | 应该看到什么 | 说明什么 |
|---|---|---|
| Token 累加 | 每轮 in/out 不同，已用持续增长 | TokenBudget 在跨轮累积 |
| 已用重置 | /clear 后从接近 0 开始 | conversationHistory 被清空 |
| 长期记忆保留 | /clear 后 /memory list 仍有记录 | 两类存储隔离生效 |
| 压缩触发 | 历史中出现 "[已压缩]" | ConversationHistoryCompactor 工作 |
| 费用变化 | 不同 provider 费率不同 | TokenUsageFormatter 区分费率 |
| 缓存标记 | 出现 cache=✓ | prompt caching 命中 |

---

## 本章快照

```text
course_full/chapters/s09_context_budget/
  README.md
  project/
  diff-from-previous.patch
```

- 上一章快照: `chapters/s08_memory/project/`
- 当前快照: `chapters/s09_context_budget/project/`
- 本章 diff: `chapters/s09_context_budget/diff-from-previous.patch`
- 主要变更文件:
  - `context/ContextProfile.java` [新增] — 8 参数画像、from() 工厂、custom() 测试构造
  - `context/TokenUsageFormatter.java` [新增] — 单行格式化、provider 费率区分、耗时展示
  - `memory/TokenBudget.java` [新增] — 窗口扣除、CJK/EN 混合估算、压缩触发、用量累积
  - `memory/ConversationHistoryCompactor.java` [新增] — 切割点查找、LLM 摘要、history 重建、失败回退
  - `agent/Agent.java` [修改] — 主循环接入 budget.check + compactor + recordUsage

---

## 接下来

s09 解决了"对话太长怎么办"：TokenBudget 知道还剩多少空间，ConversationHistoryCompactor 在接近上限时压缩早期轮次，ContextProfile 和 TokenUsageFormatter 提供参数和展示。

但现在面临一个新问题：用户给了复杂目标（比如"搭建整个 Spring Boot 项目并写测试"），Agent 还是靠单轮推理回答问题——没有结构化的任务分解和调度能力。s10 将引入 Planner 与 DAG 数据模型：把模糊目标变成有依赖关系的 task 图，为后续的计划执行、审阅和并行打好数据结构基础。
