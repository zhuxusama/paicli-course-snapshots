package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩器——当短期记忆条目过多时，用 MAP-REDUCE 策略压缩旧条目。
 *
 * <p>压缩策略：
 * <ol>
 *   <li>MAP 阶段：将旧条目每 5 条一组分片，每组调 LLM 生成片段摘要。</li>
 *   <li>REDUCE 阶段：多个片段摘要再次调 LLM 合并成一个最终摘要。</li>
 *   <li>保留最近 N 条原始条目不压缩（默认 3 条）。</li>
 *   <li>LLM 调用失败时自动降级为文本截断。</li>
 * </ol>
 *
 * <p>由 {@link MemoryManager} 在每次消息写入后自动调用 {@link #compress(ConversationMemory)}，
 * 触发条件由 {@link TokenBudget#needsCompression(ConversationMemory, double)} 控制。</p>
 *
 * @since s09
 */
public class ContextCompressor {
    private LlmClient llmClient;
    private final int retainRecentRounds;

    private static final String MAP_PROMPT = """
            请将以下对话片段压缩成一段简洁的摘要，保留关键信息：
            - 用户的需求和意图
            - 已执行的操作和结果
            - 做出的决策和结论
            - 重要的技术细节

            对话片段：
            %s

            请用中文输出摘要，控制在200字以内。
            """;

    private static final String REDUCE_PROMPT = """
            请将以下多个摘要合并成一个整体摘要，保留所有关键信息。

            各片段摘要：
            %s

            请用中文输出合并摘要，控制在300字以内。
            """;



    /** [s09 新增] 设置 LLM 客户端，用于模型热切换时更新。 */
    public void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ContextCompressor(LlmClient llmClient) {
        this(llmClient, 3);
    }

    /**
     * @param llmClient          LLM 客户端
     * @param retainRecentRounds 保留最近 N 条完整条目不压缩
     */
    public ContextCompressor(LlmClient llmClient, int retainRecentRounds) {
        this.llmClient = llmClient;
        this.retainRecentRounds = retainRecentRounds;
    }

    /**
     * 压缩对话记忆——将旧条目 MAP-REDUCE 为摘要，保留最近条目。
     *
     * @param memory 短期记忆
     * @return 压缩后的摘要文本；如果条目不足或 LLM 全部失败则返回 null
     */
    public String compress(ConversationMemory memory) {
        List<MemoryEntry> allEntries = memory.getAll();
        if (allEntries.size() <= retainRecentRounds) {
            return null;
        }

        int splitPoint = allEntries.size() - retainRecentRounds;
        List<MemoryEntry> oldEntries = new ArrayList<>(allEntries.subList(0, splitPoint));
        List<MemoryEntry> recentEntries = new ArrayList<>(allEntries.subList(splitPoint, allEntries.size()));

        List<String> chunkSummaries = mapPhase(oldEntries);
        if (chunkSummaries.isEmpty()) {
            return null;
        }

        String finalSummary;
        if (chunkSummaries.size() == 1) {
            finalSummary = chunkSummaries.get(0);
        } else {
            finalSummary = reducePhase(chunkSummaries);
        }

        memory.clear();
        MemoryEntry summaryEntry = MemoryEntry.summary(
                "[历史对话摘要] " + finalSummary, ".");
        memory.store(summaryEntry);

        for (MemoryEntry entry : recentEntries) {
            memory.store(entry);
        }

        return finalSummary;
    }

    // ---- MAP-REDUCE 内部实现 ----

    private List<String> mapPhase(List<MemoryEntry> oldEntries) {
        List<String> summaries = new ArrayList<>();
        int chunkSize = 5;
        List<List<MemoryEntry>> chunks = partition(oldEntries, chunkSize);

        for (List<MemoryEntry> chunk : chunks) {
            StringBuilder chunkText = new StringBuilder();
            for (MemoryEntry entry : chunk) {
                chunkText.append(entry.type()).append(": ")
                        .append(entry.content()).append("\n\n");
            }

            try {
                String prompt = String.format(MAP_PROMPT, chunkText);
                LlmClient.ChatResponse response = llmClient.chat(List.of(
                        LlmClient.Message.system("你是一个对话摘要助手。"),
                        LlmClient.Message.user(prompt)
                ), null);
                summaries.add(response.content());
            } catch (IOException e) {
                System.err.println("⚠️ 摘要生成失败: " + e.getMessage());
                // 降级：直接截取前 200 字
                String fallback = chunkText.substring(0, Math.min(200, chunkText.length()));
                summaries.add("[压缩] " + fallback);
            }
        }

        return summaries;
    }

    private String reducePhase(List<String> summaries) {
        String joined = String.join("\n\n---\n\n", summaries);

        try {
            String prompt = String.format(REDUCE_PROMPT, joined);
            LlmClient.ChatResponse response = llmClient.chat(List.of(
                    LlmClient.Message.system("你是一个摘要合并助手。"),
                    LlmClient.Message.user(prompt)
            ), null);
            return response.content();
        } catch (IOException e) {
            System.err.println("⚠️ 摘要合并失败: " + e.getMessage());
            return String.join("；", summaries);
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
