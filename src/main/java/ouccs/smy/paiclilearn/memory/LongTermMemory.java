package ouccs.smy.paiclilearn.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** JSON 持久化长期事实，支持项目/global 可见性和可审计删除。 */
/** [s08 新增] */
public class LongTermMemory implements Memory {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LinkedHashMap<String, MemoryEntry> entries = new LinkedHashMap<>();
    private final Path storageFile;

    public LongTermMemory(Path storageFile) {
        this.storageFile = storageFile == null ? null : storageFile.toAbsolutePath().normalize();
        load();
    }

    public static LongTermMemory createDefault() {
        String configured = System.getProperty("paicli.memory.file");
        if (configured == null || configured.isBlank()) configured = System.getenv("PAICLI_MEMORY_FILE");
        Path path = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".paicli", "memory", "long-term.json")
                : Path.of(configured);
        return new LongTermMemory(path);
    }

    public static LongTermMemory inMemory() { return new LongTermMemory(null); }

    @Override public synchronized void store(MemoryEntry entry) {
        if (entry.type() != MemoryEntry.MemoryType.FACT) {
            throw new IllegalArgumentException("长期记忆只接受 FACT");
        }
        boolean duplicate = entries.values().stream().anyMatch(e -> e.scope().equals(entry.scope())
                && e.projectKey().equals(entry.projectKey()) && e.content().equals(entry.content()));
        if (!duplicate) {
            entries.put(entry.id(), entry);
            persist();
        }
    }

    @Override public synchronized Optional<MemoryEntry> retrieve(String id) { return Optional.ofNullable(entries.get(id)); }
    @Override public synchronized List<MemoryEntry> search(String query, int limit) {
        return searchVisible(query, null, limit);
    }

    public synchronized List<MemoryEntry> searchVisible(String query, String projectKey, int limit) {
        Set<String> tokens = MemoryRetriever.tokens(query);
        return entries.values().stream()
                .filter(e -> projectKey == null || isVisible(e, projectKey))
                .filter(e -> MemoryRetriever.score(e.content(), tokens) > 0)
                .sorted(Comparator.comparing(MemoryEntry::timestamp).reversed()).limit(limit).toList();
    }
    @Override public synchronized List<MemoryEntry> getAll() { return new ArrayList<>(entries.values()); }
    @Override public synchronized boolean delete(String id) {
        boolean removed = entries.remove(id) != null;
        if (removed) persist();
        return removed;
    }
    @Override public synchronized void clear() { entries.clear(); persist(); }
    @Override public synchronized int size() { return entries.size(); }

    public synchronized List<MemoryEntry> visibleIn(String projectKey) {
        return entries.values().stream().filter(e -> isVisible(e, projectKey)).toList();
    }

    public synchronized int clearProject(String projectKey) {
        int before = entries.size();
        entries.values().removeIf(e -> "project".equals(e.scope()) && e.projectKey().equals(projectKey));
        int removed = before - entries.size();
        if (removed > 0) persist();
        return removed;
    }

    public static boolean isVisible(MemoryEntry entry, String projectKey) {
        return "global".equals(entry.scope()) || entry.projectKey().equals(projectKey);
    }

    private void persist() {
        if (storageFile == null) return;
        try {
            Path parent = storageFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "long-term-", ".tmp");
            List<Map<String, Object>> data = entries.values().stream().map(LongTermMemory::toMap).toList();
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);
            try {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入长期记忆失败: " + storageFile, e);
        }
    }

    private void load() {
        if (storageFile == null || !Files.exists(storageFile)) return;
        try {
            List<Map<String, Object>> data = MAPPER.readValue(storageFile.toFile(), new TypeReference<>() {});
            for (Map<String, Object> map : data) {
                MemoryEntry entry = fromMap(map);
                entries.put(entry.id(), entry);
            }
        } catch (Exception e) {
            throw new IllegalStateException("长期记忆文件损坏: " + storageFile, e);
        }
    }

    private static Map<String, Object> toMap(MemoryEntry e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.id()); map.put("content", e.content()); map.put("type", e.type().name());
        map.put("timestamp", e.timestamp().toString()); map.put("scope", e.scope());
        map.put("projectKey", e.projectKey()); map.put("tokenCount", e.tokenCount());
        return map;
    }

    private static MemoryEntry fromMap(Map<String, Object> map) {
        return new MemoryEntry(String.valueOf(map.get("id")), String.valueOf(map.get("content")),
                MemoryEntry.MemoryType.valueOf(String.valueOf(map.get("type"))),
                Instant.parse(String.valueOf(map.get("timestamp"))), String.valueOf(map.get("scope")),
                String.valueOf(map.getOrDefault("projectKey", "")), ((Number) map.get("tokenCount")).intValue());
    }
}
