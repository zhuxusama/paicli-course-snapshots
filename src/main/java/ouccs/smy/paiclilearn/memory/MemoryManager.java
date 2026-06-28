package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Memory 管理器——Memory 系统的门面类。
 *
 * <p>统一管理短期记忆（ConversationMemory）、长期记忆（LongTermMemory）、
 * 上下文压缩（ContextCompressor）和记忆检索（MemoryRetriever），
 * 为 Agent 提供简洁的记忆存取接口。</p>
 *
 * <p>[s09 修改] 接入 ContextCompressor：每次写入消息后自动检查 Token 预算，
 * 超过压缩阈值时触发 MAP-REDUCE 压缩，将旧条目合并为摘要保留近期条目。</p>
 *
 * @since s08
 */
public class MemoryManager {
    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);

    private final ConversationMemory shortTerm;   // 进程内短期记忆，LinkedHashMap 存储，按 token 预算驱逐
    private final LongTermMemory longTerm;        // 项目长期记忆，文件存储，按项目分隔
    private final ContextCompressor compressor;   // MAP-REDUCE 压缩器，持有 LlmClient 引用用于 LLM 摘要
    private final MemoryRetriever retriever;      // 记忆检索器，负责根据项目键检索记忆
    private TokenBudget tokenBudget;              // token 预算，负责管理短期记忆的 token 占用
    private final String projectKey;              // 项目键，用于唯一标识项目，避免冲突

    /** 压缩触发阈值——短期记忆 token 占用率超过此比例时触发压缩。 */
    private static final double COMPRESSION_TRIGGER_RATIO = 0.9;

    public MemoryManager(LlmClient llmClient, LongTermMemory longTerm, String projectKey) {
        this.shortTerm = new ConversationMemory(100);
        this.longTerm = Objects.requireNonNull(longTerm);
        this.projectKey = normalizeProjectKey(projectKey);
        this.compressor = new ContextCompressor(llmClient);
        this.retriever = new MemoryRetriever(shortTerm, longTerm);
        this.tokenBudget = new TokenBudget(llmClient.maxContextWindow());
    }

    public static MemoryManager createDefault(LlmClient llmClient, String projectPath) {
        return new MemoryManager(llmClient, LongTermMemory.createDefault(), projectPath);
    }

    public static MemoryManager inMemory(LlmClient llmClient) {
        return new MemoryManager(llmClient, LongTermMemory.inMemory(),
                Path.of("").toAbsolutePath().toString());
    }

    /** [s09 新增] 模型热切换时更新压缩器和 token 预算中的 LLM 客户端。 */
    public void setLlmClient(LlmClient llmClient) {
        this.compressor.setLlmClient(llmClient);
        this.tokenBudget = new TokenBudget(llmClient.maxContextWindow());
    }

    // ---- 消息写入（每次写入后自动触发压缩检查） ----

    public void addUserMessage(String content) {
        storeMessage(content, MemoryEntry.MemoryType.USER);
        compressIfNeeded();
    }

    public void addAssistantMessage(String content) {
        storeMessage(content, MemoryEntry.MemoryType.ASSISTANT);
        compressIfNeeded();
    }

    public void addToolResult(String content) {
        storeMessage(content, MemoryEntry.MemoryType.TOOL);
        compressIfNeeded();
    }

    // ---- 长期记忆操作 ----

    public MemoryEntry saveFact(String fact, String scope) {
        MemoryEntry entry = MemoryEntry.fact(fact, scope, projectKey);
        longTerm.store(entry);
        return entry;
    }

    public List<MemoryEntry> listLongTerm() { return longTerm.visibleIn(projectKey); }

    public List<MemoryEntry> searchLongTerm(String query, int limit) {
        return longTerm.searchVisible(query, projectKey, limit);
    }

    public boolean deleteLongTerm(String id) { return longTerm.delete(id); }

    public int clearProjectLongTerm() { return longTerm.clearProject(projectKey); }

    // ---- 记忆检索与上下文构建 ----

    /**
     * 构建用于 LLM 的记忆上下文——合并短期+长期记忆检索结果。
     *
     * @param query     当前用户查询
     * @param maxTokens 上下文 token 上限
     * @return 格式化的记忆上下文文本
     */
    public String buildContextForQuery(String query, int maxTokens) {
        return retriever.buildContextForQuery(query, projectKey, maxTokens);
    }

    // ---- 压缩 ----

    /**
     * 检查并触发短期记忆压缩。
     *
     * <p>当短期记忆 token 占用率超过 {@link #COMPRESSION_TRIGGER_RATIO} 时，
     * 调用 {@link ContextCompressor#compress(ConversationMemory)} 执行 MAP-REDUCE 压缩。
     * 压缩后旧条目被替换为一条 SUMMARY，保留最近 3 条原始条目。</p>
     *
     * @return 是否执行了压缩
     */
    public boolean compressIfNeeded() {
        if (!tokenBudget.needsCompression(shortTerm, COMPRESSION_TRIGGER_RATIO)) {
            return false;
        }
        int beforeTokens = shortTerm.getTokenCount();
        log.info("短期记忆达到压缩阈值（{}%），{} tokens，触发 MAP-REDUCE 压缩",
                (int) (COMPRESSION_TRIGGER_RATIO * 100), beforeTokens);

        String summary = compressor.compress(shortTerm);
        if (summary != null) {
            int afterTokens = shortTerm.getTokenCount();
            log.info("短期记忆压缩完成: {} -> {} tokens, summaryPreview={}",
                    beforeTokens, afterTokens,
                    summary.substring(0, Math.min(80, summary.length())));
        }
        return summary != null;
    }

    // ---- Token 用量 ----

    /** 记录一轮 LLM 调用的 token 消耗。 */
    public void recordTokenUsage(int inputTokens, int outputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens, 0);
    }

    public void recordTokenUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens, cachedInputTokens);
    }

    // ---- 清理与状态 ----

    public void clearShortTerm() { shortTerm.clear(); }
    public void clearLongTerm() { longTerm.clear(); }

    /** 获取系统状态摘要（token 用量 + 记忆占用）。 */
    public String getSystemStatus() {
        return shortTerm.getStatusSummary() + "\n" +
                longTerm.getStatusSummary() + "\n" +
                tokenBudget.getUsageReport();
    }

    // ---- Getters ----

    public ConversationMemory getConversationMemory() { return shortTerm; }
    public LongTermMemory getLongTermMemory() { return longTerm; }
    public TokenBudget getTokenBudget() { return tokenBudget; }
    public int shortTermSize() { return shortTerm.size(); }
    public String projectKey() { return projectKey; }

    // ---- 内部 ----

    private void storeMessage(String content, MemoryEntry.MemoryType type) {
        if (content != null && !content.isBlank()) {
            shortTerm.store(MemoryEntry.message(content, type, projectKey));
        }
    }

    private static String normalizeProjectKey(String projectPath) {
        String value = projectPath == null || projectPath.isBlank() ? "." : projectPath;
        return Path.of(value).toAbsolutePath().normalize().toString();
    }
}
