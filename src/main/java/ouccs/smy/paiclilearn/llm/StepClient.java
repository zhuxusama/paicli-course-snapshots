package ouccs.smy.paiclilearn.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** StepFun reasoning 格式和长上下文适配器。 */
public final class StepClient extends AbstractOpenAiCompatibleClient {
    private final String apiKey, model, apiUrl;
    public StepClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey; this.model = value(model, "step-3.5-flash");
        this.apiUrl = DeepSeekClient.chatUrl(value(baseUrl, "https://api.stepfun.com/v1"));
    }
    @Override protected String getApiUrl() { return apiUrl; }
    @Override protected String getModel() { return model; }
    @Override protected String getApiKey() { return apiKey; }
    @Override public String getModelName() { return model; }
    @Override public String getProviderName() { return "step"; }
    @Override public int maxContextWindow() { return 256_000; }
    @Override public boolean supportsPromptCaching() { return true; }
    @Override public String promptCacheMode() { return "step-prefix-cache"; }
    @Override protected void customizeRequestBody(ObjectNode body) {
        body.put("reasoning_format", "deepseek-style");
        if (model.contains("2603")) body.put("reasoning_effort", "high");
    }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
