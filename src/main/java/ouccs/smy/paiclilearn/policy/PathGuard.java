package ouccs.smy.paiclilearn.policy;

import java.nio.file.Path;

/**
 * 路径安全守卫：确保所有文件操作在项目根目录内。
 *
 * @since s05 [s05 新增]
 */
public class PathGuard {
    private final Path rootPath;

    public PathGuard(String root) {
        if (root == null || root.isBlank()) throw new IllegalArgumentException("项目根路径不可为空");
        this.rootPath = Path.of(root).toAbsolutePath().normalize();
    }

    public Path getRootPath() { return rootPath; }

    /**
     * 解析用户输入的路径并验证其在项目根内。
     * @param input 用户输入的路径字符串（相对或绝对）
     * @return 安全的绝对路径
     * @throws PolicyException 路径逃逸或包含非法模式时抛出
     */
    public Path resolveSafe(String input) {
        if (input == null || input.isBlank()) {
            return rootPath;
        }
        Path resolved = rootPath.resolve(input).normalize();
        // 检查 .. 逃逸：解析后的路径必须在 rootPath 之下
        if (!resolved.startsWith(rootPath)) {
            throw new PolicyException("路径逃逸: '" + input + "' 超出项目根目录 " + rootPath);
        }
        // 检查符号链接逃逸
        try {
            if (java.nio.file.Files.exists(resolved)) {
                Path real = resolved.toRealPath();
                if (!real.startsWith(rootPath.toRealPath())) {
                    throw new PolicyException("路径通过符号链接逃逸: " + input);
                }
            }
        } catch (java.io.IOException ignored) {
            // 文件不存在时无法检查真实路径，依赖前缀检查
        }
        return resolved;
    }
}
