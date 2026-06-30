# s15：RAG 代码切块、AST 关系与索引

本快照接在 s14 Project Memory / Provider 之后，开始构建代码检索的底座：把 Java 源码切成适合检索的 chunk，提取简单 AST 关系，并用内存索引串起来。

## 本章新增能力

| 能力 | 文件 | 说明 |
|---|---|---|
| 代码块模型 | `CodeChunk.java` | 表示 file/class/method 三类 chunk，并提供 embedding 文本 |
| 代码关系模型 | `CodeRelation.java` | 表示 imports、extends、implements、contains、calls 等关系 |
| AST 分析 | `CodeAnalyzer.java` | 使用 JavaParser 提取类、方法调用和结构关系 |
| 代码切块 | `CodeChunker.java` | 将 Java 文件拆成 class/method chunk，非 Java 文件退化为 file chunk |
| 内存索引 | `CodeIndex.java` | 扫描目录、生成 chunk/relation，并通过 ProgressListener 汇报进度 |

## 为什么先做这些

s13 之前的代码理解主要依赖 `glob_files`、`grep_code`、`read_file`：

- 精确知道关键词时，`grep_code` 很好用。
- 关键词模糊、问题跨多个文件、或需要“语义相近”时，纯 grep 不够。
- 真正的 RAG 需要先有稳定的代码切块和结构关系，再谈 embedding 与向量库。

所以 s15 只做 RAG 的前半段：代码切块 + AST 关系 + 索引进度。Embedding、VectorStore、CodeRetriever 会在后续章节继续补齐。

## 关键机制

### 1. CodeChunk

```java
CodeChunk.fileChunk("README.md", content);
CodeChunk.classChunk("Agent.java", "Agent", header, 15, 120);
CodeChunk.methodChunk("Agent.java", "Agent.run", methodBody, 42, 80);
```

`toEmbeddingText()` 会把路径、符号名、chunk 类型和正文组合起来，为后续 embedding 做准备。

### 2. CodeAnalyzer

`CodeAnalyzer` 使用 JavaParser 解析 Java 17 语法，并提取：

- import 关系
- extends / implements 关系
- class contains method 关系
- method calls 关系

### 3. CodeChunker

`CodeChunker` 优先为 Java 文件生成 class/method chunk；如果文件不是 Java 或解析失败，则退化为 file chunk，保证索引流程不中断。

### 4. CodeIndex

`CodeIndex` 扫描目录后汇总：

- 已处理文件数
- 生成的 chunk 数
- 提取的 relation 数
- 通过 `ProgressListener` 输出索引进度

## 和原项目还差什么

| 方面 | 原项目 | 本节实现 |
|---|---|---|
| Embedding | 真实 EmbeddingClient 调用 | 未实现，后续章节补 |
| 向量库 | SQLite / VectorStore 持久化 | 未实现，后续章节补 |
| 检索器 | CodeRetriever 混合检索 | 未实现，后续章节补 |
| CLI 命令 | `/index` 与检索命令 | 未实现，后续章节补 |

s15 的边界是刻意收窄的：先让“代码如何变成可索引材料”讲清楚，后面再接向量检索。

## 验证

```powershell
mvn test '-Dtest=CodeChunkerTest,CodeAnalyzerTest,CodeIndexTest' -DskipTests=false
mvn test -DskipTests=false
```
