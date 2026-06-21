package ouccs.smy.paiclilearn.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 按项目覆盖、用户覆盖、classpath 内置资源的优先级加载 prompt。
 *
 * @since s07
 */
public class PromptRepository {
    private static final String RESOURCE_PREFIX = "prompts/";

    private final Path userPromptsDir;
    private final Path projectPromptsDir;
    private final ClassLoader classLoader;

    public PromptRepository(Path userPromptsDir, Path projectPromptsDir) {
        this(userPromptsDir, projectPromptsDir, PromptRepository.class.getClassLoader());
    }

    PromptRepository(Path userPromptsDir, Path projectPromptsDir, ClassLoader classLoader) {
        this.userPromptsDir = normalizeRoot(userPromptsDir);
        this.projectPromptsDir = normalizeRoot(projectPromptsDir);
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /** 使用 ~/.paicli/prompts 和项目 .paicli/prompts 创建仓库。 */
    public static PromptRepository createDefault() {
        Path user = Path.of(System.getProperty("user.home"), ".paicli", "prompts");
        Path project = Path.of(".paicli", "prompts");
        return new PromptRepository(user, project);
    }

    /** 加载必需资源；项目覆盖优先级最高。 */
    public String loadRequired(String relativePath) {
        String normalized = normalizeResourcePath(relativePath);
        String content = loadBuiltin(normalized);
        content = overrideIfPresent(userPromptsDir, normalized, content);
        content = overrideIfPresent(projectPromptsDir, normalized, content);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Prompt 资源缺失或为空: " + normalized);
        }
        return content.trim();
    }

    private String loadBuiltin(String relativePath) {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_PREFIX + relativePath)) {
            if (input == null) {
                return null;
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取内置 prompt 失败: " + relativePath, e);
        }
    }

    private static String overrideIfPresent(Path root, String relativePath, String fallback) {
        if (root == null) {
            return fallback;
        }
        Path override = root.resolve(relativePath).normalize();
        if (!override.startsWith(root)) {
            throw new IllegalArgumentException("Prompt 覆盖路径超出根目录: " + relativePath);
        }
        if (!Files.isRegularFile(override)) {
            return fallback;
        }
        try {
            return Files.readString(override, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 prompt 覆盖失败: " + override, e);
        }
    }

    private static Path normalizeRoot(Path root) {
        return root == null ? null : root.toAbsolutePath().normalize();
    }

    private static String normalizeResourcePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Prompt 相对路径不可为空");
        }
        Path path = Path.of(relativePath.replace('\\', '/'));
        if (path.isAbsolute() || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("非法 Prompt 路径: " + relativePath);
        }
        return path.normalize().toString().replace('\\', '/');
    }
}
