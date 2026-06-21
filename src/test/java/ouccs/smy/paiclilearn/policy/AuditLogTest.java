package ouccs.smy.paiclilearn.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s05 新增] 验证 AuditLog 的 JSONL 写入和读取。 */
class AuditLogTest {
    @Test void recordAllowEntry(@TempDir Path tempDir) {
        var log = new AuditLog(tempDir);
        var entry = AuditLog.AuditEntry.allow("write_file", "{\"path\":\"test\"}", 42);
        log.record(entry);
        List<AuditLog.AuditEntry> recent = log.readRecent(10);
        assertFalse(recent.isEmpty());
        assertEquals("allow", recent.get(0).outcome());
        assertEquals("write_file", recent.get(0).tool());
    }
    @Test void recordDenyEntry(@TempDir Path tempDir) {
        var log = new AuditLog(tempDir);
        var entry = AuditLog.AuditEntry.denyByPolicy("execute_command", "{\"cmd\":\"rm\"}", "PathGuard拒绝", 10);
        log.record(entry);
        List<AuditLog.AuditEntry> recent = log.readRecent(10);
        assertEquals("deny", recent.get(0).outcome());
        assertEquals("policy", recent.get(0).approver());
    }
    @Test void sanitizeSensitiveData(@TempDir Path tempDir) {
        var log = new AuditLog(tempDir);
        var entry = AuditLog.AuditEntry.allow("write_file", "{\"apiKey\":\"sk-secret-123\"}", 5);
        log.record(entry);
        List<AuditLog.AuditEntry> recent = log.readRecent(10);
        assertFalse(recent.get(0).args().contains("sk-secret-123"));
    }
}
