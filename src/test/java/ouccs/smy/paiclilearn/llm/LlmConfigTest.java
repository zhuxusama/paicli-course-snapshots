package ouccs.smy.paiclilearn.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmConfigTest {
    @Test
    void selectsProviderFromNearestDotEnv(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve(".env"), "PAICLI_PROVIDER=step\nSTEP_API_KEY=file-key\nSTEP_MODEL=step-model\n");
        Path child = Files.createDirectories(root.resolve("project/sub"));
        LlmConfig config = LlmConfig.fromSources(Map.of(), child);
        assertEquals("step", config.provider());
        assertEquals("file-key", config.apiKey());
        assertEquals("step-model", config.model());
    }

    @Test
    void environmentOverridesDotEnv(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve(".env"), "PAICLI_PROVIDER=glm\nGLM_API_KEY=file-key\n");
        LlmConfig config = LlmConfig.fromSources(Map.of(
                "PAICLI_PROVIDER", "deepseek", "DEEPSEEK_API_KEY", "env-key",
                "DEEPSEEK_MODEL", "deepseek-reasoner"), root);
        assertEquals("deepseek", config.provider());
        assertEquals("env-key", config.apiKey());
        assertEquals("deepseek-reasoner", config.model());
    }
}
