package ouccs.smy.paiclilearn.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 验证短期记忆顺序、容量和清空。 */
class ConversationMemoryTest {
    @Test void evictsOldestAndClears() {
        var memory = new ConversationMemory(2);
        var first = MemoryEntry.message("一", MemoryEntry.MemoryType.USER, "demo");
        memory.store(first);
        memory.store(MemoryEntry.message("二", MemoryEntry.MemoryType.ASSISTANT, "demo"));
        memory.store(MemoryEntry.message("三", MemoryEntry.MemoryType.USER, "demo"));
        assertEquals(2, memory.size());
        assertTrue(memory.retrieve(first.id()).isEmpty());
        memory.clear();
        assertEquals(0, memory.size());
    }
}
