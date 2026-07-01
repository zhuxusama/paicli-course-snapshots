package ouccs.smy.paiclilearn.memory.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 从 JSON 读取真实 rollout 的 gold facts，并评估压缩后上下文是否仍覆盖这些事实。 */
final class CriticalFactCatalog {
    private static final String RESOURCE = "real-rollout-critical-facts.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CriticalFactCatalog() {}

    static List<FactSpec> load() {
        try (InputStream input = CriticalFactCatalog.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("未找到关键事实资源: " + RESOURCE);
            }
            return MAPPER.readValue(input, new TypeReference<List<FactSpec>>() {});
        } catch (IOException error) {
            throw new IllegalStateException("读取关键事实失败: " + RESOURCE, error);
        }
    }

    static List<FactEvaluation> evaluate(String summary, List<LlmClient.Message> tail) {
        return evaluate(summary, semanticText(tail));
    }

    static List<FactEvaluation> evaluate(String summary, String retainedContextText) {
        List<FactEvaluation> result = new ArrayList<>();
        for (FactSpec spec : load()) {
            boolean inSummary = matches(summary, spec.alternatives());
            boolean inTail = matches(retainedContextText, spec.alternatives());
            result.add(new FactEvaluation(spec.id(), spec.expected(), inSummary || inTail,
                    inSummary ? "summary" : inTail ? "recent-tail" : "missing"));
        }
        return List.copyOf(result);
    }

    static List<FactEvaluation> evaluateKnown(String sourceBeforeCompression,
                                              String summary, String retainedContextText) {
        List<FactEvaluation> result = new ArrayList<>();
        for (FactSpec spec : load()) {
            if (!matches(sourceBeforeCompression, spec.alternatives())) continue;
            boolean inSummary = matches(summary, spec.alternatives());
            boolean inTail = matches(retainedContextText, spec.alternatives());
            result.add(new FactEvaluation(spec.id(), spec.expected(), inSummary || inTail,
                    inSummary ? "summary" : inTail ? "recent-tail" : "missing"));
        }
        return List.copyOf(result);
    }

    static double recall(List<FactEvaluation> facts) {
        if (facts.isEmpty()) return 1.0;
        return facts.stream().filter(FactEvaluation::matched).count() / (double) facts.size();
    }

    static String summary(List<FactEvaluation> facts) {
        long matched = facts.stream().filter(FactEvaluation::matched).count();
        return matched + "/" + facts.size() + " (" + String.format(Locale.ROOT, "%.2f%%", recall(facts) * 100) + ")";
    }

    private static boolean matches(String text, List<List<String>> alternatives) {
        String normalized = normalize(text);
        for (List<String> terms : alternatives) {
            if (terms.stream().map(CriticalFactCatalog::normalize).allMatch(normalized::contains)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String semanticText(List<LlmClient.Message> messages) {
        StringBuilder text = new StringBuilder();
        for (LlmClient.Message message : messages) {
            if (message.content() != null) text.append(message.content()).append('\n');
            if (message.toolCalls() != null) {
                for (LlmClient.ToolCall call : message.toolCalls()) {
                    text.append(call.function().name()).append(' ')
                            .append(call.function().arguments()).append('\n');
                }
            }
            if (message.toolCallId() != null) text.append(message.toolCallId()).append('\n');
        }
        return text.toString();
    }

    record FactSpec(String id, String type, String severity, String expected,
                    String sourceHint, List<List<String>> alternatives) {}
}
