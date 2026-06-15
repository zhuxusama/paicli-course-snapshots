package ouccs.smy.paiclilearn.llm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class LlmConfigTest {

    @Test
    void loadsDeepSeekConfigFromNearestParentDotEnv(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                DEEPSEEK_API_KEY=sk-test-deepseek-key
                DEEPSEEK_MODEL=deepseek-reasoner
                """);

        Path subDir = tempDir.resolve("sub").resolve("project");
        Files.createDirectories(subDir);

        LlmConfig config = LlmConfig.fromSources(Map.of(), subDir);

        Assertions.assertEquals("https://api.deepseek.com/chat/completions", config.apiUrl());
        Assertions.assertEquals("sk-test-deepseek-key", config.apiKey());
        Assertions.assertEquals("deepseek-reasoner", config.model());
    }

    @Test
    void environmentOverridesDotEnv(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                DEEPSEEK_API_KEY=sk-env-file-key
                DEEPSEEK_MODEL=deepseek-chat
                """);

        Map<String, String> envOverrides = Map.of(
                "DEEPSEEK_API_KEY", "sk-override-key",
                "DEEPSEEK_MODEL", "deepseek-coder"
        );

        LlmConfig config = LlmConfig.fromSources(envOverrides, tempDir);

        Assertions.assertEquals("sk-override-key", config.apiKey());
        Assertions.assertEquals("deepseek-coder", config.model());
    }
}
