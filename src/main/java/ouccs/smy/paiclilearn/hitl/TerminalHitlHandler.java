package ouccs.smy.paiclilearn.hitl;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 终端交互式 HITL 审批处理器。
 * <p>通过 stdin/stdout 向用户展示审批提示并读取决策。</p>
 *
 * @since s05 [s05 新增]
 */
public class TerminalHitlHandler implements HitlHandler {
    private volatile boolean enabled;
    private final java.util.Set<String> approvedAll = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final BufferedReader in;
    private final java.io.PrintStream out;
    private static final int MAX_RETRIES = 5;

    public TerminalHitlHandler(boolean enabled) {
        this(enabled, new BufferedReader(new InputStreamReader(System.in)), System.out);
    }

    TerminalHitlHandler(boolean enabled, BufferedReader in, java.io.PrintStream out) {
        this.enabled = enabled;
        this.in = in;
        this.out = out;
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public boolean isApprovedAllForTool(String toolName) { return approvedAll.contains(toolName); }

    @Override
    public void clearApprovedAll() { approvedAll.clear(); }

    @Override
    public synchronized ApprovalResult requestApproval(ApprovalRequest req) {
        if (!enabled) return ApprovalResult.approve();

        // 已对该工具选择"全部允许"
        if (isApprovedAllForTool(req.toolName())) {
            out.println("  ⏭ 已全部允许: " + req.toolName());
            return ApprovalResult.approve();
        }

        out.println(req.toDisplayText());

        for (int i = 0; i < MAX_RETRIES; i++) {
            out.print("  > ");
            try {
                String line = in.readLine();
                if (line == null) return ApprovalResult.reject("输入流已关闭");
                String input = line.trim().toLowerCase();

                return switch (input) {
                    case "y", "yes", "" -> ApprovalResult.approve();
                    case "a", "all" -> { approvedAll.add(req.toolName()); yield ApprovalResult.approveAll(); }
                    case "n", "no" ->  { out.print("  拒绝原因(可选): "); String reason = in.readLine(); yield ApprovalResult.reject(reason != null ? reason : "用户拒绝"); }
                    case "s", "skip" -> ApprovalResult.skip();
                    default -> { out.println("  ❓ 无效输入，请输入 y/n/a/s"); yield null; }
                };
            } catch (java.io.IOException e) {
                return ApprovalResult.reject("输入读取失败: " + e.getMessage());
            }
        }
        return ApprovalResult.reject("超过最大重试次数");
    }
}
