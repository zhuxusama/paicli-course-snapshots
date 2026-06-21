package ouccs.smy.paiclilearn.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 验证只有明确保存意图才提取长期事实。 */
class ExplicitMemoryHintsTest {
    @Test void extractsOnlyExplicitFacts() {
        assertEquals("用户偏好中文", ExplicitMemoryHints.extractFact("请记住：用户偏好中文。"));
        assertNull(ExplicitMemoryHints.extractFact("请用中文回答这一次"));
        assertNull(ExplicitMemoryHints.extractFact("记住"));
    }
}
