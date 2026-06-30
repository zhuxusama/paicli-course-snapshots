package ouccs.smy.paiclilearn.llm;

/**
 * 讯飞 MaaS OpenAI-compatible provider 适配器。
 *
 * <p>不同租户的 MaaS 域名可能不同，因此默认值只作为教学入口，生产使用时应显式配置 XFYUN_MAAS_BASE_URL。</p>
 */
public final class XfyunMaaSClient extends AbstractOpenAiCompatibleClient {
    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public XfyunMaaSClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = value(model, "xdeepseekv3");
        this.apiUrl = DeepSeekClient.chatUrl(value(baseUrl, "https://maas-api.cn-huabei-1.xf-yun.com/v1"));
    }

    @Override protected String getApiUrl() { return apiUrl; }
    @Override protected String getModel() { return model; }
    @Override protected String getApiKey() { return apiKey; }
    @Override public String getModelName() { return model; }
    @Override public String getProviderName() { return "xfyun-maas"; }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
