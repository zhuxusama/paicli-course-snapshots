package ouccs.smy.paiclilearn.memory;

import com.huaban.analysis.jieba.JiebaSegmenter;
import ouccs.smy.paiclilearn.util.JiebaSegmenterFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于 jieba 的检索分词器——将中文查询拆分为有意义的词语。
 *
 * <p>与简单的单字切分不同，jieba 分词能识别中文词组边界：
 * "数据库连接池配置" → ["数据库", "连接", "池", "配置"]，
 * 而非 {'数','据','库','连','接','池','配','置'}。
 * 过滤掉单字符和纯标点，保留长度 ≥2 的有意义词语。</p>
 *
 * <p>英文保留完整单词，按空格和标点自然切分。</p>
 *
 * @since s09
 */
final class MemoryQueryTokenizer {
    private static final JiebaSegmenter SEGMENTER = JiebaSegmenterFactory.createSilently();

    private MemoryQueryTokenizer() {}

    /**
     * 对查询文本进行分词，返回用于检索匹配的 token 集合。
     *
     * @param query 用户查询文本
     * @return 有序去重的 token 集合；查询为空时返回空集合
     */
    static Set<String> tokenize(String query) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return tokens;
        }

        List<String> words = SEGMENTER.sentenceProcess(query.toLowerCase(Locale.ROOT).trim());
        for (String word : words) {
            String trimmed = word.trim();
            // 过滤单字符和纯标点——单个汉字几乎没有检索意义
            if (trimmed.length() >= 2 && !isPunctuation(trimmed)) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    /**
     * 检查文本中是否包含任意一个查询 token（子串匹配）。
     *
     * @param text        待匹配的文本
     * @param queryTokens 查询分词结果
     * @return 至少命中一个 token 时返回 true
     */
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

    /** 判断字符串是否为纯标点符号（非字母数字且非汉字）。 */
    private static boolean isPunctuation(String s) {
        return s.codePoints().allMatch(cp ->
                !Character.isLetterOrDigit(cp)
                        && Character.UnicodeScript.of(cp) != Character.UnicodeScript.HAN);
    }
}
