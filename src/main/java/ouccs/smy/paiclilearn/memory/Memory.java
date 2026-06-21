package ouccs.smy.paiclilearn.memory;

import java.util.List;
import java.util.Optional;

/** 记忆存储的最小 CRUD 契约。 */
public interface Memory {
    void store(MemoryEntry entry);
    Optional<MemoryEntry> retrieve(String id);
    List<MemoryEntry> search(String query, int limit);
    List<MemoryEntry> getAll();
    boolean delete(String id);
    void clear();
    int size();
}
