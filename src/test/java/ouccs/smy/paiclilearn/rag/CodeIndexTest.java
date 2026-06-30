package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeIndexTest {

    @Test
    void returnsEmptyResultForMissingPath() {
        CodeIndex indexer = new CodeIndex();

        CodeIndex.IndexResult result = indexer.index("__missing_path__");

        assertEquals(0, result.chunkCount());
        assertTrue(result.message().contains("路径不存在"));
    }

    @Test
    void indexesTestResources() {
        CodeIndex indexer = new CodeIndex();

        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        assertTrue(result.chunkCount() > 0);
        assertTrue(result.relationCount() > 0);
        assertTrue(result.message().contains("索引完成"));
    }

    @Test
    void reportsProgress() {
        List<String> messages = new ArrayList<>();
        CodeIndex indexer = new CodeIndex(messages::add);

        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        assertTrue(result.chunkCount() > 0);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("开始索引:")));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("发现 ")));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("索引完成:")));
    }
}
