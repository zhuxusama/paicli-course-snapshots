package ouccs.smy.paiclilearn.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMemoryInitializerTest {
    @TempDir
    Path tempDir;

    @Test
    void createsTemplateWithoutOverwritingExistingMemory() throws Exception {
        Path file = ProjectMemoryInitializer.ensureExists(tempDir);

        assertEquals("PAI.md", file.getFileName().toString());
        assertTrue(Files.readString(file).contains("PAI 项目记忆"));

        Files.writeString(file, "# 自定义记忆\n\n- 保留人工内容");
        ProjectMemoryInitializer.ensureExists(tempDir);

        assertEquals("# 自定义记忆\n\n- 保留人工内容", Files.readString(file));
    }
}
