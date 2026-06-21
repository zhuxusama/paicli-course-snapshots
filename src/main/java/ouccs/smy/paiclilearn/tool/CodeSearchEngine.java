package ouccs.smy.paiclilearn.tool;

import java.nio.file.Path;
import java.util.List;

/**
 * 代码搜索引擎边界（策略模式）。
 * <p>
 * 两种实现：{@link RipgrepCodeSearchEngine}（优先使用 rg JSON 输出）
 * 和 {@link JavaCodeSearchEngine}（纯 Java 回退）。</p>
 *
 * @since s04
 */
interface CodeSearchEngine {
    CodeSearchResult search(CodeSearchRequest request);
}

/** grep 搜索请求，包含所有过滤和预算参数。 */
record CodeSearchRequest(
        String query,
        Path root,
        Path projectRoot,
        String glob,
        boolean regex,
        boolean caseSensitive,
        int contextLines,
        int maxResults,
        int headLimit
) {}

/** grep 搜索结果，包含匹配列表和截断状态。 */
record CodeSearchResult(
        String engine,
        List<GrepMatch> matches,
        boolean partial,
        String partialReason
) {}

/** 单行上下文（行号 + 文本）。 */
record ContextLine(int lineNumber, String text) {}

/** 单个 grep 匹配项（文件路径、行号、上下文行）。 */
record GrepMatch(String file, int lineNumber, List<ContextLine> context) {}
