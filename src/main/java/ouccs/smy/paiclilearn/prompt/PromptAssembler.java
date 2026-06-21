package ouccs.smy.paiclilearn.prompt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按确定顺序组装当前 Agent 模式的 system prompt。
 *
 * @since s07
 */
public class PromptAssembler {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)}}");
    private final PromptRepository repository;

    public PromptAssembler(PromptRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** 使用默认 PromptRepository 创建组装器。 */
    public static PromptAssembler createDefault() {
        return new PromptAssembler(PromptRepository.createDefault());
    }

    /**
     * 按 base、personality、mode、approval、runtime 的顺序组装 prompt。
     */
    public String assemble(PromptMode mode, PromptContext context) {
        Objects.requireNonNull(mode, "mode");
        PromptContext ctx = context == null ? PromptContext.empty() : context;
        validateApprovalMode(ctx.approvalMode());

        Map<String, String> variables = new LinkedHashMap<>(ctx.variables());
        variables.put("currentDate", ctx.currentDate().toString());
        variables.put("timeZone", ctx.zoneId().toString());

        StringBuilder prompt = new StringBuilder();
        append(prompt, repository.loadRequired("base.md"));
        append(prompt, repository.loadRequired("personalities/" + ctx.personality() + ".md"));
        append(prompt, repository.loadRequired(mode.resourcePath()));
        append(prompt, repository.loadRequired("approvals/" + ctx.approvalMode() + ".md"));
        append(prompt, repository.loadRequired("context/runtime.md"));

        String assembled = replaceVariables(prompt.toString(), variables).trim();
        if (!assembled.contains("## Language")) {
            throw new IllegalStateException("组装后的 prompt 缺少 ## Language 段");
        }
        return assembled;
    }

    private static void validateApprovalMode(String approvalMode) {
        if (!"suggest".equals(approvalMode)
                && !"auto".equals(approvalMode)
                && !"never".equals(approvalMode)) {
            throw new IllegalArgumentException("未知审批模式: " + approvalMode);
        }
    }

    private static String replaceVariables(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!variables.containsKey(key)) {
                throw new IllegalStateException("未提供 Prompt 变量: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(variables.get(key)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void append(StringBuilder prompt, String section) {
        if (!prompt.isEmpty()) {
            prompt.append("\n\n---\n\n");
        }
        prompt.append(section.trim());
    }
}
