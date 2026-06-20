package ouccs.smy.paiclilearn.llm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class LlmConfigTest {

    @Test
    void loadsGlmConfigFromNearestParentDotEnv(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                GLM_API_KEY=sk-test-glm-key
                OPENAI_COMPATIBLE_MODEL=glm-4-plus
                """);

        Path subDir = tempDir.resolve("sub").resolve("project");
        Files.createDirectories(subDir);

        LlmConfig config = LlmConfig.fromSources(Map.of(), subDir);

        Assertions.assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", config.apiUrl());
        Assertions.assertEquals("sk-test-glm-key", config.apiKey());
        Assertions.assertEquals("glm-4-plus", config.model());
    }

    @Test
    void environmentOverridesDotEnv(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                OPENAI_COMPATIBLE_API_KEY=sk-env-file-key
                OPENAI_COMPATIBLE_API_URL=https://example.invalid/v1/chat/completions
                OPENAI_COMPATIBLE_MODEL=file-model
                """);

        Map<String, String> envOverrides = Map.of(
                "OPENAI_COMPATIBLE_API_KEY", "sk-override-key",
                "OPENAI_COMPATIBLE_API_URL", "https://api.example.test/v1/chat/completions",
                "OPENAI_COMPATIBLE_MODEL", "environment-model"
        );

        LlmConfig config = LlmConfig.fromSources(envOverrides, tempDir);

        Assertions.assertEquals("sk-override-key", config.apiKey());
        Assertions.assertEquals("environment-model", config.model());
        Assertions.assertEquals("https://api.example.test/v1/chat/completions", config.apiUrl());
    }
}
