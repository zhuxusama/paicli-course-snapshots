package ouccs.smy.paiclilearn.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** 当前会话的有界短期记忆；s09 再用 token 预算和摘要替代条目数边界。 */
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
}
