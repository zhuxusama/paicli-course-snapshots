package ouccs.smy.paiclilearn.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMemoryLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsPaiMarkdownAsPromptSection() throws Exception {
        Files.writeString(tempDir.resolve("PAI.md"), "# 项目规则\n\n- 默认中文输出");

        String section = new ProjectMemoryLoader(tempDir).load();

        assertTrue(section.startsWith("## Project Memory"));
        assertTrue(section.contains("默认中文输出"));
    }

    @Test
    void returnsEmptyWhenPaiFileIsMissingOrBlank() throws Exception {
        assertEquals("", new ProjectMemoryLoader(tempDir).load());

        Files.writeString(tempDir.resolve("PAI.md"), "   ");
        assertEquals("", new ProjectMemoryLoader(tempDir).load());
    }
}
