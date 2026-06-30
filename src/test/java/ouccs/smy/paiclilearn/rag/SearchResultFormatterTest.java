package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchResultFormatterTest {

    @Test
    void formatsCliResultsWithReadableMetadata() {
        List<VectorStore.SearchResult> results = List.of(new VectorStore.SearchResult(
                "C:/demo/src/UserService.java",
                "method",
                "findUserById",
                "public User findUserById(Long id) {\n    return repository.findById(id);\n}",
                0.92
        ));

        String text = SearchResultFormatter.formatForCli("find user lookup", results);

        assertTrue(text.contains("查询: find user lookup"));
        assertTrue(text.contains("src/UserService.java"));
        assertTrue(text.contains("findUserById"));
        assertTrue(text.contains("score=0.920"));
    }

    @Test
    void formatsToolResultsAsJsonLikePayload() {
        List<VectorStore.SearchResult> results = List.of(new VectorStore.SearchResult(
                "src/UserService.java", "class", "UserService", "class UserService {}", 0.8));

        String json = SearchResultFormatter.formatForTool("user service", results);

        assertTrue(json.contains("\"query\":\"user service\""));
        assertTrue(json.contains("\"file\":\"src/UserService.java\""));
        assertTrue(json.contains("\"similarity\":0.8000"));
    }

    @Test
    void explainsEmptyResult() {
        String text = SearchResultFormatter.formatForCli("missing", List.of());

        assertTrue(text.contains("未找到"));
    }
}
