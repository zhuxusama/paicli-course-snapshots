package ouccs.smy.paiclilearn.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 当前选中 Provider 的真实凭据、模型和端点配置。
 */
public record LlmConfig(String provider, String apiKey, String model, String baseUrl) {
    public static LlmConfig fromEnvironment() {
        return fromSources(System.getenv(), Path.of("").toAbsolutePath());
    }

    public static LlmConfig fromSources(Map<String, String> environment, Path startDirectory) {
        Map<String, String> values = loadNearestDotEnv(startDirectory);
        environment.forEach((key, value) -> { if (present(value)) values.put(key, value); });
        String selected = value(values, "PAICLI_PROVIDER");
        if (!present(selected)) selected = detectProvider(values);
        if (!present(selected)) throw new IllegalStateException(missingConfigMessage());
        return forProvider(selected, values);
    }

    public static LlmConfig forProvider(String provider, Map<String, String> values) {
        String normalized = LlmClientFactory.normalizeProvider(provider);
        if ("openai-compatible".equals(normalized)) {
            return require(normalized, value(values, "OPENAI_COMPATIBLE_API_KEY"),
                    value(values, "OPENAI_COMPATIBLE_MODEL"), value(values, "OPENAI_COMPATIBLE_API_URL"));
        }
        String prefix = switch (normalized) {
            case "glm" -> "GLM";
            case "deepseek" -> "DEEPSEEK";
            case "step" -> "STEP";
            case "kimi" -> "KIMI";
            case "freellmapi" -> "FREELLMAPI";
            default -> throw new IllegalArgumentException("未知 Provider: " + provider);
        };
        String key = value(values, prefix + "_API_KEY");
        if ("kimi".equals(normalized) && !present(key)) key = value(values, "MOONSHOT_API_KEY");
        String model = value(values, prefix + "_MODEL");
        String baseUrl = value(values, prefix + "_BASE_URL");
        return require(normalized, key, model, baseUrl);
    }

    private static LlmConfig require(String provider, String key, String model, String baseUrl) {
        if (!present(key)) throw new IllegalStateException("缺少 " + provider + " API Key");
        return new LlmConfig(provider, key, model, baseUrl);
    }

    private static String detectProvider(Map<String, String> values) {
        if (present(value(values, "OPENAI_COMPATIBLE_API_KEY"))) return "openai-compatible";
        for (String provider : new String[]{"glm", "deepseek", "step", "kimi", "freellmapi"}) {
            String key = value(values, provider.toUpperCase() + "_API_KEY");
            if ("kimi".equals(provider) && !present(key)) key = value(values, "MOONSHOT_API_KEY");
            if (present(key)) return provider;
        }
        return "";
    }

    private static Map<String, String> loadNearestDotEnv(Path startDirectory) {
        Path directory = startDirectory.toAbsolutePath().normalize();
        while (directory != null) {
            Path dotEnv = directory.resolve(".env");
            if (Files.isRegularFile(dotEnv)) {
                try { return parseDotEnv(Files.readString(dotEnv)); }
                catch (IOException ignored) { /* 继续向父目录查找可读配置。 */ }
            }
            directory = directory.getParent();
        }
        return new HashMap<>();
    }

    private static Map<String, String> parseDotEnv(String content) {
        Map<String, String> result = new HashMap<>();
        for (String raw : content.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int split = line.indexOf('=');
            if (split <= 0) continue;
            String item = line.substring(split + 1).trim();
            if (item.length() >= 2 && item.charAt(0) == item.charAt(item.length() - 1)
                    && (item.charAt(0) == '\'' || item.charAt(0) == '"')) item = item.substring(1, item.length() - 1);
            result.put(line.substring(0, split).trim(), item);
        }
        return result;
    }

    private static String value(Map<String, String> values, String key) { return values.get(key); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String missingConfigMessage() {
        return "未找到 LLM 配置。请设置 PAICLI_PROVIDER，并配置对应 *_API_KEY；也可直接提供任一已支持 Provider 的 API Key。";
    }
}
