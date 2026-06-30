package ouccs.smy.paiclilearn.rag;

import java.util.List;
import java.util.Locale;

/**
 * s16: 检索结果格式化器把排序后的代码块整理成人能读的终端文本。
 * <p>
 * RAG 结果如果直接打印对象，学习者看不到“为什么命中”。这里保留文件、符号、
 * 相似度和片段，让后续 CLI 或工具输出都能复用同一种格式。
 */
public final class SearchResultFormatter {
    private SearchResultFormatter() {
    }

    public static String formatForCli(String query, List<VectorStore.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "未找到与查询相关的代码: " + query;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("查询: ").append(query).append(System.lineSeparator());
        builder.append(buildSummary(results)).append(System.lineSeparator());
        for (int i = 0; i < results.size(); i++) {
            VectorStore.SearchResult result = results.get(i);
            builder.append(System.lineSeparator())
                    .append(i + 1).append(". ")
                    .append(shortenPath(result.filePath()))
                    .append(" :: ").append(result.name())
                    .append(" [").append(result.chunkType()).append("]")
                    .append(" score=").append(String.format(Locale.ROOT, "%.3f", result.similarity()))
                    .append(System.lineSeparator())
                    .append(buildSnippet(result.content()));
        }
        return builder.toString();
    }

    public static String formatForTool(String query, List<VectorStore.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "{\"query\":\"" + escape(query) + "\",\"results\":[]}";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{\"query\":\"").append(escape(query)).append("\",\"results\":[");
        for (int i = 0; i < results.size(); i++) {
            VectorStore.SearchResult result = results.get(i);
            if (i > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"file\":\"").append(escape(result.filePath())).append("\",")
                    .append("\"type\":\"").append(escape(result.chunkType())).append("\",")
                    .append("\"name\":\"").append(escape(result.name())).append("\",")
                    .append("\"similarity\":").append(String.format(Locale.ROOT, "%.4f", result.similarity())).append(",")
                    .append("\"snippet\":\"").append(escape(buildSnippet(result.content()))).append("\"")
                    .append("}");
        }
        builder.append("]}");
        return builder.toString();
    }

    static String buildSummary(List<VectorStore.SearchResult> results) {
        long files = results.stream().map(VectorStore.SearchResult::filePath).distinct().count();
        return "命中 " + results.size() + " 个代码块，分布在 " + files + " 个文件。";
    }

    static String buildSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "  <empty>";
        }
        String compact = content.strip().replace("\r\n", "\n");
        String[] lines = compact.split("\n");
        int limit = Math.min(lines.length, 4);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            builder.append("  ").append(lines[i].strip()).append(System.lineSeparator());
        }
        if (lines.length > limit) {
            builder.append("  ...").append(System.lineSeparator());
        }
        return builder.toString().stripTrailing();
    }

    static String shortenPath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int src = normalized.indexOf("src/");
        return src >= 0 ? normalized.substring(src) : normalized;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
