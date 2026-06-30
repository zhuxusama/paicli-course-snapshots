package ouccs.smy.paiclilearn.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * s16: 代码索引器从“扫描统计器”升级为“可检索索引构建器”。
 * <p>
 * 它沿用 s15 的 CodeChunker/CodeAnalyzer，把文件切成代码块并提取关系；
 * 本章新增的部分是：为每个代码块生成 embedding，并把代码块、向量、关系一起写入 SQLite。
 */
public class CodeIndex {

    private static final Logger LOG = LoggerFactory.getLogger(CodeIndex.class);

    private final CodeChunker chunker;
    private final CodeAnalyzer analyzer;
    private final EmbeddingClient embeddingClient;
    private final ProgressListener progressListener;

    /**
     * 索引进度监听器，CLI 或测试可以注入它来观察索引阶段。
     */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(String message);

        static ProgressListener noop() {
            return message -> {
            };
        }
    }

    public CodeIndex() {
        this(new EmbeddingClient(), ProgressListener.noop());
    }

    public CodeIndex(ProgressListener progressListener) {
        this(new EmbeddingClient(), progressListener);
    }

    public CodeIndex(EmbeddingClient embeddingClient) {
        this(embeddingClient, ProgressListener.noop());
    }

    public CodeIndex(EmbeddingClient embeddingClient, ProgressListener progressListener) {
        this.chunker = new CodeChunker();
        this.analyzer = new CodeAnalyzer();
        this.embeddingClient = embeddingClient;
        this.progressListener = progressListener == null ? ProgressListener.noop() : progressListener;
    }

    public IndexResult index(String projectPath) {
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            String message = "路径不存在: " + projectPath;
            emit("⚠ " + message);
            return new IndexResult(0, 0, message);
        }

        emit("开始索引: " + root);
        List<Path> files = new ArrayList<>();
        collectFiles(root, files);
        emit("发现 " + files.size() + " 个文件待索引");

        List<VectorStore.CodeChunkEntry> chunkEntries = new ArrayList<>();
        List<CodeRelation> relations = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            emit("进度: " + (i + 1) + "/" + files.size() + " " + file.getFileName());
            try {
                List<CodeChunk> chunks = chunker.chunkFile(file);
                for (CodeChunk chunk : chunks) {
                    // s16: 索引阶段把代码块文本交给 EmbeddingClient，后续检索才能按语义相似度召回。
                    chunkEntries.add(new VectorStore.CodeChunkEntry(chunk, embeddingClient.embed(chunk.toEmbeddingText())));
                }
                if (file.toString().endsWith(".java")) {
                    relations.addAll(analyzer.analyzeFile(file));
                }
            } catch (Exception e) {
                emit("跳过: " + file + " - " + e.getMessage());
                LOG.warn("code index failed for {}", file, e);
            }
        }

        try (VectorStore vectorStore = new VectorStore(root.toString())) {
            // s16: VectorStore 是本章的持久化边界；重新索引时替换当前项目的旧块和旧关系。
            vectorStore.clearProject();
            vectorStore.insertChunks(chunkEntries);
            vectorStore.insertRelations(relations);
            VectorStore.IndexStats stats = vectorStore.getStats();
            String message = "索引完成: " + stats.chunkCount() + " 个代码块, "
                    + stats.relationCount() + " 条关系, 已写入 SQLite";
            emit(message);
            return new IndexResult(stats.chunkCount(), stats.relationCount(), message);
        } catch (Exception e) {
            String message = "索引写入失败: " + e.getMessage();
            emit(message);
            LOG.warn("persist code index failed for {}", root, e);
            return new IndexResult(chunkEntries.size(), relations.size(), message);
        }
    }

    private void collectFiles(Path root, List<Path> files) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (name.equals("target") || name.equals("build") || name.equals("node_modules")
                            || name.equals(".git") || name.equals(".idea") || name.equals(".vscode")
                            || name.equals("dist") || name.equals("out")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isIndexable(file.getFileName().toString())) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            emit("文件扫描失败: " + e.getMessage());
            LOG.warn("collect files failed for {}", root, e);
        }
    }

    private boolean isIndexable(String name) {
        return name.endsWith(".java")
                || name.endsWith(".md")
                || name.endsWith(".xml")
                || name.endsWith(".json")
                || name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.endsWith(".properties")
                || name.endsWith(".js")
                || name.endsWith(".ts")
                || name.endsWith(".py")
                || name.endsWith(".go")
                || name.endsWith(".rs");
    }

    private void emit(String message) {
        progressListener.onProgress(message);
    }

    /**
     * 索引结果统计。
     *
     * @param chunkCount 生成并写入的代码块数量
     * @param relationCount 提取并写入的结构关系数量
     * @param message 面向终端展示的摘要
     */
    public record IndexResult(int chunkCount, int relationCount, String message) {
    }
}
