# s14：Project Memory 与 Provider 扩展

本快照从 s13 继续演进，新增两条能力：

1. 启动时读取项目根目录 `PAI.md`，把项目级规则注入 Agent 的 system prompt。
2. 新增 Agnes 与讯飞 MaaS 两个 OpenAI-compatible Provider，并接入统一配置与工厂。

## Project Memory

`PAI.md` 是项目级记忆，不等同于 `/save` 保存的长期记忆：

- `PAI.md` 随项目快照存在，适合记录仓库约定、架构边界和协作规则。
- `/save` 属于运行时长期记忆，只保存用户明确要求保存的稳定事实。
- s14 只会在缺失时创建默认模板，不会自动把对话内容写入 `PAI.md`。

启动路径：

```java
Path projectRoot = hitlRegistry.delegate().getProjectPath();
ProjectMemoryInitializer.ensureExists(projectRoot);
String projectMemoryContext = new ProjectMemoryLoader(projectRoot).load();

Agent agent = new Agent(
        llmClient,
        toolRegistry,
        PromptAssembler.createDefault(),
        PromptContext.builder().projectMemoryContext(projectMemoryContext).build(),
        memoryManager);
```

## Provider 扩展

新增配置示例：

```env
PAICLI_PROVIDER=agnes
AGNES_API_KEY=...
AGNES_MODEL=agnes-chat
AGNES_BASE_URL=https://api.agnes.com/v1

PAICLI_PROVIDER=xfyun-maas
XFYUN_MAAS_API_KEY=...
XFYUN_MAAS_MODEL=xdeepseekv3
XFYUN_MAAS_BASE_URL=https://maas-api.cn-huabei-1.xf-yun.com/v1
```

`LlmClientFactory` 会把 `xfyun`、`xfyun_maas`、`iflytek` 等别名归一到讯飞 MaaS 客户端。

## 关键文件

| 文件 | 作用 |
|---|---|
| `PAI.md` | 项目记忆模板 |
| `cli/ProjectMemoryInitializer.java` | 缺失时初始化 `PAI.md` |
| `prompt/ProjectMemoryLoader.java` | 读取并包装项目记忆 |
| `prompt/PromptContext.java` | 增加 `projectMemoryContext` |
| `prompt/PromptAssembler.java` | 注入 Project Memory |
| `llm/AgnesClient.java` | Agnes OpenAI-compatible 客户端 |
| `llm/XfyunMaaSClient.java` | 讯飞 MaaS OpenAI-compatible 客户端 |

## 验证

```powershell
mvn test '-Dtest=ProjectMemoryInitializerTest,ProjectMemoryLoaderTest,LlmClientFactoryTest,PromptAssemblerTest' -DskipTests=false
mvn test -DskipTests=false
```

## 和原项目还差什么

| 方面 | 原项目 | 本节实现 |
|---|---|---|
| Project Memory 生命周期 | 可结合更完整的项目规则、Skill 与 Prompt 分层 | 启动时读取项目根 `PAI.md` 并注入 |
| Provider 能力 | 厂商私有鉴权、thinking、流式事件和重试策略 | OpenAI-compatible 非流式教学路径 |
| 配置来源 | 多层配置合并 | 沿用课程已有环境变量模型 |

这些差距是刻意的：本章聚焦项目记忆进入 Prompt、Provider 进入工厂。更完整的 Prompt 分层与 Skill/Runtime 体系会在后续章节补齐。
