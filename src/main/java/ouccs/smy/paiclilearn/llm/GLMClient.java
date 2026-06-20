package ouccs.smy.paiclilearn.llm;

/** GLM coding/vision 端点适配器。 */
public final class GLMClient extends AbstractOpenAiCompatibleClient {
    private static final String CODING_URL = "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions";
    private static final String VISION_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private final String apiKey, model, apiUrl;
    public GLMClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = value(model, "glm-5.1");
        this.apiUrl = value(baseUrl, this.model.toLowerCase().startsWith("glm-5v") ? VISION_URL : CODING_URL);
    }
    @Override protected String getApiUrl() { return apiUrl; }
    @Override protected String getModel() { return model; }
    @Override protected String getApiKey() { return apiKey; }
    @Override public String getModelName() { return model; }
    @Override public String getProviderName() { return "glm"; }
    @Override public int maxContextWindow() { return 200_000; }
    @Override public boolean supportsPromptCaching() { return true; }
    @Override public String promptCacheMode() { return "glm-prompt-cache"; }
    private static String value(String candidate, String fallback) { return candidate == null || candidate.isBlank() ? fallback : candidate; }
}
