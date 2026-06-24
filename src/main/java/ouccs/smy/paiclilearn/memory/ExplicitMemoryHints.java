package ouccs.smy.paiclilearn.memory;

import java.util.List;

/** 只识别用户明确表达的保存意图，不从普通聊天自动抽取事实。 */
/** [s08 新增] */
public final class ExplicitMemoryHints {
    private static final List<String> MARKERS = List.of(
            "请记住", "记住", "记一下", "记下来", "以后记得", "下次记得", "保存到长期记忆");

    private ExplicitMemoryHints() {}

    public static String extractFact(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        for (String marker : MARKERS) {
            int index = trimmed.indexOf(marker);
            if (index >= 0) {
                String fact = trimmed.substring(index + marker.length()).trim()
                        .replaceFirst("^[：:，,。\\s]+", "")
                        .replaceFirst("[。\\s]+$", "");
                return fact.isBlank() ? null : fact;
            }
        }
        return null;
    }
}
