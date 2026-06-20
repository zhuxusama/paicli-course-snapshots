package ouccs.smy.paiclilearn.llm;

/** 本地 FreeLlmAPI OpenAI-compatible 网关适配器。 */
public final class FreeLlmApiClient extends AbstractOpenAiCompatibleClient {
    private final String apiKey, model, apiUrl;
    public FreeLlmApiClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey; this.model = value(model, "auto");
        this.apiUrl = DeepSeekClient.chatUrl(value(baseUrl, "http://localhost:5173/v1"));
    }
    @Override protected String getApiUrl() { return apiUrl; }
    @Override protected String getModel() { return model; }
    @Override protected String getApiKey() { return apiKey; }
    @Override public String getModelName() { return model; }
    @Override public String getProviderName() { return "freellmapi"; }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
