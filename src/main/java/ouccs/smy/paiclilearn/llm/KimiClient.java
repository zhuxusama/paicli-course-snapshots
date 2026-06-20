package ouccs.smy.paiclilearn.llm;

/** Kimi/Moonshot thinking 历史与上下文缓存适配器。 */
public final class KimiClient extends AbstractOpenAiCompatibleClient {
    private final String apiKey, model, apiUrl;
    public KimiClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey; this.model = value(model, "kimi-k2.6");
        this.apiUrl = DeepSeekClient.chatUrl(value(baseUrl, "https://api.moonshot.ai/v1"));
    }
    @Override protected String getApiUrl() { return apiUrl; }
    @Override protected String getModel() { return model; }
    @Override protected String getApiKey() { return apiKey; }
    @Override protected boolean shouldSendReasoningContentInRequestHistory() { return true; }
    @Override public String getModelName() { return model; }
    @Override public String getProviderName() { return "kimi"; }
    @Override public int maxContextWindow() { return 256_000; }
    @Override public boolean supportsPromptCaching() { return true; }
    @Override public String promptCacheMode() { return "moonshot-context-cache"; }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
