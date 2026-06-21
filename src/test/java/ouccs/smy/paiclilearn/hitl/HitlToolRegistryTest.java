package ouccs.smy.paiclilearn.hitl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.io.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** [s05 新增] 验证 HitlToolRegistry 的审批链流程。 */
class HitlToolRegistryTest {
    @Test void readOnlyToolBypassesApproval(@TempDir Path tempDir) throws Exception {
        var delegate = new ToolRegistry();
        delegate.setProjectPath(tempDir.toString());
        java.nio.file.Files.writeString(tempDir.resolve("f.txt"), "hello");
        var handler = new TerminalHitlHandler(true); // enabled but won't be called
        var registry = new HitlToolRegistry(delegate, handler);

        String result = registry.executeTool("read_file", "{\"path\":\"f.txt\"}");
        assertTrue(result.contains("hello"), "只读工具应直接通过: " + result);
    }

    @Test void dangerousToolWithAutoApprove(@TempDir Path tempDir) {
        var delegate = new ToolRegistry();
        delegate.setProjectPath(tempDir.toString());
        var in = new BufferedReader(new StringReader("y\n"));
        var handler = new TerminalHitlHandler(true, in, new PrintStream(OutputStream.nullOutputStream()));
        var registry = new HitlToolRegistry(delegate, handler);

        String result = registry.executeTool("write_file", "{\"path\":\"out.txt\",\"content\":\"hi\"}");
        assertTrue(result.contains("已写入"), "应写入成功: " + result);
    }

    @Test void dangerousToolRejected(@TempDir Path tempDir) {
        var delegate = new ToolRegistry();
        delegate.setProjectPath(tempDir.toString());
        var in = new BufferedReader(new StringReader("n\n危险\n"));
        var handler = new TerminalHitlHandler(true, in, new PrintStream(OutputStream.nullOutputStream()));
        var registry = new HitlToolRegistry(delegate, handler);

        String result = registry.executeTool("write_file", "{\"path\":\"out.txt\",\"content\":\"x\"}");
        assertTrue(result.contains("拒绝") || result.contains("HITL"), "应被拒绝: " + result);
    }

    @Test void pathEscapeBlockedByPolicy(@TempDir Path tempDir) {
        var delegate = new ToolRegistry();
        delegate.setProjectPath(tempDir.toString());
        var handler = new TerminalHitlHandler(false);
        var registry = new HitlToolRegistry(delegate, handler);

        String result = registry.executeTool("write_file", "{\"path\":\"../outside.txt\",\"content\":\"x\"}");
        assertTrue(result.contains("策略拒绝") || result.contains("逃逸"), "路径逃逸应被策略层阻断: " + result);
    }

    @Test void auditLogContainsRecords(@TempDir Path tempDir) throws Exception {
        var delegate = new ToolRegistry();
        delegate.setProjectPath(tempDir.toString());
        java.nio.file.Files.writeString(tempDir.resolve("f.txt"), "content");
        var handler = new TerminalHitlHandler(false); // disabled - auto approve
        var registry = new HitlToolRegistry(delegate, handler);

        // 只读工具不记录审计
        registry.executeTool("read_file", "{\"path\":\"f.txt\"}");

        // 副作用工具记录审计
        registry.executeTool("write_file", "{\"path\":\"out.txt\",\"content\":\"data\"}");

        // 验证审计日志有记录
        assertFalse(registry.auditLog().readRecent(10).isEmpty());
    }
}
