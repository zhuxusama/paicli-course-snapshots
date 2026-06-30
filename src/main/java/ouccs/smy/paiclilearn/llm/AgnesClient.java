package ouccs.smy.paiclilearn.llm;

/**
 * Agnes OpenAI-compatible provider 适配器。
 *
 * <p>s14 只接入真实 HTTP/SSE 主路径，不做假响应；具体 baseUrl 可由 AGNES_BASE_URL 覆盖。</p>
 */
public final class AgnesClient extends AbstractOpenAiCompatibleClient {
    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public AgnesClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = value(model, "agnes-chat");
        this.apiUrl = DeepSeekClient.chatUrl(value(baseUrl, "https://api.agnes.com/v1"));
    }

    @Override protected String getApiUrl() { return apiUrl; }
    @Override protected String getModel() { return model; }
    @Override protected String getApiKey() { return apiKey; }
    @Override public String getModelName() { return model; }
    @Override public String getProviderName() { return "agnes"; }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
