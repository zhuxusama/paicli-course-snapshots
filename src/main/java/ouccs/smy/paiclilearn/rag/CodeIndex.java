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
 * 轻量代码索引器：扫描文件、切块、提取关系，并汇总统计结果。
 */
public class CodeIndex {

    private static final Logger LOG = LoggerFactory.getLogger(CodeIndex.class);

    private final CodeChunker chunker;
    private final CodeAnalyzer analyzer;
    private final ProgressListener progressListener;

    /**
     * 索引进度监听器，由 CLI 或测试注入，用来观察长时间索引任务的阶段变化。
     */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * 接收索引过程中的可展示进度文本。
         *
         * @param message 当前进度消息
         */
        void onProgress(String message);

        /**
         * 返回一个丢弃所有进度消息的监听器。
         *
         * @return no-op listener
         */
        static ProgressListener noop() {
            return message -> {
            };
        }
    }

    public CodeIndex() {
        this(ProgressListener.noop());
    }

    public CodeIndex(ProgressListener progressListener) {
        this.chunker = new CodeChunker();
        this.analyzer = new CodeAnalyzer();
        this.progressListener = progressListener == null ? ProgressListener.noop() : progressListener;
    }

    public IndexResult index(String projectPath) {
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            String message = "路径不存在: " + projectPath;
            emit("✗ " + message);
            return new IndexResult(0, 0, message);
        }

        emit("开始索引: " + root);
        List<Path> files = new ArrayList<>();
        collectFiles(root, files);
        emit("发现 " + files.size() + " 个文件待索引");

        int chunkCount = 0;
        int relationCount = 0;
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            emit("进度: " + (i + 1) + "/" + files.size() + " " + file.getFileName());
            try {
                List<CodeChunk> chunks = chunker.chunkFile(file);
                chunkCount += chunks.size();
                if (file.toString().endsWith(".java")) {
                    relationCount += analyzer.analyzeFile(file).size();
                }
            } catch (Exception e) {
                emit("跳过: " + file + " - " + e.getMessage());
                LOG.warn("code index failed for {}", file, e);
            }
        }

        String message = "索引完成: " + chunkCount + " 个代码块, " + relationCount + " 条关系";
        emit(message);
        return new IndexResult(chunkCount, relationCount, message);
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
                    String name = file.getFileName().toString();
                    if (isIndexable(name)) {
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
     * @param chunkCount 生成的代码块数量
     * @param relationCount 提取到的结构关系数量
     * @param message 面向终端展示的结果摘要
     */
    public record IndexResult(int chunkCount, int relationCount, String message) {
    }
}
