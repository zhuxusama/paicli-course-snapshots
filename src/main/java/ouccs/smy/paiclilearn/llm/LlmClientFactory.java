package ouccs.smy.paiclilearn.llm;

/** 根据规范化 Provider 名称创建真实客户端。 */
public final class LlmClientFactory {
    private LlmClientFactory() {}
    public static LlmClient create(LlmConfig config) {
        return switch (normalizeProvider(config.provider())) {
            case "openai-compatible" -> new OpenAiCompatibleClient(config);
            case "glm" -> new GLMClient(config.apiKey(), config.model(), config.baseUrl());
            case "deepseek" -> new DeepSeekClient(config.apiKey(), config.model(), config.baseUrl());
            case "step" -> new StepClient(config.apiKey(), config.model(), config.baseUrl());
            case "kimi" -> new KimiClient(config.apiKey(), config.model(), config.baseUrl());
            case "freellmapi" -> new FreeLlmApiClient(config.apiKey(), config.model(), config.baseUrl());
            default -> throw new IllegalArgumentException("未知 Provider: " + config.provider());
        };
    }
    public static String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim().toLowerCase();
        return switch (value) {
            case "openai", "compatible", "openai_compatible" -> "openai-compatible";
            case "stepfun", "step-fun" -> "step";
            case "moonshot", "moonshotai", "moonshot-ai" -> "kimi";
            case "free-llm-api", "free_llm_api", "freellm", "free-llm" -> "freellmapi";
            default -> value;
        };
    }
}
