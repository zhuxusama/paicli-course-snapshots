package ouccs.smy.paiclilearn.prompt;

import ouccs.smy.paiclilearn.cli.ProjectMemoryInitializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从项目根目录读取 PAI.md，并格式化为可以直接注入 system prompt 的项目记忆段落。
 */
public final class ProjectMemoryLoader {
    private final Path projectRoot;

    public ProjectMemoryLoader(Path projectRoot) {
        this.projectRoot = projectRoot == null ? Path.of("").toAbsolutePath() : projectRoot.toAbsolutePath().normalize();
    }

    public String load() {
        Path file = projectRoot.resolve(ProjectMemoryInitializer.FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            String content = Files.readString(file).trim();
            if (content.isBlank()) {
                return "";
            }
            return "## Project Memory (PAI.md)\n\n" + content;
        } catch (IOException e) {
            return "";
        }
    }
}
