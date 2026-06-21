package ouccs.smy.paiclilearn.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** [s05 新增] 验证 PathGuard 路径安全约束。 */
class PathGuardTest {
    @Test void resolveSafeRelativePath() {
        var guard = new PathGuard("/tmp/project");
        Path resolved = guard.resolveSafe("src/main/java/Foo.java");
        assertTrue(resolved.toString().endsWith("src/main/java/Foo.java") || resolved.toString().endsWith("src\\main\\java\\Foo.java"));
    }
    @Test void resolveSafeBlankReturnsRoot() {
        var guard = new PathGuard("/tmp/project");
        assertEquals(guard.getRootPath(), guard.resolveSafe(""));
    }
    @Test void escapeDetected(@TempDir Path tempDir) {
        var guard = new PathGuard(tempDir.toString());
        assertThrows(PolicyException.class, () -> guard.resolveSafe("../etc/passwd"));
    }
}
