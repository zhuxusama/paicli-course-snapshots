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

class CodeRetrieverDemoTest {

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
    void demoHybridCodeSearchFlow() throws Exception {
        String projectPath = "demo-project";
        String query = "哪里使用 UserRepository 查用户";

        System.out.println("【场景】上一章只能切出代码块；本章把代码块写入 SQLite，然后用自然语言问题召回相关代码。");

        try (VectorStore store = new VectorStore(projectPath)) {
            store.clearProject();

            System.out.println("【输入】准备两个代码块和一个查询。");
            System.out.println("query = " + query);
            System.out.println("chunk[0] = UserService.findUserById");
            System.out.println("chunk[1] = PlanRunner.executePlan");

            System.out.println("【执行】调用 VectorStore.insertChunks(...) 写索引，再调用 CodeRetriever.hybridSearch(query, 3)。");
            store.insertChunks(List.of(
                    entry("src/UserService.java", "method", "findUserById",
                            "public User findUserById(Long id) { return userRepository.findById(id); }",
                            new float[]{1f, 0f}),
                    entry("src/PlanRunner.java", "method", "executePlan",
                            "public void executePlan() { planner.nextTask(); }",
                            new float[]{0f, 1f})
            ));

            CodeRetriever retriever = new CodeRetriever(new DemoEmbeddingClient(), store);
            List<VectorStore.SearchResult> results = retriever.hybridSearch(query, 3);
            String formatted = SearchResultFormatter.formatForCli(query, results);

            System.out.println("【输出】检索结果会显示文件、符号、类型、相似度和代码片段。");
            System.out.println(formatted);

            assertFalse(results.isEmpty());
            assertEquals("findUserById", results.get(0).name());
        }
    }

    private VectorStore.CodeChunkEntry entry(String file, String type, String name, String content, float[] embedding) {
        return new VectorStore.CodeChunkEntry(new CodeChunk(file, type, name, content, 1, 2), embedding);
    }

    private static class DemoEmbeddingClient extends EmbeddingClient {
        @Override
        public float[] embed(String text) throws IOException {
            return text.contains("User") || text.contains("用户") ? new float[]{1f, 0f} : new float[]{0f, 1f};
        }
    }
}
