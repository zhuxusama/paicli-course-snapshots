package ouccs.smy.paiclilearn.rag;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.paicli.util.JiebaSegmenterFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * s16: 查询分词器把自然语言问题拆成可做关键词补召回的 token。
 * <p>
 * 原项目会把语义检索和关键词检索混合：embedding 负责“意思相近”，token 负责
 * 类名、方法名、路径片段这类必须精确命中的信号。
 */
final class RagQueryTokenizer {
    private static final JiebaSegmenter SEGMENTER = JiebaSegmenterFactory.createSilently();
    private static final Pattern ASCII_TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{1,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "where", "what", "which", "how", "the", "and", "with", "code",
            "find", "show", "is", "are", "在", "哪里", "什么", "如何", "怎么", "代码"
    );

    private RagQueryTokenizer() {
    }

    static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();

        Matcher matcher = ASCII_TOKEN.matcher(query);
        while (matcher.find()) {
            addToken(tokens, matcher.group());
            splitCamelCase(tokens, matcher.group());
        }

        for (SegToken token : SEGMENTER.process(query, JiebaSegmenter.SegMode.SEARCH)) {
            addToken(tokens, token.word);
        }

        return new ArrayList<>(tokens);
    }

    private static void splitCamelCase(Set<String> tokens, String value) {
        String[] parts = value.replace('_', ' ').split("(?<=[a-z])(?=[A-Z])|\\s+");
        for (String part : parts) {
            addToken(tokens, part);
        }
    }

    private static void addToken(Set<String> tokens, String token) {
        if (token == null) {
            return;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2 || STOP_WORDS.contains(normalized)) {
            return;
        }
        tokens.add(normalized);
    }
}
