package ouccs.smy.paiclilearn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * s14 引入项目级 PAI.md：它是随仓库保存的稳定项目记忆，不等同于 /save 写入的运行期长期记忆。
 */
public final class ProjectMemoryInitializer {
    public static final String FILE_NAME = "PAI.md";

    private ProjectMemoryInitializer() {}

    /**
     * 在项目根目录创建 PAI.md 模板；如果文件已经存在则保持原样，避免覆盖人工维护的项目记忆。
     */
    public static Path ensureExists(Path projectRoot) throws IOException {
        Path root = projectRoot == null ? Path.of("").toAbsolutePath() : projectRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path file = root.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            Files.writeString(file, defaultTemplate());
        }
        return file;
    }

    public static String defaultTemplate() {
        return """
                # PAI 项目记忆

                这里记录对当前项目长期稳定有效的约定。它会在启动时被加载进 system prompt，
                用来补充 README/AGENTS.md 之外的项目上下文。

                ## 项目约定

                - 只记录跨会话仍然成立的事实。
                - 不记录一次性任务、临时调试信息或真实密钥。
                - 如果约定已经迁移到 README/AGENTS.md，可以从这里删掉。
                """;
    }
}
