package ouccs.smy.paiclilearn.hitl;

import ouccs.smy.paiclilearn.policy.PolicyException;

import java.util.Set;

/**
 * 审批策略：判断工具是否需要人工审批及风险等级。
 *
 * @since s05 [s05 新增]
 */
public final class ApprovalPolicy {
    private ApprovalPolicy() {}

    /** 需要审批的副作用工具集。 */
    private static final Set<String> DANGEROUS = Set.of("write_file", "execute_command", "create_project");

    public static Set<String> dangerousTools() { return DANGEROUS; }

    public static boolean requiresApproval(String toolName) {
        return DANGEROUS.contains(toolName);
    }

    public static String dangerLevel(String toolName) {
        if ("execute_command".equals(toolName)) return "\uD83D\uDD34 高危";
        if ("write_file".equals(toolName) || "create_project".equals(toolName)) return "\uD83D\uDFE1 中危";
        return "\uD83D\uDFE2 安全";
    }

    public static String riskDescription(String toolName) {
        return switch (toolName) {
            case "write_file" -> "将内容写入项目文件，可能覆盖已有代码";
            case "execute_command" -> "在项目根目录执行 shell 命令";
            case "create_project" -> "创建新项目目录结构";
            default -> "无风险";
        };
    }

    /** 检查工具调用是否违反策略（Policy 层在 HITL 之前执行）。 */
    public static void checkPolicy(String toolName, String args) {
        if (!requiresApproval(toolName)) return;
        // s05：策略层主要做路径/命令验证，具体逻辑在 PathGuard/CommandGuard
    }
}
