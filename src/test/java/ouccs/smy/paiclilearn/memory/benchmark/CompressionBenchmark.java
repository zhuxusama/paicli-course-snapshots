package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.ConversationHistoryCompactor;
import ouccs.smy.paiclilearn.memory.TokenBudget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 通过生产 ConversationHistoryCompactor 执行真实会话压缩并评估结果。 */
final class CompressionBenchmark {
    private static final int RETAIN_RECENT_ROUNDS = 2;

    private CompressionBenchmark() {}

    static CompressionBenchmarkResult run(LoadedRollout rollout, LlmClient summarizer, String mode)
            throws IOException {
        List<LlmClient.Message> history = rollout.messages().stream()
                .map(TracedMessage::message).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<LlmClient.Message> before = List.copyOf(history);
        List<LlmClient.Message> expectedTail = recentTail(before, RETAIN_RECENT_ROUNDS);
        int beforeTokens = TokenBudget.estimateMessagesTokens(before);
        int compressibleCharacters = compressibleCharacters(before, expectedTail.size());

        RecordingClient recording = new RecordingClient(summarizer);
        ConversationHistoryCompactor compactor = new ConversationHistoryCompactor(
                recording, RETAIN_RECENT_ROUNDS);
        long started = System.nanoTime();
        boolean compacted = compactor.compactIfNeeded(history, beforeTokens);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        int afterTokens = TokenBudget.estimateMessagesTokens(history);
        double reduction = beforeTokens == 0 ? 0.0
                : (beforeTokens - afterTokens) / (double) beforeTokens;
        double coverage = compressibleCharacters == 0 ? 1.0
                : Math.min(1.0, recording.requestCharacters / (double) compressibleCharacters);
        Map<String, Double> roleCoverage = roleCoverage(before, expectedTail.size(), recording.requestText);
        boolean systemPreserved = !before.isEmpty() && !history.isEmpty()
                && before.get(0).equals(history.get(0));
        boolean recentTailPreserved = endsWith(history, expectedTail);
        boolean protocolValid = toolProtocolValid(history);
        String summary = findSummary(history);
        List<FactEvaluation> facts = CriticalFactCatalog.evaluate(summary, expectedTail);
        double factRecall = CriticalFactCatalog.recall(facts);

        List<GateEvaluation> gates = new ArrayList<>();
        gates.add(gate("baseline_event_count", !rollout.baselineFixture()
                        || rollout.stats().totalEvents() == 780,
                "baseline=780", String.valueOf(rollout.stats().totalEvents())));
        gates.add(gate("json_parse", rollout.stats().parseErrors() == 0,
                "0 parse errors", String.valueOf(rollout.stats().parseErrors())));
        gates.add(gate("tool_pairing", rollout.pairingIssues().isEmpty(),
                "0 pairing issues", String.valueOf(rollout.pairingIssues().size())));
        gates.add(gate("compacted", compacted, "true", String.valueOf(compacted)));
        gates.add(gate("token_reduction", reduction >= 0.60,
                ">=60%", String.format(Locale.ROOT, "%.2f%%", reduction * 100)));
        gates.add(gate("system_preserved", systemPreserved, "true", String.valueOf(systemPreserved)));
        gates.add(gate("recent_two_rounds_preserved", recentTailPreserved,
                "true", String.valueOf(recentTailPreserved)));
        gates.add(gate("tool_protocol_valid", protocolValid, "true", String.valueOf(protocolValid)));
        gates.add(gate("critical_fact_recall", factRecall >= 0.80,
                ">=80%", String.format(Locale.ROOT, "%.2f%%", factRecall * 100)));

        BenchmarkReportSnapshot report = new BenchmarkReportSnapshot(
                rollout.fixture(), rollout.baselineFixture(), rollout.stats(), rollout.messages(),
                rollout.pairingIssues(), before.size() - expectedTail.size() - 1, expectedTail.size(),
                roleCoverage, beforeTokens, afterTokens, reduction, coverage, elapsedMillis,
                summary, facts, List.copyOf(gates), mode, recording.getProviderName(),
                recording.getModelName(), recording.inputTokens, recording.outputTokens,
                recording.cachedInputTokens);
        return new CompressionBenchmarkResult(report);
    }

    private static GateEvaluation gate(String name, boolean passed, String expected, String actual) {
        return new GateEvaluation(name, passed, expected, actual);
    }

