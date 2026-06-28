package ouccs.smy.paiclilearn.cli;

/**
 * 计划审阅输入解析器——将用户输入映射为执行/补充/取消决策。
 *
 * <p>直接移植自原项目 {@code com.paicli.cli.PlanReviewInputParser}，仅调整课程包名。
 * Enter/yes/run → 执行；ESC/cancel → 取消；其他任意文本 → 补充反馈。</p>
 *
 * @since s11
 */
public final class PlanReviewInputParser {

    /** 审阅决策类型。 */
    public enum DecisionType {
        /** 按当前计划执行 */
        EXECUTE,
        /** 用补充反馈重新规划 */
        SUPPLEMENT,
        /** 取消本次计划 */
        CANCEL
    }

    /** 审阅决策——类型 + 可选反馈文本。 */
    public record Decision(DecisionType type, String feedback) {
        public static Decision execute() {
            return new Decision(DecisionType.EXECUTE, null);
        }

        public static Decision supplement(String feedback) {
            return new Decision(DecisionType.SUPPLEMENT, feedback);
        }

        public static Decision cancel() {
            return new Decision(DecisionType.CANCEL, null);
        }
    }

    private PlanReviewInputParser() {}

    /**
     * 解析用户输入为审阅决策。
     *
     * @param input 用户输入的原始字符串（可能为 null 或 ESC 控制字符）
     * @return 解析后的决策
     */
    public static Decision parse(String input) {
        // ESC 键（）→ 取消
        if (input != null && input.equals("")) {
            return Decision.cancel();
        }

        String trimmed = input == null ? "" : input.trim();

        // 空输入、Enter、yes、run → 直接执行
        if (trimmed.isEmpty()
                || trimmed.equalsIgnoreCase("y")
                || trimmed.equalsIgnoreCase("yes")
                || trimmed.equalsIgnoreCase("run")
                || trimmed.equalsIgnoreCase("/run")) {
            return Decision.execute();
        }

        // 明确的取消命令
        if (trimmed.equalsIgnoreCase("cancel")
                || trimmed.equalsIgnoreCase("esc")
                || trimmed.equalsIgnoreCase("/cancel")) {
            return Decision.cancel();
        }

        // 其他任意文本 → 补充反馈
        return Decision.supplement(trimmed);
    }
}
