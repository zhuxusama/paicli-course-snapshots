# s16：Embedding、SQLite VectorStore 与语义检索

本快照接在 s15 之后，把“代码切块 + AST 关系”升级成可持久化、可检索的 RAG 后端。

## 本章新增能力

| 能力 | 文件 | 说明 |
|---|---|---|
| Embedding 调用 | `EmbeddingClient.java` | 支持 Ollama 与 OpenAI 兼容 `/embeddings` |
| 向量持久化 | `VectorStore.java` | SQLite 保存代码块、embedding JSON 和代码关系 |
| 查询分词 | `RagQueryTokenizer.java` | Jieba + ASCII token，用于关键词补召回 |
| 静默分词工厂 | `JiebaSegmenterFactory.java` | 避免 Jieba 首次加载日志污染 DemoTest/CLI |
| 混合检索 | `CodeRetriever.java` | 语义召回 + 关键词加权 + 排序限流 |
| 输出格式化 | `SearchResultFormatter.java` | 把结果整理为 CLI/工具可读文本 |
| 索引升级 | `CodeIndex.java` | 扫描后生成 embedding，并写入 SQLite |

## 运行 DemoTest

```powershell
mvn test '-Dtest=CodeRetrieverDemoTest' -DskipTests=false
```

控制台会展示：

```text
【场景】上一章只能切出代码块；本章把代码块写入 SQLite，然后用自然语言问题召回相关代码。
【输入】准备两个代码块和一个查询。
【执行】调用 VectorStore.insertChunks(...) 写索引，再调用 CodeRetriever.hybridSearch(query, 3)。
【输出】检索结果会显示文件、符号、类型、相似度和代码片段。
```

## 验证

```powershell
mvn test '-Dtest=VectorStoreTest,EmbeddingClientTest,CodeRetrieverTest,SearchResultFormatterTest,CodeRetrieverDemoTest,CodeIndexTest' -DskipTests=false
mvn test -DskipTests=false
```

## 配置

真实索引会读取这些配置：

| 配置 | 默认值 | 用途 |
|---|---|---|
| `EMBEDDING_PROVIDER` | `ollama` | embedding provider |
| `EMBEDDING_MODEL` | `nomic-embed-text` | embedding 模型 |
| `EMBEDDING_BASE_URL` | `http://localhost:11434` | provider 地址 |
| `EMBEDDING_API_KEY` | 空 | OpenAI 兼容 provider token |
| `paicli.rag.dir` | `~/.paicli/rag` | SQLite 数据目录 |

测试会把 `paicli.rag.dir` 指向临时目录，不污染本机索引。

## 和原项目还差什么

本章完成 RAG 后端链路。CLI 命令、`search_code` 工具注册、检索结果注入 Agent prompt 放到后续章节继续做。
