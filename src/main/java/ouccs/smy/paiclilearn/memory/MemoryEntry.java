package ouccs.smy.paiclilearn.memory;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * 记忆系统的不可变数据单元。
 *
 * @param id 可审计、可删除的唯一标识
 * @param content 记忆正文
 * @param type 记忆类型
 * @param timestamp 创建时间
 * @param scope project 或 global
 * @param projectKey project scope 所属项目
 * @param tokenCount 近似 token 数
 */
/** [s08 新增] */
public record MemoryEntry(String id, String content, MemoryType type, Instant timestamp,
                          String scope, String projectKey, int tokenCount) {
    /** 本阶段实际保存的记忆类型；s09 增加 SUMMARY 承载压缩后的短期记忆。 */
    public enum MemoryType { USER, ASSISTANT, TOOL, FACT, SUMMARY }

    public MemoryEntry {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("记忆 id 不可为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("记忆内容不可为空");
        if (type == null) throw new IllegalArgumentException("记忆类型不可为空");
        timestamp = timestamp == null ? Instant.now() : timestamp;
        scope = normalizeScope(scope);
        projectKey = projectKey == null ? "" : projectKey.trim();
        if ("project".equals(scope) && projectKey.isBlank()) {
            throw new IllegalArgumentException("project scope 必须提供 projectKey");
        }
        tokenCount = tokenCount > 0 ? tokenCount : estimateTokens(content);
    }

    public static MemoryEntry message(String content, MemoryType type, String projectKey) {
        return new MemoryEntry(UUID.randomUUID().toString(), content, type, Instant.now(),
                "project", projectKey, estimateTokens(content));
    }

    public static MemoryEntry fact(String content, String scope, String projectKey) {
        return new MemoryEntry(UUID.randomUUID().toString(), content, MemoryType.FACT,
                Instant.now(), scope, "global".equalsIgnoreCase(scope) ? "" : projectKey,
                estimateTokens(content));
    }

    public static MemoryEntry summary(String content, String projectKey) {
        return new MemoryEntry("summary-" + UUID.randomUUID(), content, MemoryType.SUMMARY,
                Instant.now(), "project", projectKey, estimateTokens(content));
    }

    /** 使用字符比例给出可解释的近似 token 数，不冒充模型 tokenizer。 */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        long cjk = text.codePoints().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long other = text.length() - cjk;
        return Math.max(1, (int) Math.ceil(cjk / 1.5 + other / 4.0));
    }

    private static String normalizeScope(String scope) {
        String normalized = scope == null || scope.isBlank()
                ? "project" : scope.trim().toLowerCase(Locale.ROOT);
        if (!"project".equals(normalized) && !"global".equals(normalized)) {
            throw new IllegalArgumentException("未知记忆 scope: " + scope);
        }
        return normalized;
    }
}
