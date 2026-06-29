package ouccs.smy.paiclilearn.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码索引管理器 —— 扫描项目目录、对代码文件分块、提取结构关系。
 *
 * <p>本章（s14）的核心职责：把"项目扫描 → 文件收集 → AST 分块 → 关系提取"
 * 串联成一条可观测的管道。进度通过 {@link ProgressListener} 回传，
 * 结果汇入 {@link IndexResult}。
 *
 * <p><b>与源项目的差异：</b>源项目的 {@code CodeIndex} 在构造函数中持有
 * {@code EmbeddingClient} 和 {@code VectorStore}，本版本暂时不引入这两个依赖
 * （它们将在 s16 的 Embedding / SQLite 向量存储章节加入）。
 * 当前版本仅收集 chunk 与 relation 的计数信息，不做向量化与持久化。
 *
 * <p>集成点：后续章节的 {@code /index} 命令会构造 {@code CodeIndex}
 * 并注册 {@code ProgressListener}，把索引进度推送到终端输出流。
 *
 * @since s14
 */
public class CodeIndex {

    private static final Logger log = LoggerFactory.getLogger(CodeIndex.class);

    private final CodeChunker chunker;
    private final CodeAnalyzer analyzer;
    private final ProgressListener progressListener;

    /**
     * 索引进度监听器 —— 由调用方注入，用于把进度消息推送到终端或日志。
     */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(String message);

        static ProgressListener noop() {
            return message -> {};
        }
    }

    /** 无进度监听器的默认构造 */
    public CodeIndex() {
        this(ProgressListener.noop());
    }

    /** 带进度监听器的构造 */
    public CodeIndex(ProgressListener progressListener) {
        this.chunker = new CodeChunker();
        this.analyzer = new CodeAnalyzer();
        this.progressListener = progressListener == null ? ProgressListener.noop() : progressListener;
    }

    /**
     * 索引指定路径的代码库：收集文件 → 分块 → 分析关系 → 返回统计。
     *
     * @param projectPath 项目根目录路径
     * @return 索引统计结果（chunk 数、关系数、状态消息）
     */
    public IndexResult index(String projectPath) {
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            String message = "路径不存在: " + projectPath;
            emit("❌ " + message);
            return new IndexResult(0, 0, message);
        }

        emit("🔍 开始索引: " + root);

        // 步骤 1：收集待索引文件
        List<Path> filesToIndex = new ArrayList<>();
        collectFiles(root, filesToIndex);
        emit("📁 发现 " + filesToIndex.size() + " 个文件待索引");

        int totalChunks = 0;
        int totalRelations = 0;

        int processed = 0;
        int total = filesToIndex.size();

        // 步骤 2 & 3：逐文件分块 + 分析关系
        for (Path file : filesToIndex) {
            processed++;
            if (processed % 10 == 0 || processed == total) {
                emit(String.format("   进度: %d/%d (%s)",
                        processed, total, file.getFileName()));
            }

            try {
                List<CodeChunk> chunks = chunker.chunkFile(file);
                totalChunks += chunks.size();

                if (file.toString().endsWith(".java")) {
                    List<CodeRelation> relations = analyzer.analyzeFile(file);
                    totalRelations += relations.size();
                }
            } catch (Exception e) {
                String message = "   ⚠️ 索引失败: " + file + " - " + e.getMessage();
                emit(message);
                log.warn("code index failed for file {}", file, e);
            }
        }

        String msg = String.format("索引完成：%d 个代码块，%d 条关系",
                totalChunks, totalRelations);
        emit("✅ " + msg);
        return new IndexResult(totalChunks, totalRelations, msg);
    }

    private void emit(String message) {
        progressListener.onProgress(message);
    }

    /**
     * 递归收集项目目录中需要索引的代码文件。
     * 跳过常见的非代码目录和隐藏目录。
     */
    private void collectFiles(Path root, List<Path> files) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (dirName.equals("node_modules") || dirName.equals("target")
                            || dirName.equals("build") || dirName.equals(".git")
                            || dirName.equals(".idea") || dirName.equals(".vscode")
                            || dirName.equals("dist") || dirName.equals("out")
                            || dirName.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".java") || name.endsWith(".py")
                            || name.endsWith(".js") || name.endsWith(".ts")
                            || name.endsWith(".go") || name.endsWith(".rs")
                            || name.endsWith(".c") || name.endsWith(".cpp")
                            || name.endsWith(".h") || name.endsWith(".md")
                            || name.endsWith(".xml") || name.endsWith(".properties")
                            || name.endsWith(".yaml") || name.endsWith(".yml")
                            || name.endsWith(".json") || name.endsWith(".sh")
                            || name.endsWith(".gradle") || name.endsWith(".kt")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            String message = "遍历文件失败: " + e.getMessage();
            emit("❌ " + message);
            log.warn("code index file traversal failed for root {}", root, e);
        }
    }

    /**
     * 索引结果 —— 包含 chunk 数、关系数和状态描述消息。
     */
    public record IndexResult(int chunkCount, int relationCount, String message) {}
}