    static List<LlmClient.Message> recentTail(List<LlmClient.Message> history, int rounds) {
        int users = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) users++;
            if (users >= rounds) return List.copyOf(history.subList(i, history.size()));
        }
        return List.copyOf(history);
    }

    static int compressStart(List<LlmClient.Message> history) {
        return !history.isEmpty() && "system".equals(history.get(0).role()) ? 1 : 0;
    }

    static int compressEnd(List<LlmClient.Message> history, int retainedTailSize) {
        return Math.max(compressStart(history), history.size() - retainedTailSize);
    }

    static String semanticText(LlmClient.Message message) {
        StringBuilder text = new StringBuilder();
        if (message.content() != null) text.append(message.content());
        if (message.toolCalls() != null) {
            for (LlmClient.ToolCall call : message.toolCalls()) {
                text.append('\n').append(call.function().name()).append(' ')
                        .append(call.function().arguments());
            }
        }
        if (message.toolCallId() != null) text.append('\n').append(message.toolCallId());
        return text.toString();
    }

    static int tokenCount(List<LlmClient.Message> messages) {
        return TokenBudget.estimateMessagesTokens(messages);
    }

    static boolean endsWith(List<LlmClient.Message> actual, List<LlmClient.Message> suffix) {
        if (suffix.size() > actual.size()) return false;
        return actual.subList(actual.size() - suffix.size(), actual.size()).equals(suffix);
    }

    static boolean toolProtocolValid(List<LlmClient.Message> history) {
        Set<String> calls = new LinkedHashSet<>();
        Set<String> outputs = new LinkedHashSet<>();
        for (LlmClient.Message message : history) {
            if (message.toolCalls() != null) {
                for (LlmClient.ToolCall call : message.toolCalls()) {
                    if (!calls.add(call.id())) return false;
                }
            }
            if ("tool".equals(message.role())) {
                if (message.toolCallId() == null || !outputs.add(message.toolCallId())) return false;
            }
        }
        return calls.equals(outputs);
    }

    static String findSummary(List<LlmClient.Message> history) {
        for (LlmClient.Message message : history) {
            if (!"user".equals(message.role()) || message.content() == null) continue;
            String content = message.content();
            if (content.startsWith("[已压缩]")) return content.substring("[已压缩]".length()).trim();
            if (content.startsWith("[宸插帇缂")) {
                int close = content.indexOf(']');
                return close >= 0 ? content.substring(close + 1).trim() : content;
            }
        }
        return "";
    }

    static Map<String, Double> roleCoverage(List<LlmClient.Message> history, int tailSize,
                                            String requestText) {
        int start = compressStart(history);
        int end = compressEnd(history, tailSize);
        Map<String, Integer> available = new LinkedHashMap<>();
        for (int i = start; i < end; i++) {
            LlmClient.Message message = history.get(i);
            available.merge(message.role(), semanticText(message).length(), Integer::sum);
        }
        Map<String, Integer> sampled = new LinkedHashMap<>();
        for (String line : requestText.split("\\R")) {
            if (!line.startsWith("[") || !line.contains("] ")) continue;
            int close = line.indexOf("] ");
            String role = line.substring(1, close).toLowerCase(Locale.ROOT);
            sampled.merge(role, line.length() - close - 2, Integer::sum);
        }
        Map<String, Double> result = new LinkedHashMap<>();
        available.forEach((role, chars) -> result.put(role,
                chars == 0 ? 1.0 : Math.min(1.0, sampled.getOrDefault(role, 0) / (double) chars)));
        return Map.copyOf(result);
    }

    private static int compressibleCharacters(List<LlmClient.Message> history, int tailSize) {
        int start = compressStart(history);
        int end = compressEnd(history, tailSize);
        int chars = 0;
        for (int i = start; i < end; i++) chars += semanticText(history.get(i)).length();
        return chars;
    }

    static final class RecordingClient implements LlmClient {
        private final LlmClient delegate;
        private int requestCharacters;
        private String requestText = "";
        private int inputTokens;
        private int outputTokens;
        private int cachedInputTokens;

        RecordingClient(LlmClient delegate) { this.delegate = delegate; }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener)
                throws IOException {
            requestCharacters = messages == null ? 0 : messages.stream()
                    .map(Message::content).filter(value -> value != null).mapToInt(String::length).sum();
            requestText = messages == null ? "" : messages.stream().map(Message::content)
                    .filter(value -> value != null).reduce("", (left, right) -> left + "\n" + right);
            ChatResponse response = delegate.chat(messages, tools, listener);
            if (response != null) {
                inputTokens = response.inputTokens();
                outputTokens = response.outputTokens();
                cachedInputTokens = response.cachedInputTokens();
            }
            return response;
        }

        @Override public String getModelName() { return delegate.getModelName(); }
        @Override public String getProviderName() { return delegate.getProviderName(); }
        @Override public int maxContextWindow() { return delegate.maxContextWindow(); }
        @Override public boolean supportsPromptCaching() { return delegate.supportsPromptCaching(); }
        @Override public String promptCacheMode() { return delegate.promptCacheMode(); }
        int requestCharacters() { return requestCharacters; }
        String requestText() { return requestText; }
        int inputTokens() { return inputTokens; }
        int outputTokens() { return outputTokens; }
        int cachedInputTokens() { return cachedInputTokens; }
    }
}

record CompressionBenchmarkResult(BenchmarkReportSnapshot report) {
    boolean allGatesPassed() {
        return report.gates().stream().allMatch(GateEvaluation::passed);
    }

    String failedGateSummary() {
        return report.gates().stream().filter(gate -> !gate.passed())
                .map(gate -> gate.name() + " expected=" + gate.expected() + " actual=" + gate.actual())
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "; " + right);
    }
}
