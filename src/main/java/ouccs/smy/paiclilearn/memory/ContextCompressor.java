package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 压缩 ConversationMemory 中的旧短期记忆，并保留最近几条原始记忆。
 *
 * <p>它和 {@link ConversationHistoryCompactor} 的边界不同：本类处理 PaiCLI
 * 自己维护的短期记忆条目；ConversationHistoryCompactor 处理即将发送给 LLM 的消息历史。</p>
 */
/** [s09 新增] */
public class ContextCompressor {
    private static final int CHUNK_SIZE = 5;

    private static final String MAP_PROMPT = """
            请将下面这段旧对话记忆压缩成一段中文摘要，保留：
            - 用户需求和意图
            - 已执行的操作和结果
            - 已确认的技术决策
            - 后续仍需要使用的关键信息

            旧对话记忆：
            %s

            请控制在 200 字以内。
            """;

    private static final String REDUCE_PROMPT = """
            请将下面多个片段摘要合并成一段整体摘要，去重但不要丢失关键事实。

            片段摘要：
            %s

            请控制在 300 字以内。
            """;

    private final LlmClient llmClient;
    private final int retainRecentEntries;

    public ContextCompressor(LlmClient llmClient) {
        this(llmClient, 6);
    }

    public ContextCompressor(LlmClient llmClient, int retainRecentEntries) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        if (retainRecentEntries < 1) throw new IllegalArgumentException("retainRecentEntries 必须 > 0");
        this.retainRecentEntries = retainRecentEntries;
    }

    /**
     * 将短期记忆中较旧的条目压成 SUMMARY，并把最近的原始条目放回 memory。
     *
     * @return 实际写回 memory 的摘要；条目不足或 LLM 返回空内容时返回 null
     */
    public String compress(ConversationMemory memory, String projectKey) throws IOException {
        Objects.requireNonNull(memory, "memory");
        List<MemoryEntry> all = memory.getAll();
        if (all.size() <= retainRecentEntries) {
            return null;
        }

        int split = all.size() - retainRecentEntries;
        List<MemoryEntry> oldEntries = new ArrayList<>(all.subList(0, split));
        List<MemoryEntry> recentEntries = new ArrayList<>(all.subList(split, all.size()));

        List<String> summaries = map(oldEntries);
        if (summaries.isEmpty()) {
            return null;
        }

        String summary = summaries.size() == 1 ? summaries.get(0) : reduce(summaries);
        if (summary == null || summary.isBlank()) {
            return null;
        }

        memory.clear();
        memory.store(MemoryEntry.summary("[历史对话摘要] " + summary.trim(), projectKey));
        for (MemoryEntry entry : recentEntries) {
            memory.store(entry);
        }
        return summary.trim();
    }

    private List<String> map(List<MemoryEntry> entries) throws IOException {
        List<String> summaries = new ArrayList<>();
        for (int start = 0; start < entries.size(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, entries.size());
            String chunk = render(entries.subList(start, end));
            String summary = ask(String.format(MAP_PROMPT, chunk), "你是对话记忆压缩助手。");
            if (summary != null && !summary.isBlank()) {
                summaries.add(summary.trim());
            }
        }
        return summaries;
    }

    private String reduce(List<String> summaries) throws IOException {
        String joined = String.join("\n\n---\n\n", summaries);
        return ask(String.format(REDUCE_PROMPT, joined), "你是摘要合并助手。");
    }

    private String ask(String prompt, String systemPrompt) throws IOException {
        var response = llmClient.chat(List.of(
                LlmClient.Message.system(systemPrompt),
                LlmClient.Message.user(prompt)
        ), null);
        return response.content();
    }

    private String render(List<MemoryEntry> entries) {
        StringBuilder out = new StringBuilder();
        for (MemoryEntry entry : entries) {
            out.append(entry.type()).append(": ").append(entry.content()).append("\n\n");
        }
        return out.toString();
    }
}
