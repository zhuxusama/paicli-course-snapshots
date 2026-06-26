package ouccs.smy.paiclilearn.memory;

import com.huaban.analysis.jieba.JiebaSegmenter;
import ouccs.smy.paiclilearn.util.JiebaSegmenterFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * s08: 使用 jieba 做中文分词，避免把中文查询退化成逐字匹配。
 * <p>
 * 本章已接入真实 jieba 分词器（通过 JiebaSegmenterFactory），为 MemoryRetriever、
 * ConversationMemory.search() 和 LongTermMemory.search() 提供关键词切分。
 * s09 可能在此基础上增加自定义词典和领域停用词过滤。
 * </p>
 */
final class MemoryQueryTokenizer {
    private static final JiebaSegmenter SEGMENTER = JiebaSegmenterFactory.createSilently();

    private MemoryQueryTokenizer() {
    }

    static Set<String> tokenize(String query) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return tokens;
        }

        List<String> words = SEGMENTER.sentenceProcess(query.toLowerCase(Locale.ROOT).trim());
        for (String word : words) {
            String trimmed = word.trim();
            if (trimmed.length() >= 2 && !isPunctuation(trimmed)) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    static boolean matches(String text, Set<String> queryTokens) {
        if (text == null || text.isBlank() || queryTokens.isEmpty()) {
            return false;
        }

        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String token : queryTokens) {
            if (normalizedText.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPunctuation(String s) {
        return s.codePoints().allMatch(cp ->
                !Character.isLetterOrDigit(cp) && Character.UnicodeScript.of(cp) != Character.UnicodeScript.HAN);
    }
}
