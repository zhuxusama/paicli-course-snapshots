package ouccs.smy.paiclilearn.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 验证相关性、项目可见性和 token 截断。 */
class MemoryRetrieverTest {
    @Test void retrievesRelevantVisibleFacts() {
        var shortTerm = new ConversationMemory(10);
        var longTerm = LongTermMemory.inMemory();
        longTerm.store(MemoryEntry.fact("项目使用 Java 17", "project", "A"));
        longTerm.store(MemoryEntry.fact("项目使用 Python", "project", "B"));
        longTerm.store(MemoryEntry.fact("用户偏好中文", "global", ""));
        var retriever = new MemoryRetriever(shortTerm, longTerm);

        String context = retriever.buildLongTermContext("这个 Java 项目", "A", 100);
        assertTrue(context.contains("Java 17"));
        assertFalse(context.contains("Python"));
        assertEquals("", retriever.buildLongTermContext("Java", "A", 1));
    }
}
