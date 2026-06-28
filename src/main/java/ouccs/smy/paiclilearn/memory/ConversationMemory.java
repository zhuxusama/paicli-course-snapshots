package ouccs.smy.paiclilearn.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * s08: 短期记忆使用 token 预算淘汰旧消息，而不是简单按条数裁剪。
 * s09: 接入 compressedSummaries 管道——淘汰条目记录为压缩候选，供 ContextCompressor 消费。
 */
public class ConversationMemory implements Memory {
    private final LinkedHashMap<String, MemoryEntry> entries;
    private int maxTokens;
    private int currentTokens;
    private final List<MemoryEntry> compressedSummaries;

    public ConversationMemory(int maxTokens) {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens 必须大于 0");
        }
        this.entries = new LinkedHashMap<>();
        this.maxTokens = maxTokens;
        this.currentTokens = 0;
        this.compressedSummaries = new ArrayList<>();
    }

    @Override
    public void store(MemoryEntry entry) {
        MemoryEntry previous = entries.put(entry.id(), entry);
        if (previous != null) {
            currentTokens -= previous.tokenCount();
        }
        currentTokens += entry.tokenCount();

        // s08: 保留源项目的滑动窗口语义，超预算时淘汰最早条目并记录为压缩候选。
        while (currentTokens > maxTokens && entries.size() > 1) {
            evictOldest();
        }
    }

    @Override
    public Optional<MemoryEntry> retrieve(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public List<MemoryEntry> search(String query, int limit) {
        Set<String> queryTokens = MemoryQueryTokenizer.tokenize(query);
        return entries.values().stream()
                .filter(entry -> MemoryQueryTokenizer.matches(entry.content(), queryTokens))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryEntry> getAll() {
        return new ArrayList<>(entries.values());
    }

    @Override
    public boolean delete(String id) {
        MemoryEntry removed = entries.remove(id);
        if (removed != null) {
            currentTokens -= removed.tokenCount();
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        entries.clear();
        currentTokens = 0;
        compressedSummaries.clear();
    }

    public int getTokenCount() {
        return currentTokens;
    }

    @Override
    public int size() {
        return entries.size();
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    /** s09: 动态调整 token 上限，用于 context profile 切换。调整后立即淘汰超出部分。 */
    public void setMaxTokens(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        this.maxTokens = maxTokens;
        while (currentTokens > maxTokens && entries.size() > 1) {
            evictOldest();
        }
    }

    private void evictOldest() {
        Iterator<Map.Entry<String, MemoryEntry>> it = entries.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<String, MemoryEntry> oldest = it.next();
            it.remove();
            currentTokens -= oldest.getValue().tokenCount();
            // s09: 淘汰的条目进入压缩候选列表，供 ContextCompressor 消费
            compressedSummaries.add(oldest.getValue());
        }
    }

    /** s09: 只读视图——ContextCompressor 通过此方法获取需要压缩的淘汰条目。 */
    public List<MemoryEntry> getCompressedSummaries() {
        return Collections.unmodifiableList(compressedSummaries);
    }

    /** s09: ContextCompressor 压缩完成后，把摘要注回并清空候选列表。 */
    public void injectSummary(MemoryEntry summary) {
        compressedSummaries.clear();
        store(summary);
    }

    /** s09: 短期记忆使用率——TokenBudget.needsCompression 的判断依据。 */
    public double getUsageRatio() {
        return maxTokens > 0 ? (double) currentTokens / maxTokens : 0;
    }

    /** s09: 带使用率和压缩统计的状态摘要，供终端展示。 */
    public String getStatusSummary() {
        return String.format("短期记忆: %d条 / %d tokens (预算: %d, 使用率: %.0f%%, 已压缩: %d条)",
                entries.size(), currentTokens, maxTokens, getUsageRatio() * 100, compressedSummaries.size());
    }
}
