package ouccs.smy.paiclilearn.hitl;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/** [s05 新增] 验证 TerminalHitlHandler 的交互式审批逻辑。 */
class TerminalHitlHandlerTest {
    @Test void enabledAllowsApproval() {
        var in = new BufferedReader(new StringReader("y\n"));
        var out = new ByteArrayOutputStream();
        var ps = new PrintStream(out);
        var handler = new TerminalHitlHandler(true, in, ps);

        ApprovalResult result = handler.requestApproval(
                ApprovalRequest.of("write_file", "{\"path\":\"test\"}", null));
        assertTrue(result.isApproved());
        assertTrue(out.toString().contains("工具: write_file"));
    }
    @Test void disabledAutoApproves() {
        var handler = new TerminalHitlHandler(false);
        handler.setEnabled(false);
        assertFalse(handler.isEnabled());
        var result = handler.requestApproval(ApprovalRequest.of("write_file", "{}", null));
        assertTrue(result.isApproved());
    }
    @Test void rejectReturnsRejected() {
        var in = new BufferedReader(new StringReader("n\n因为危险\n"));
        var handler = new TerminalHitlHandler(true, in, new PrintStream(OutputStream.nullOutputStream()));
        var result = handler.requestApproval(ApprovalRequest.of("execute_command", "{}", null));
        assertTrue(result.isRejected());
    }
    @Test void approveAllCaches() {
        var in = new BufferedReader(new StringReader("a\n"));
        var handler = new TerminalHitlHandler(true, in, new PrintStream(OutputStream.nullOutputStream()));
        var result = handler.requestApproval(ApprovalRequest.of("write_file", "{}", null));
        assertTrue(result.isApproved());
        assertTrue(handler.isApprovedAllForTool("write_file"));
    }
    @Test void clearApprovedAll() {
        var handler = new TerminalHitlHandler(true);
        var in = new BufferedReader(new StringReader("a\n"));
        // inject cache via approveAll
        var h = new TerminalHitlHandler(true,
                new BufferedReader(new StringReader("a\n")),
                new PrintStream(OutputStream.nullOutputStream()));
        h.requestApproval(ApprovalRequest.of("write_file", "{}", null));
        assertTrue(h.isApprovedAllForTool("write_file"));
        h.clearApprovedAll();
        assertFalse(h.isApprovedAllForTool("write_file"));
    }
}
