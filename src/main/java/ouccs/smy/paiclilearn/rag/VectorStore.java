package ouccs.smy.paiclilearn.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * s16: SQLite 向量库，把上一章的代码块和关系图变成可复用索引。
 * <p>
 * 原项目在课程阶段使用 SQLite 存储 embedding JSON，再在检索时做内存余弦排序。
 * 这不是玩具内存 Map：索引会落盘到 `~/.paicli/rag/codebase.db`，测试通过系统属性隔离目录。
 */
public class VectorStore implements AutoCloseable {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Connection connection;
    private final String projectPath;

    public VectorStore(String projectPath) throws SQLException {
        this.projectPath = projectPath;
        String dbDir = System.getProperty("paicli.rag.dir",
                System.getProperty("user.home") + "/.paicli/rag");
        java.io.File dir = new java.io.File(dbDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new SQLException("无法创建 RAG 数据目录: " + dbDir);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dir.getAbsolutePath() + "/codebase.db");
        initTables();
    }

    private void initTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS code_chunks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        project_path TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        chunk_type TEXT NOT NULL,
                        name TEXT NOT NULL,
                        content TEXT NOT NULL,
                        embedding_json TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS code_relations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        project_path TEXT NOT NULL,
                        from_file TEXT NOT NULL,
                        from_name TEXT NOT NULL,
                        to_file TEXT,
                        to_name TEXT,
                        relation_type TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chunks_project ON code_chunks(project_path)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chunks_file ON code_chunks(file_path)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chunks_type ON code_chunks(chunk_type)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_rel_project ON code_relations(project_path)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_rel_from ON code_relations(from_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_rel_to ON code_relations(to_name)");
        }
    }

    public void clearProject() throws SQLException {
        try (PreparedStatement chunks = connection.prepareStatement("DELETE FROM code_chunks WHERE project_path = ?");
             PreparedStatement relations = connection.prepareStatement("DELETE FROM code_relations WHERE project_path = ?")) {
            chunks.setString(1, projectPath);
            relations.setString(1, projectPath);
            chunks.executeUpdate();
            relations.executeUpdate();
        }
    }

    public void insertChunks(List<CodeChunkEntry> entries) throws SQLException {
        String sql = """
                INSERT INTO code_chunks (project_path, file_path, chunk_type, name, content, embedding_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CodeChunkEntry entry : entries) {
                statement.setString(1, projectPath);
                statement.setString(2, entry.chunk().filePath());
                statement.setString(3, entry.chunk().chunkType());
                statement.setString(4, entry.chunk().name());
                statement.setString(5, entry.chunk().content());
                statement.setString(6, embeddingToJson(entry.embedding()));
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public void insertRelations(List<CodeRelation> relations) throws SQLException {
        String sql = """
                INSERT INTO code_relations (project_path, from_file, from_name, to_file, to_name, relation_type)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CodeRelation relation : relations) {
                statement.setString(1, projectPath);
                statement.setString(2, relation.fromFile());
                statement.setString(3, relation.fromName());
                statement.setString(4, relation.toFile());
                statement.setString(5, relation.toName());
                statement.setString(6, relation.relationType());
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public List<SearchResult> search(float[] queryEmbedding, int topK) throws SQLException {
        String sql = """
                SELECT file_path, chunk_type, name, content, embedding_json
                FROM code_chunks
                WHERE project_path = ?
                """;
        List<SearchResult> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectPath);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String embeddingJson = rs.getString("embedding_json");
                    if (embeddingJson == null || embeddingJson.isBlank()) {
                        continue;
                    }
                    double similarity = cosineSimilarity(queryEmbedding, jsonToEmbedding(embeddingJson));
                    candidates.add(new SearchResult(
                            rs.getString("file_path"),
                            rs.getString("chunk_type"),
                            rs.getString("name"),
                            rs.getString("content"),
                            similarity
                    ));
                }
            }
        }
        candidates.sort((left, right) -> Double.compare(right.similarity(), left.similarity()));
        return candidates.size() > topK ? new ArrayList<>(candidates.subList(0, topK)) : candidates;
    }

    public List<SearchResult> searchByKeyword(String keyword) throws SQLException {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        String sql = """
                SELECT file_path, chunk_type, name, content
                FROM code_chunks
                WHERE project_path = ? AND (name LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\')
                """;
        List<SearchResult> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectPath);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                            rs.getString("file_path"),
                            rs.getString("chunk_type"),
                            rs.getString("name"),
                            rs.getString("content"),
                            0.3
                    ));
                }
            }
        }
        return results;
    }

    public List<CodeRelation> getRelations(String name) throws SQLException {
        String sql = """
                SELECT from_file, from_name, to_file, to_name, relation_type
                FROM code_relations
                WHERE project_path = ? AND (from_name = ? OR to_name = ?)
                """;
        List<CodeRelation> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectPath);
            statement.setString(2, name);
            statement.setString(3, name);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(readRelation(rs));
                }
            }
        }
        return results;
    }

    public List<CodeRelation> getOutgoingRelations(String name) throws SQLException {
        String sql = """
                SELECT from_file, from_name, to_file, to_name, relation_type
                FROM code_relations
                WHERE project_path = ? AND from_name = ?
                """;
        List<CodeRelation> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectPath);
            statement.setString(2, name);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(readRelation(rs));
                }
            }
        }
        return results;
    }

    public IndexStats getStats() throws SQLException {
        return new IndexStats(countRows("code_chunks"), countRows("code_relations"));
    }

    private int countRows(String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE project_path = ?")) {
            statement.setString(1, projectPath);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private CodeRelation readRelation(ResultSet rs) throws SQLException {
        return new CodeRelation(
                rs.getString("from_file"),
                rs.getString("from_name"),
                rs.getString("to_file"),
                rs.getString("to_name"),
                rs.getString("relation_type")
        );
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length || left.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normLeft = 0.0;
        double normRight = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            normLeft += left[i] * left[i];
            normRight += right[i] * right[i];
        }
        if (normLeft == 0.0 || normRight == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }

    private String embeddingToJson(float[] embedding) {
        try {
            return mapper.writeValueAsString(embedding);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("向量序列化失败", e);
        }
    }

    private float[] jsonToEmbedding(String json) {
        try {
            return mapper.readValue(json, float[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("向量反序列化失败", e);
        }
    }

    @Override
    public void close() throws SQLException {
        if (!connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * 带 embedding 的代码块条目，索引器把它批量写入 `code_chunks`。
     *
     * @param chunk 原始代码块
     * @param embedding 该代码块的向量表示
     */
    public record CodeChunkEntry(CodeChunk chunk, float[] embedding) {
    }

    /**
     * 检索返回的代码块视图，包含排序分数。
     *
     * @param filePath 文件路径
     * @param chunkType chunk 类型
     * @param name 类名、方法名或文件名
     * @param content 代码片段正文
     * @param similarity 与查询的相似度或混合分
     */
    public record SearchResult(String filePath, String chunkType,
                               String name, String content, double similarity) {
    }

    /**
     * 当前项目索引规模统计。
     *
     * @param chunkCount 已持久化代码块数量
     * @param relationCount 已持久化关系数量
     */
    public record IndexStats(int chunkCount, int relationCount) {
    }
}
