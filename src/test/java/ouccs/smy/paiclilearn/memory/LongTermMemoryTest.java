package ouccs.smy.paiclilearn.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 验证长期记忆持久化、scope 和损坏文件处理。 */
class LongTermMemoryTest {
    @TempDir Path tempDir;

    @Test void persistsAndReloadsWithProjectVisibility() {
        Path file = tempDir.resolve("long-term.json");
        var memory = new LongTermMemory(file);
        memory.store(MemoryEntry.fact("项目 A 使用 Java", "project", "A"));
        memory.store(MemoryEntry.fact("用户偏好中文", "global", "A"));

        var reloaded = new LongTermMemory(file);
        assertEquals(2, reloaded.size());
        assertEquals(2, reloaded.visibleIn("A").size());
        assertEquals(1, reloaded.visibleIn("B").size());
        assertEquals(1, reloaded.clearProject("A"));
        assertEquals(1, new LongTermMemory(file).size(), "global 记忆应保留");
    }

    @Test void rejectsCorruptedJson() throws Exception {
        Path file = tempDir.resolve("broken.json");
        Files.writeString(file, "{broken");
        assertThrows(IllegalStateException.class, () -> new LongTermMemory(file));
    }
}
