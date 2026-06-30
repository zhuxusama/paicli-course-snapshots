package ouccs.smy.paiclilearn.rag;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * s16: CodeRetriever 是“自然语言问题 -> 代码块”的召回入口。
 * <p>
 * 它先用 EmbeddingClient 做语义召回，再用 RagQueryTokenizer 做关键词补召回，
 * 最后把同一文件的结果限流，避免一个大文件淹没整页答案。
 */
public class CodeRetriever implements AutoCloseable {
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final boolean ownsStore;

    public CodeRetriever(String projectPath) throws SQLException {
        this(new EmbeddingClient(), new VectorStore(projectPath), true);
    }

    public CodeRetriever(String projectPath, EmbeddingClient embeddingClient) throws SQLException {
        this(embeddingClient, new VectorStore(projectPath), true);
    }

    public CodeRetriever(EmbeddingClient embeddingClient, VectorStore vectorStore) {
        this(embeddingClient, vectorStore, false);
    }

    private CodeRetriever(EmbeddingClient embeddingClient, VectorStore vectorStore, boolean ownsStore) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.ownsStore = ownsStore;
    }

    public List<VectorStore.SearchResult> semanticSearch(String query, int topK) throws IOException, SQLException {
        float[] queryEmbedding = embeddingClient.embed(query);
        return vectorStore.search(queryEmbedding, topK);
    }

    public List<VectorStore.SearchResult> keywordSearch(String query) throws SQLException {
        Map<String, VectorStore.SearchResult> merged = new LinkedHashMap<>();
        for (String token : RagQueryTokenizer.tokenize(query)) {
            for (VectorStore.SearchResult result : vectorStore.searchByKeyword(token)) {
                merged.putIfAbsent(keyOf(result), result);
            }
        }
        return new ArrayList<>(merged.values());
    }

    public List<VectorStore.SearchResult> hybridSearch(String query, int topK) throws IOException, SQLException {
        Map<String, VectorStore.SearchResult> merged = new LinkedHashMap<>();

        for (VectorStore.SearchResult result : semanticSearch(query, Math.max(topK * 2, topK))) {
            merged.put(keyOf(result), result);
        }
        for (VectorStore.SearchResult keywordHit : keywordSearch(query)) {
            merged.merge(keyOf(keywordHit), boost(keywordHit, 0.35), this::combineScores);
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(VectorStore.SearchResult::similarity).reversed())
                .filter(new PerFileLimiter(2)::allow)
                .limit(topK)
                .toList();
    }

    public List<CodeRelation> getRelationGraph(String symbolName) throws SQLException {
        return vectorStore.getRelations(symbolName);
    }

    public VectorStore.IndexStats getStats() throws SQLException {
        return vectorStore.getStats();
    }

    private VectorStore.SearchResult combineScores(VectorStore.SearchResult semanticHit,
                                                   VectorStore.SearchResult keywordHit) {
        return new VectorStore.SearchResult(
                semanticHit.filePath(),
                semanticHit.chunkType(),
                semanticHit.name(),
                semanticHit.content(),
                semanticHit.similarity() + keywordHit.similarity()
        );
    }

    private VectorStore.SearchResult boost(VectorStore.SearchResult result, double amount) {
        double typeBoost = switch (result.chunkType()) {
            case "class" -> 0.20;
            case "method" -> 0.15;
            default -> 0.0;
        };
        return new VectorStore.SearchResult(
                result.filePath(),
                result.chunkType(),
                result.name(),
                result.content(),
                result.similarity() + amount + typeBoost
        );
    }

    private String keyOf(VectorStore.SearchResult result) {
        return result.filePath() + "#" + result.chunkType() + "#" + result.name();
    }

    @Override
    public void close() throws SQLException {
        if (ownsStore) {
            vectorStore.close();
        }
    }

    private static final class PerFileLimiter {
        private final int maxPerFile;
        private final Map<String, Integer> counts = new LinkedHashMap<>();

        private PerFileLimiter(int maxPerFile) {
            this.maxPerFile = maxPerFile;
        }

        boolean allow(VectorStore.SearchResult result) {
            int current = counts.getOrDefault(result.filePath(), 0);
            if (current >= maxPerFile) {
                return false;
            }
            counts.put(result.filePath(), current + 1);
            return true;
        }
    }
}
