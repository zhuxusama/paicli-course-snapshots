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
        assertEquals("kimi", LlmClientFactory.normalizeProvider("Moonshot-AI"));
    }

    @Test
    void exposesProviderCapabilitiesAndNormalizedUrls() {
        StepClient step = assertInstanceOf(StepClient.class,
                LlmClientFactory.create(new LlmConfig("step", "key", "step-3.5-flash-2603", "https://api.stepfun.com/v1/")));
        assertEquals("https://api.stepfun.com/v1/chat/completions", step.getApiUrl());
        assertEquals(256_000, step.maxContextWindow());
        assertEquals("step-prefix-cache", step.promptCacheMode());
    }

    private static LlmClient create(String provider) {
        return LlmClientFactory.create(new LlmConfig(provider, "key", null, null));
    }
}
