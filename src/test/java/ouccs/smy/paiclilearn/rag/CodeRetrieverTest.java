package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeRetrieverTest {
    private static final String TEST_PROJECT = "demo-project";

    @TempDir
    Path tempDir;

    private String previousRagDir;
    private VectorStore store;

    @BeforeEach
    void setUp() {
        previousRagDir = System.getProperty("paicli.rag.dir");
        System.setProperty("paicli.rag.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (store != null) {
            store.close();
        }
        if (previousRagDir == null) {
            System.clearProperty("paicli.rag.dir");
        } else {
            System.setProperty("paicli.rag.dir", previousRagDir);
        }
    }

    @Test
    void hybridSearchMergesSemanticAndKeywordResults() throws Exception {
        store = new VectorStore(TEST_PROJECT);
        store.clearProject();
        store.insertChunks(List.of(
                entry("src/UserService.java", "class", "UserService",
                        "class UserService { UserRepository repository; }", new float[]{1f, 0f}),
                entry("src/ApprovalPolicy.java", "class", "ApprovalPolicy",
                        "class ApprovalPolicy { boolean approve(String command) { return true; } }", new float[]{0f, 1f})
        ));

        CodeRetriever retriever = new CodeRetriever(new QueryEmbeddingClient(), store);

        List<VectorStore.SearchResult> results = retriever.hybridSearch("where is UserRepository used", 3);

        assertFalse(results.isEmpty());
        assertEquals("UserService", results.get(0).name());
        assertTrue(results.get(0).similarity() > 1.0);
    }

    @Test
    void relationGraphReadsPersistedRelations() throws Exception {
        store = new VectorStore(TEST_PROJECT);
        store.clearProject();
        store.insertRelations(List.of(new CodeRelation("src/UserService.java", "UserService",
                "src/UserRepository.java", "UserRepository", "uses")));
        CodeRetriever retriever = new CodeRetriever(new QueryEmbeddingClient(), store);

        List<CodeRelation> relations = retriever.getRelationGraph("UserService");

        assertEquals(1, relations.size());
        assertEquals("uses", relations.get(0).relationType());
    }

    private VectorStore.CodeChunkEntry entry(String file, String type, String name, String content, float[] embedding) {
        return new VectorStore.CodeChunkEntry(new CodeChunk(file, type, name, content, 1, 4), embedding);
    }

    private static class QueryEmbeddingClient extends EmbeddingClient {
        @Override
        public float[] embed(String text) throws IOException {
            return text.toLowerCase().contains("user") ? new float[]{1f, 0f} : new float[]{0f, 1f};
        }
    }
}
