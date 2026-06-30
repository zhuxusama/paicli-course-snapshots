package ouccs.smy.paiclilearn.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LlmClientFactoryTest {
    @Test
    void createsEverySupportedProviderAndNormalizesAliases() {
        assertInstanceOf(GLMClient.class, create("glm"));
        assertInstanceOf(DeepSeekClient.class, create("deepseek"));
        assertInstanceOf(StepClient.class, create("stepfun"));
        assertInstanceOf(KimiClient.class, create("moonshot"));
        assertInstanceOf(FreeLlmApiClient.class, create("free-llm-api"));
        assertInstanceOf(AgnesClient.class, create("agnes"));
        assertInstanceOf(XfyunMaaSClient.class, create("xfyun"));
        assertEquals("kimi", LlmClientFactory.normalizeProvider("Moonshot-AI"));
        assertEquals("xfyun-maas", LlmClientFactory.normalizeProvider("spark-maas"));
    }

    @Test
    void exposesProviderCapabilitiesAndNormalizedUrls() {
        StepClient step = assertInstanceOf(StepClient.class,
                LlmClientFactory.create(new LlmConfig("step", "key", "step-3.5-flash-2603", "https://api.stepfun.com/v1/")));
        assertEquals("https://api.stepfun.com/v1/chat/completions", step.getApiUrl());
        assertEquals(256_000, step.maxContextWindow());
        assertEquals("step-prefix-cache", step.promptCacheMode());
    }

    @Test
    void createsAgnesAndXfyunMaaSClientsFromConfig() {
        LlmClient agnes = LlmClientFactory.create(new LlmConfig(
                "agnes", "key", "agnes-pro", "https://example.agnes/v1"));
        LlmClient xfyun = LlmClientFactory.create(new LlmConfig(
                "xfyun-maas", "key", "xdeepseekv3", "https://example.xfyun/v1"));

        assertEquals("agnes", agnes.getProviderName());
        assertEquals("agnes-pro", agnes.getModelName());
        assertEquals("xfyun-maas", xfyun.getProviderName());
        assertEquals("xdeepseekv3", xfyun.getModelName());
    }

    private static LlmClient create(String provider) {
        return LlmClientFactory.create(new LlmConfig(provider, "key", null, null));
    }
}
