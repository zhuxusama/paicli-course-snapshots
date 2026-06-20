package ouccs.smy.paiclilearn.cli;

/**
 * Slash 命令解析器——在 CLI 层拦截 /xxx 命令，未知命令直接拒绝不进入 Agent。
 *
 * @since s06 [s06 新增]
 */
public final class CliCommandParser {
    private CliCommandParser() {}

    /** 命令类型枚举——s06 支持的命令子集。s11/s12/s20/s21 逐步扩展。 */
    public enum CommandType {
        NONE,              // 普通文本 → 交给 Agent
        UNKNOWN_COMMAND,   // 未识别的 /xxx → CLI 层直接报错
        CLEAR,             // /clear — 清空对话历史
        SWITCH_MODEL,      // /model [provider] — 切换模型
        EXIT               // /exit — 退出
    }

    /** 解析结果：命令类型 + 参数载荷。 */
    public record ParsedCommand(CommandType type, String payload) {
        static ParsedCommand none() { return new ParsedCommand(CommandType.NONE, null); }
    }

    /**
     * 解析用户输入行。
     * <ul>
     *   <li>以 / 开头且未匹配已知命令 → UNKNOWN_COMMAND</li>
     *   <li>未以 / 开头 → NONE（普通消息，进入 Agent）</li>
     * </ul>
     */
    public static ParsedCommand parse(String input) {
        if (input == null) return ParsedCommand.none();
        String trimmed = input.trim();

        // ---- /clear ----
        if (trimmed.equalsIgnoreCase("/clear") || trimmed.equalsIgnoreCase("clear")) {
            return new ParsedCommand(CommandType.CLEAR, null);
        }

        // ---- /exit ----
        if (trimmed.equalsIgnoreCase("/exit") || trimmed.equalsIgnoreCase("exit")) {
            return new ParsedCommand(CommandType.EXIT, null);
        }

        // ---- /model [provider] ----
        final String modelPrefix = "/model";
        if (trimmed.equalsIgnoreCase(modelPrefix)) {
            return new ParsedCommand(CommandType.SWITCH_MODEL, null);
        }
        if (trimmed.regionMatches(true, 0, modelPrefix + " ", 0, modelPrefix.length() + 1)) {
            return new ParsedCommand(CommandType.SWITCH_MODEL,
                    trimmed.substring(modelPrefix.length() + 1).trim());
        }

        // ---- /xxx 未识别 → 直接拒绝 ----
        if (trimmed.startsWith("/")) {
            return new ParsedCommand(CommandType.UNKNOWN_COMMAND, trimmed);
        }

        // 普通文本 → 进入 Agent
        return ParsedCommand.none();
    }
}
