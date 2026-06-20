package ouccs.smy.paiclilearn.llm;

/** DeepSeek thinking 历史保真适配器。 */
public final class DeepSeekClient extends AbstractOpenAiCompatibleClient {
    private final String apiKey, model, apiUrl;
    public DeepSeekClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey; this.model = value(model, "deepseek-v4-flash");
        this.apiUrl = chatUrl(value(baseUrl, "https://api.deepseek.com"));
    }
    @Override protected String getApiUrl() { return apiUrl; }
    @Override protected String getModel() { return model; }
    @Override protected String getApiKey() { return apiKey; }
    @Override protected boolean shouldSendReasoningContentInRequestHistory() { return true; }
    @Override public String getModelName() { return model; }
    @Override public String getProviderName() { return "deepseek"; }
    @Override public int maxContextWindow() { return 1_000_000; }
    @Override public boolean supportsPromptCaching() { return true; }
    @Override public String promptCacheMode() { return "automatic-prefix-cache"; }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    static String chatUrl(String base) { String value = base.replaceAll("/+$", ""); return value.endsWith("/chat/completions") ? value : value + "/chat/completions"; }
}
