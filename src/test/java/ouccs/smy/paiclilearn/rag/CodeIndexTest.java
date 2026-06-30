package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeIndexTest {

    @TempDir
    Path tempDir;

    private String previousRagDir;

    @BeforeEach
    void setUp() {
        previousRagDir = System.getProperty("paicli.rag.dir");
        System.setProperty("paicli.rag.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousRagDir == null) {
            System.clearProperty("paicli.rag.dir");
        } else {
            System.setProperty("paicli.rag.dir", previousRagDir);
        }
    }

    @Test
    void returnsEmptyResultForMissingPath() {
        CodeIndex indexer = new CodeIndex(new DeterministicEmbeddingClient());

        CodeIndex.IndexResult result = indexer.index("__missing_path__");

        assertEquals(0, result.chunkCount());
        assertTrue(result.message().contains("路径不存在"));
    }

    @Test
    void indexesTestResourcesIntoSqliteVectorStore() throws Exception {
        CodeIndex indexer = new CodeIndex(new DeterministicEmbeddingClient());

        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        assertTrue(result.chunkCount() > 0);
        assertTrue(result.relationCount() > 0);
        assertTrue(result.message().contains("已写入 SQLite"));
        try (VectorStore store = new VectorStore(Path.of("src/test/resources/rag").toAbsolutePath().normalize().toString())) {
            VectorStore.IndexStats stats = store.getStats();
            assertEquals(result.chunkCount(), stats.chunkCount());
            assertEquals(result.relationCount(), stats.relationCount());
        }
    }

    @Test
    void reportsProgress() {
        List<String> messages = new ArrayList<>();
        CodeIndex indexer = new CodeIndex(new DeterministicEmbeddingClient(), messages::add);

        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        assertTrue(result.chunkCount() > 0);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("开始索引:")));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("发现 ")));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("索引完成:")));
    }

    private static class DeterministicEmbeddingClient extends EmbeddingClient {
        @Override
        public float[] embed(String text) throws IOException {
            float user = text.contains("User") || text.contains("user") ? 1.0f : 0.1f;
            float service = text.contains("Service") || text.contains("service") ? 1.0f : 0.1f;
            return new float[]{user, service, 0.5f};
        }
    }
}
