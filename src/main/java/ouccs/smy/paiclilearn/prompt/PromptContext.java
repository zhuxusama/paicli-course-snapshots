package ouccs.smy.paiclilearn.prompt;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * s07 组装 system prompt 所需的不可变运行时上下文。
 *
 * @param approvalMode 审批策略名称：suggest、auto 或 never
 * @param personality 人格资源名称
 * @param currentDate 当前日期
 * @param zoneId 当前时区
 * @param variables 额外模板变量
 * @since s07
 */
public record PromptContext(
        String approvalMode,
        String personality,
        LocalDate currentDate,
        ZoneId zoneId,
        Map<String, String> variables
) {
    public PromptContext {
        approvalMode = normalizeName(approvalMode, "suggest", "approvalMode");
        personality = normalizeName(personality, "calm", "personality");
        zoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
        currentDate = currentDate == null ? LocalDate.now(zoneId) : currentDate;
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    /** 创建使用稳定默认值的 Builder。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 创建默认 suggest/calm 上下文。 */
    public static PromptContext empty() {
        return builder().build();
    }

    /** 返回变量值；不存在时返回空字符串。 */
    public String variable(String key) {
        return key == null ? "" : variables.getOrDefault(key, "");
    }

    private static String normalizeName(String value, String fallback, String field) {
        String normalized = value == null || value.isBlank()
                ? fallback : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException(field + " 包含非法字符: " + value);
        }
        return normalized;
    }

    /** 用于逐项构造不可变 PromptContext。 */
    public static final class Builder {
        private String approvalMode = "suggest";
        private String personality = "calm";
        private LocalDate currentDate;
        private ZoneId zoneId = ZoneId.systemDefault();
        private final Map<String, String> variables = new LinkedHashMap<>();

        public Builder approvalMode(String approvalMode) {
            this.approvalMode = approvalMode;
            return this;
        }

        public Builder personality(String personality) {
            this.personality = personality;
            return this;
        }

        public Builder currentDate(LocalDate currentDate) {
            this.currentDate = currentDate;
            return this;
        }

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        public Builder variable(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                variables.put(key.trim(), String.valueOf(value));
            }
            return this;
        }

        public PromptContext build() {
            return new PromptContext(approvalMode, personality, currentDate, zoneId, variables);
        }
    }
}
