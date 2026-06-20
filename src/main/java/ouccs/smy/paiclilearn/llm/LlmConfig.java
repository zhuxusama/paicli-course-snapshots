package ouccs.smy.paiclilearn.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM 配置加载器。
 * <p>
 * 按优先级从环境变量和 .env 文件中读取 API 密钥、端点和模型名称，
 * 支持 OpenAI 兼容三件套和 GLM 快捷配置。
 *
 * @param apiUrl API 端点 URL
 * @param apiKey API 密钥
 * @param model  模型标识符
 */
public record LlmConfig(String apiUrl, String apiKey, String model) {

    private static final String OPENAI_COMPATIBLE_API_KEY = "OPENAI_COMPATIBLE_API_KEY";
    private static final String OPENAI_COMPATIBLE_API_URL = "OPENAI_COMPATIBLE_API_URL";
    private static final String OPENAI_COMPATIBLE_MODEL   = "OPENAI_COMPATIBLE_MODEL";

    private static final String GLM_API_KEY     = "GLM_API_KEY";
    private static final String GLM_DEFAULT_URL  = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String GLM_DEFAULT_MODEL = "glm-4-flash";

    /**
     * 从当前系统环境变量和工作目录加载配置。
     */
    public static LlmConfig fromEnvironment() {
        return fromSources(System.getenv(), Path.of("").toAbsolutePath());
    }

    /**
     * 从指定的环境变量映射和起始目录加载配置。
     * <p>
     * 加载策略：
     * <ol>
     *   <li>向上查找最近的 .env 文件并解析</li>
     *   <li>用环境变量覆盖 .env 值（环境变量优先）</li>
     *   <li>按优先级检查：OpenAI 兼容三件套 → GLM</li>
     * </ol>
     *
     * @param environment    环境变量映射（通常为 {@link System#getenv()}）
     * @param startDirectory 向上查找 .env 的起始目录
     * @return 已解析的配置
     * @throws IllegalStateException 未找到任何有效配置时抛出
     */
    public static LlmConfig fromSources(Map<String, String> environment, Path startDirectory) {
        Map<String, String> env = loadNearestDotEnv(startDirectory);

        // 环境变量覆盖 .env 值
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (present(entry.getValue())) {
                env.put(entry.getKey(), entry.getValue());
            }
        }

        // 1) OpenAI 兼容三件套（最高优先级）
        if (present(env.get(OPENAI_COMPATIBLE_API_KEY))
                && present(env.get(OPENAI_COMPATIBLE_API_URL))
                && present(env.get(OPENAI_COMPATIBLE_MODEL))) {
            return new LlmConfig(
                    env.get(OPENAI_COMPATIBLE_API_URL),
                    env.get(OPENAI_COMPATIBLE_API_KEY),
                    env.get(OPENAI_COMPATIBLE_MODEL)
            );
        }

        // 2) GLM 快捷配置
        if (present(env.get(GLM_API_KEY))) {
            return new LlmConfig(
                    valueOrDefault(env.get(OPENAI_COMPATIBLE_API_URL), GLM_DEFAULT_URL),
                    env.get(GLM_API_KEY),
                    valueOrDefault(env.get(OPENAI_COMPATIBLE_MODEL), GLM_DEFAULT_MODEL)
            );
        }

        throw new IllegalStateException("""
                未找到 LLM 配置。请选择以下任一方式配置：

                  方式 A — OpenAI 兼容三件套：
                    OPENAI_COMPATIBLE_API_KEY=sk-xxx
                    OPENAI_COMPATIBLE_API_URL=https://api.openai.com/v1/chat/completions
                    OPENAI_COMPATIBLE_MODEL=gpt-4o-mini

                  方式 B — GLM（智谱）：
                    GLM_API_KEY=your-glm-key

                配置可写入 .env 文件或设置为系统环境变量（环境变量优先）。""");
    }

    /**
     * 从 startDirectory 开始向上查找最近的 .env 文件并解析为 Map。
     */
    private static Map<String, String> loadNearestDotEnv(Path startDirectory) {
        Path dir = startDirectory.toAbsolutePath().normalize();
        while (dir != null) {
            Path dotEnv = dir.resolve(".env");
            if (Files.isRegularFile(dotEnv)) {
                try {
                    return parseDotEnv(Files.readString(dotEnv));
                } catch (IOException e) {
                    // 当前目录不可读时继续向父目录查找。
                }
            }
            Path parent = dir.getParent();
            if (parent == null || parent.equals(dir)) {
                break;
            }
            dir = parent;
        }
        return new HashMap<>();
    }

    /**
     * 解析 KEY=VALUE 格式的 .env 文本。
     * <p>
     * 支持以 {@code #} 或 {@code ;} 开头的注释行，
     * VALUE 两端的引号会被自动去除。
     *
     * @param content .env 文件的原始文本
     * 解析后的键值对映射
     */
    private static Map<String, String> parseDotEnv(String content) {
        Map<String, String> map = new HashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = stripOptionalQuotes(line.substring(eq + 1).trim());
            map.put(key, value);
        }
        return map;
    }

    /**
     * 去除值两端的单引号或双引号（如果成对出现）。
     */
    private static String stripOptionalQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if (first == last && (first == '"' || first == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * 当 value 非空且非空白时返回 value，否则返回 defaultValue。
     */
    private static String valueOrDefault(String value, String defaultValue) {
        return present(value) ? value : defaultValue;
    }

    /**
     * 判断字符串是否非空且包含非空白字符。
     */
    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
