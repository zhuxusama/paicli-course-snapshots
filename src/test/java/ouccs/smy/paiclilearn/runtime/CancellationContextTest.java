package ouccs.smy.paiclilearn.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * [s13 新增] 验证 CancellationToken 和 CancellationContext 的生命周期。
 */
class CancellationContextTest {
    @Test
    void startRunCreatesToken() {
        var token = CancellationContext.startRun();
        assertNotNull(token);
        assertFalse(token.isCancelled());
        CancellationContext.clear(token);
    }

    @Test
    void cancelMarksToken() {
        var token = CancellationContext.startRun();
        assertFalse(CancellationContext.isCancelled());

        token.cancel();
        assertTrue(token.isCancelled());
        assertTrue(CancellationContext.isCancelled());

        CancellationContext.clear(token);
    }

    @Test
    void isCancelledReturnsFalseWhenNoToken() {
        assertFalse(CancellationContext.isCancelled());
    }

    @Test
    void clearRemovesToken() {
        var token = CancellationContext.startRun();
        assertNotNull(CancellationContext.current());

        CancellationContext.clear(token);
        assertFalse(CancellationContext.isCancelled());
    }
}
