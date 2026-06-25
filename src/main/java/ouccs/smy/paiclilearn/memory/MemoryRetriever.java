package ouccs.smy.paiclilearn.memory;

import java.util.*;

/** 合并短期与长期记忆，并用可解释关键词相关性排序。 */
/** [s08 新增] */
public class MemoryRetriever {
    private final ConversationMemory shortTerm;
    private final LongTermMemory longTerm;

    public MemoryRetriever(ConversationMemory shortTerm, LongTermMemory longTerm) {
        this.shortTerm = Objects.requireNonNull(shortTerm);
        this.longTerm = Objects.requireNonNull(longTerm);
    }

    public List<MemoryEntry> retrieve(String query, String projectKey, int limit, int maxTokens) {
        Set<String> tokens = tokens(query);
        List<Scored> scored = new ArrayList<>();
        shortTerm.getAll().forEach(e -> addIfRelevant(scored, e, tokens, 1.0));
        longTerm.visibleIn(projectKey).forEach(e -> addIfRelevant(scored, e, tokens, 1.2));
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(s -> s.entry().timestamp(), Comparator.reverseOrder()));

        List<MemoryEntry> result = new ArrayList<>();
        int used = 0;
        for (Scored candidate : scored) {
            if (result.size() >= limit || used + candidate.entry().tokenCount() > maxTokens) continue;
            result.add(candidate.entry());
            used += candidate.entry().tokenCount();
        }
        return result;
    }

    public String buildLongTermContext(String query, String projectKey, int maxTokens) {
        Set<String> tokens = tokens(query);
        List<Scored> scored = new ArrayList<>();
        longTerm.visibleIn(projectKey).forEach(e -> addIfRelevant(scored, e, tokens, 1.2));
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(s -> s.entry().timestamp(), Comparator.reverseOrder()));
        StringBuilder context = new StringBuilder();
        int used = 0;
        for (Scored candidate : scored) {
            if (used + candidate.entry().tokenCount() > maxTokens) continue;
            if (context.isEmpty()) context.append("## 相关长期记忆\n\n");
            context.append("- [").append(candidate.entry().scope()).append("] ")
                    .append(candidate.entry().content()).append('\n');
            used += candidate.entry().tokenCount();
        }
        return context.toString().trim();
    }

    public String buildContextForQuery(String query, String projectKey, int maxTokens) {
        List<MemoryEntry> entries = retrieve(query, projectKey, 8, maxTokens);
        if (entries.isEmpty()) return "";
        StringBuilder context = new StringBuilder("## 相关记忆\n\n");
        for (MemoryEntry entry : entries) {
            // 这里把短期会话和长期事实统一交给 prompt 层，让 Agent 当前轮真正使用刚发生的上下文。
            String source = entry.type() == MemoryEntry.MemoryType.FACT ? entry.scope() : "short-term";
            context.append("- [").append(source).append("] ")
                    .append(entry.content()).append('\n');
        }
        return context.toString().trim();
    }

    private static void addIfRelevant(List<Scored> target, MemoryEntry entry, Set<String> query, double weight) {
        double score = score(entry.content(), query) * weight;
        if (score > 0) target.add(new Scored(entry, score));
    }

    static Set<String> tokens(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : lower.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank()) result.add(word);
        }
        lower.codePoints().filter(c -> c >= 0x4E00 && c <= 0x9FFF)
                .forEach(c -> result.add(new String(Character.toChars(c))));
        return result;
    }

    static double score(String content, Set<String> queryTokens) {
        if (content == null || queryTokens.isEmpty()) return 0;
        String lower = content.toLowerCase(Locale.ROOT);
        long matches = queryTokens.stream().filter(lower::contains).count();
        return (double) matches / queryTokens.size();
    }

    private record Scored(MemoryEntry entry, double score) {}
}
