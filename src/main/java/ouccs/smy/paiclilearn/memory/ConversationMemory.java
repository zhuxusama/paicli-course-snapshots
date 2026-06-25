package ouccs.smy.paiclilearn.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** 当前会话的有界短期记忆；s09 再用 token 预算和摘要替代条目数边界。 */
/** [s08 新增] */
public class ConversationMemory implements Memory {
    private final LinkedHashMap<String, MemoryEntry> entries = new LinkedHashMap<>();
    private final int maxEntries;

    public ConversationMemory(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries 必须大于 0");
        this.maxEntries = maxEntries;
    }

    @Override public void store(MemoryEntry entry) {
        entries.put(entry.id(), entry);
        while (entries.size() > maxEntries) {
            entries.remove(entries.keySet().iterator().next());
        }
    }
    @Override public Optional<MemoryEntry> retrieve(String id) { return Optional.ofNullable(entries.get(id)); }
    @Override public List<MemoryEntry> search(String query, int limit) {
        String needle = query == null ? "" : query.toLowerCase();
        return entries.values().stream().filter(e -> e.content().toLowerCase().contains(needle)).limit(limit).toList();
    }
    @Override public List<MemoryEntry> getAll() { return new ArrayList<>(entries.values()); }
    @Override public boolean delete(String id) { return entries.remove(id) != null; }
    @Override public void clear() { entries.clear(); }
    @Override public int size() { return entries.size(); }

    /** [s09 新增] 估算当前所有条目的 token 总数，供压缩触发判断。 */
    public int getTokenCount() {
        return entries.values().stream().mapToInt(MemoryEntry::tokenCount).sum();
    }

    /** [s09 新增] 动态调整条目数上限，用于 context profile 切换。 */
    public void setMaxTokens(int maxTokens) {
        // 简化：用条目数近似 token 上限
        while (entries.size() > 0 && getTokenCount() > maxTokens) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    /** [s09 新增] 状态摘要。 */
    public String getStatusSummary() {
        return "短期记忆: " + entries.size() + " 条, ~" + getTokenCount() + " tokens";
    }
}
