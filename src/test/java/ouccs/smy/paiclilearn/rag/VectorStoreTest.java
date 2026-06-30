package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorStoreTest {
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
    void storesChunksRelationsAndSearchesByVector() throws Exception {
        store = new VectorStore(TEST_PROJECT);
        store.clearProject();
        store.insertChunks(List.of(
                entry("src/UserService.java", "class", "UserService",
                        "class UserService { UserRepository repository; }", new float[]{1f, 0f}),
                entry("src/PlanRunner.java", "class", "PlanRunner",
                        "class PlanRunner { void executePlan() {} }", new float[]{0f, 1f})
        ));
        store.insertRelations(List.of(new CodeRelation(
                "src/UserService.java", "UserService", "src/UserRepository.java", "UserRepository", "uses")));

        List<VectorStore.SearchResult> results = store.search(new float[]{1f, 0f}, 2);

        assertEquals("UserService", results.get(0).name());
        assertEquals(2, store.getStats().chunkCount());
        assertEquals(1, store.getStats().relationCount());
        assertFalse(store.getRelations("UserService").isEmpty());
    }

    @Test
    void searchesByEscapedKeyword() throws Exception {
        store = new VectorStore(TEST_PROJECT);
        store.clearProject();
        store.insertChunks(List.of(entry("src/User_Repo.java", "method", "find_user",
                "return repository.find_user(id);", new float[]{1f})));

        List<VectorStore.SearchResult> results = store.searchByKeyword("find_user");

        assertEquals(1, results.size());
        assertTrue(results.get(0).content().contains("repository"));
    }

    private VectorStore.CodeChunkEntry entry(String file, String type, String name, String content, float[] embedding) {
        return new VectorStore.CodeChunkEntry(new CodeChunk(file, type, name, content, 1, 3), embedding);
    }
}
