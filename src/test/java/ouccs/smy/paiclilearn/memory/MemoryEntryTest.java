package ouccs.smy.paiclilearn.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 验证记忆条目不变量和 token 近似。 */
class MemoryEntryTest {
    @Test void validatesScopeAndEstimatesTokens() {
        var entry = MemoryEntry.fact("用户偏好中文", "project", "demo");
        assertTrue(entry.tokenCount() > 0);
        assertThrows(IllegalArgumentException.class,
                () -> MemoryEntry.fact("事实", "project", ""));
        assertThrows(IllegalArgumentException.class,
                () -> MemoryEntry.fact("事实", "unknown", "demo"));
    }
}
