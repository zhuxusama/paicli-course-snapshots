package ouccs.smy.paiclilearn.util;

/**
 * [s09 新增] 终端 ANSI 样式辅助。
 * <p>
 * 本章只需要低调显示 token 状态行，因此保留最小可运行实现；后续 TUI 章节会扩展更多样式。
 */
public final class AnsiStyle {
    private static final String RESET = "\u001B[0m";
    private static final String DIM_GRAY = "\u001B[2m\u001B[90m";
    private static final boolean ENABLED = determineEnabled();

    private AnsiStyle() {
    }

    public static String subtle(String text) {
        if (!ENABLED || text == null || text.isEmpty()) {
            return text;
        }
        return DIM_GRAY + text + RESET;
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    private static boolean determineEnabled() {
        String property = System.getProperty("paicli.render.color");
        if (property != null && !property.isBlank()) {
            return Boolean.parseBoolean(property);
        }
        return System.getenv("NO_COLOR") == null;
    }
}
