package ouccs.smy.paiclilearn.hitl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** [s05 新增] 验证 SwitchableHitlHandler 的委托切换。 */
class SwitchableHitlHandlerTest {
    @Test void delegatesToInner() {
        var handler = new SwitchableHitlHandler(new TerminalHitlHandler(true));
        assertTrue(handler.isEnabled());
        handler.setEnabled(false);
        assertFalse(handler.isEnabled());
    }
    @Test void switchDelegate() {
        var handler = new SwitchableHitlHandler(new TerminalHitlHandler(false));
        assertFalse(handler.isEnabled());
        handler.setDelegate(new TerminalHitlHandler(true));
        assertTrue(handler.isEnabled());
    }
}
