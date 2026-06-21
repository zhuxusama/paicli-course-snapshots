package ouccs.smy.paiclilearn.memory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** 统一短期记录、显式长期保存、检索和管理操作。 */
public class MemoryManager {
    private final ConversationMemory shortTerm;
    private final LongTermMemory longTerm;
    private final MemoryRetriever retriever;
    private final String projectKey;

    public MemoryManager(LongTermMemory longTerm, String projectKey) {
        this.shortTerm = new ConversationMemory(100);
        this.longTerm = Objects.requireNonNull(longTerm);
        this.projectKey = normalizeProjectKey(projectKey);
        this.retriever = new MemoryRetriever(shortTerm, longTerm);
    }

    public static MemoryManager createDefault(String projectPath) {
        return new MemoryManager(LongTermMemory.createDefault(), projectPath);
    }

    public static MemoryManager inMemory() {
        return new MemoryManager(LongTermMemory.inMemory(), Path.of("").toAbsolutePath().toString());
    }

    public void addUserMessage(String content) { storeMessage(content, MemoryEntry.MemoryType.USER); }
    public void addAssistantMessage(String content) { storeMessage(content, MemoryEntry.MemoryType.ASSISTANT); }
    public void addToolResult(String content) { storeMessage(content, MemoryEntry.MemoryType.TOOL); }

    public MemoryEntry saveFact(String fact, String scope) {
        MemoryEntry entry = MemoryEntry.fact(fact, scope, projectKey);
        longTerm.store(entry);
        return entry;
    }

    public String buildContextForQuery(String query, int maxTokens) {
        return retriever.buildLongTermContext(query, projectKey, maxTokens);
    }

    public List<MemoryEntry> listLongTerm() { return longTerm.visibleIn(projectKey); }
    public List<MemoryEntry> searchLongTerm(String query, int limit) {
        return longTerm.searchVisible(query, projectKey, limit);
    }
    public boolean deleteLongTerm(String id) { return longTerm.delete(id); }
    public int clearProjectLongTerm() { return longTerm.clearProject(projectKey); }
    public void clearShortTerm() { shortTerm.clear(); }
    public int shortTermSize() { return shortTerm.size(); }
    public String projectKey() { return projectKey; }

    private void storeMessage(String content, MemoryEntry.MemoryType type) {
        if (content != null && !content.isBlank()) shortTerm.store(MemoryEntry.message(content, type, projectKey));
    }

    private static String normalizeProjectKey(String projectPath) {
        String value = projectPath == null || projectPath.isBlank() ? "." : projectPath;
        return Path.of(value).toAbsolutePath().normalize().toString();
    }
}
