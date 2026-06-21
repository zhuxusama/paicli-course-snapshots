package ouccs.smy.paiclilearn.hitl;

/**
 * 审批请求——HITL 审批前传递的工具调用信息。
 *
 * @param toolName    工具名称
 * @param arguments   参数 JSON 字符串
 * @param dangerLevel 风险等级标签（来自 ApprovalPolicy）
 * @param riskDesc    风险描述
 * @param suggestion  LLM 给出的操作理由
 * @since s05 [s05 新增]
 */
public record ApprovalRequest(
        String toolName,
        String arguments,
        String dangerLevel,
        String riskDesc,
        String suggestion
) {
    public static ApprovalRequest of(String toolName, String arguments, String suggestion) {
        return new ApprovalRequest(toolName, arguments,
                ApprovalPolicy.dangerLevel(toolName),
                ApprovalPolicy.riskDescription(toolName),
                suggestion);
    }

    /** 生成终端显示用的审批提示文本。 */
    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("════════════════════════════════════════\n");
        sb.append("  ").append(dangerLevel).append(" 工具: ").append(toolName).append("\n");
        sb.append("  风险: ").append(riskDesc).append("\n");
        if (suggestion != null && !suggestion.isBlank()) {
            sb.append("  理由: ").append(suggestion).append("\n");
        }
        sb.append("  参数: ").append(truncateArgs(arguments, 200)).append("\n");
        sb.append("════════════════════════════════════════\n");
        sb.append("  [y=允许] [n=拒绝] [a=本次全部允许] [s=跳过]\n");
        return sb.toString();
    }

    private static String truncateArgs(String args, int max) {
        return args != null && args.length() > max ? args.substring(0, max) + "..." : args;
    }
}
