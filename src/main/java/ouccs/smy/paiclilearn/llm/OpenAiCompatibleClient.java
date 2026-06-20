package ouccs.smy.paiclilearn.llm;

import okhttp3.OkHttpClient;

/**
 * 用户显式提供 URL、模型和 Key 时使用的通用 OpenAI-compatible 客户端。
 */
public final class OpenAiCompatibleClient extends AbstractOpenAiCompatibleClient {
    private final LlmConfig config;

    public OpenAiCompatibleClient(LlmConfig config) { this(config, defaultHttpClient()); }
    OpenAiCompatibleClient(LlmConfig config, OkHttpClient client) { super(client); this.config = config; }

    @Override protected String getApiUrl() { return requireValue(config.baseUrl(), "OPENAI_COMPATIBLE_API_URL"); }
    @Override protected String getModel() { return requireValue(config.model(), "OPENAI_COMPATIBLE_MODEL"); }
    @Override protected String getApiKey() { return config.apiKey(); }
    @Override public String getModelName() { return getModel(); }
    @Override public String getProviderName() { return "openai-compatible"; }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少 " + name);
        return value;
    }
}
