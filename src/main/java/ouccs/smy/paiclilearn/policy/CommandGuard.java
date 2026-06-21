package ouccs.smy.paiclilearn.policy;

import java.util.regex.Pattern;

/**
 * 命令安全守卫：基于正则黑名单阻断危险 shell 命令。
 * <p>这不是主防线（路径在 PathGuard 限制），而是辅助安全网。</p>
 *
 * @since s05 [s05 新增]
 */
public final class CommandGuard {
    private CommandGuard() {}

    private record Rule(String reason, Pattern pattern) {}

    /** 危险命令模式列表。 */
    private static final java.util.List<Rule> RULES = java.util.List.of(
            new Rule("禁止 sudo", Pattern.compile("\\bsudo\\b", Pattern.CASE_INSENSITIVE)),
            new Rule("禁止递归删除根目录", Pattern.compile("\\brm\\s+.*(-rf?\\s+/|/\\s*-rf?)")),
            new Rule("禁止格式化磁盘", Pattern.compile("\\bmkfs\\b")),
            new Rule("禁止写入磁盘设备", Pattern.compile("\\bdd\\s+.*of=/dev/")),
            new Rule("禁止 fork 炸弹", Pattern.compile(":.*:.*&|fork\\s*bomb", Pattern.CASE_INSENSITIVE)),
            new Rule("禁止 curl/wget 管道到 shell", Pattern.compile("\\b(curl|wget)\\s+.*\\|\\s*(sh|bash|zsh)")),
            new Rule("禁止 chmod 777 根目录", Pattern.compile("\\bchmod\\s+.*777\\s+/")),
            new Rule("禁止关机/重启", Pattern.compile("\\b(shutdown|reboot|halt|poweroff)\\b", Pattern.CASE_INSENSITIVE))
    );

    /**
     * 检查命令是否安全。
     * @param command 待执行的命令字符串
     * @return null 表示通过；非 null 字符串是拒绝原因
     */
    public static String check(String command) {
        if (command == null || command.isBlank()) return null;
        String normalized = command.replaceAll("\\s+", " ").trim();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(normalized).find()) {
                return rule.reason();
            }
        }
        return null;
    }
}
