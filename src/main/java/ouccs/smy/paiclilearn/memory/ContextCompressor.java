package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * s08: 使用真实 LLM 做 Map-Reduce 对话压缩，并把摘要回注到短期记忆。
 * <p>
 * 【s08 状态：骨架引入】本章定义了压缩的 Map/Reduce prompt 模板、retainRecentRounds
 * 策略、compress() 方法签名和 LLM 依赖注入点。实际的 LLM 调用链路、压缩触发时机
 * （TokenBudget.needsCompression）和 Agent 主循环中的自动压缩逻辑由 s09 完成。
 * </p>
 */
public class ContextCompressor {
    private LlmClient llmClient;
    private final int retainRecentRounds;

    private static final String MAP_PROMPT = """
            请将以下对话片段压缩成一段简洁摘要，保留用户需求、已执行操作、关键结果和技术细节：

            %s
            """;

    private static final String REDUCE_PROMPT = """
            请将以下多个摘要合并为一个整体摘要，保留所有关键事实：

            %s
            """;

    private static final String EXTRACT_FACTS_PROMPT = """
            请从以下对话中提取跨会话仍然成立、未来复用仍有价值的稳定事实，每行一条：

            %s
            """;

    private static final List<String> EPHEMERAL_FACT_PREFIXES = List.of(
            "用户想", "用户要", "用户需要", "用户请求", "帮我", "让我", "当前这一轮", "本次任务"
    );

    private static final List<String> SPECULATION_CUES = List.of("可能", "应该", "猜测", "推测", "提醒");

    private static final List<String> DURABLE_FACT_HINTS = List.of(
            "用户偏好", "用户习惯", "项目", "仓库", "路径", "技术栈", "版本", "模型", "接口", "配置", "环境变量", "命令", "约定", "规则"
    );

    public ContextCompressor(LlmClient llmClient) {
        this(llmClient, 3);
    }

    public ContextCompressor(LlmClient llmClient, int retainRecentRounds) {
        this.llmClient = llmClient;
        this.retainRecentRounds = retainRecentRounds;
    }

    public void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String compress(ConversationMemory memory) {
        List<MemoryEntry> allEntries = memory.getAll();
        if (allEntries.size() <= retainRecentRounds || llmClient == null) {
            return null;
        }

        int splitPoint = allEntries.size() - retainRecentRounds;
        List<MemoryEntry> oldEntries = new ArrayList<>(allEntries.subList(0, splitPoint));
        List<MemoryEntry> recentEntries = new ArrayList<>(allEntries.subList(splitPoint, allEntries.size()));
        List<String> chunkSummaries = mapPhase(oldEntries);
        if (chunkSummaries.isEmpty()) {
            return null;
        }
        String finalSummary = chunkSummaries.size() == 1 ? chunkSummaries.get(0) : reducePhase(chunkSummaries);
        memory.clear();
        MemoryEntry summaryEntry = new MemoryEntry(
                "summary-" + UUID.randomUUID().toString().substring(0, 8),
                "[历史对话摘要] " + finalSummary,
                MemoryEntry.MemoryType.SUMMARY,
                null,
                MemoryEntry.estimateTokens(finalSummary)
        );
        memory.store(summaryEntry);
        for (MemoryEntry entry : recentEntries) {
            memory.store(entry);
        }
        return finalSummary;
    }

    public List<String> extractFacts(List<MemoryEntry> entries, LongTermMemory longTermMemory) {
        if (entries == null || entries.isEmpty() || llmClient == null) {
            return List.of();
        }
        StringBuilder conversation = new StringBuilder();
        for (MemoryEntry entry : entries) {
            conversation.append(resolveSource(entry).toUpperCase(Locale.ROOT))
                    .append("(").append(entry.getType()).append("): ")
                    .append(entry.getContent()).append("\n\n");
        }
        try {
            String prompt = String.format(EXTRACT_FACTS_PROMPT, conversation);
            List<LlmClient.Message> messages = List.of(
                    LlmClient.Message.system("你是信息提取助手，只输出关键事实。"),
                    LlmClient.Message.user(prompt)
            );
            LlmClient.ChatResponse response = llmClient.chat(messages, null);
            List<String> facts = new ArrayList<>();
            for (String line : response.content().split("\n")) {
                String fact = normalizeFactLine(line);
                if (isPersistentFactCandidate(fact)) {
                    facts.add(fact);
                    longTermMemory.store(MemoryEntry.fact(fact, "project", ""));
                }
            }
            return facts;
        } catch (IOException e) {
            System.err.println("事实提取失败: " + e.getMessage());
            return List.of();
        }
    }

    private List<String> mapPhase(List<MemoryEntry> oldEntries) {
        List<String> summaries = new ArrayList<>();
        int chunkSize = 5;
        for (int i = 0; i < oldEntries.size(); i += chunkSize) {
            List<MemoryEntry> chunk = oldEntries.subList(i, Math.min(i + chunkSize, oldEntries.size()));
            StringBuilder chunkText = new StringBuilder();
            for (MemoryEntry entry : chunk) {
                chunkText.append(entry.getType()).append(": ").append(entry.getContent()).append("\n\n");
            }
            try {
                String prompt = String.format(MAP_PROMPT, chunkText);
                summaries.add(llmClient.chat(List.of(
                        LlmClient.Message.system("你是对话摘要助手。"),
                        LlmClient.Message.user(prompt)
                ), null).content());
            } catch (IOException e) {
                String fallback = chunkText.substring(0, Math.min(200, chunkText.length()));
                summaries.add("[压缩] " + fallback);
            }
        }
        return summaries;
    }

    private String reducePhase(List<String> summaries) {
        try {
            String prompt = String.format(REDUCE_PROMPT, String.join("\n\n---\n\n", summaries));
            return llmClient.chat(List.of(
                    LlmClient.Message.system("你是摘要合并助手。"),
                    LlmClient.Message.user(prompt)
            ), null).content();
        } catch (IOException e) {
            return String.join("；", summaries);
        }
    }

    private String resolveSource(MemoryEntry entry) {
        String source = entry.getMetadata().get("source");
        return source == null || source.isBlank() ? "unknown" : source;
    }

    private String normalizeFactLine(String line) {
        String fact = line == null ? "" : line.trim();
        return fact.startsWith("- ") ? fact.substring(2).trim() : fact;
    }

    private boolean isPersistentFactCandidate(String fact) {
        if (fact == null || fact.length() <= 5) {
            return false;
        }
        String normalized = fact.toLowerCase(Locale.ROOT);
        for (String prefix : EPHEMERAL_FACT_PREFIXES) {
            if (normalized.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        for (String cue : SPECULATION_CUES) {
            if (normalized.contains(cue.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return normalized.contains("：") || normalized.contains(":")
                || DURABLE_FACT_HINTS.stream().anyMatch(hint -> normalized.contains(hint.toLowerCase(Locale.ROOT)));
    }
}
