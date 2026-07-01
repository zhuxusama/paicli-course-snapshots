package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.TokenBudget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 增量回放测试的可读诊断输出：触发原因、压缩计划、覆盖率和事实召回。 */
final class IncrementalCompressionDiagnostics {
    private static final int HEAD_PREVIEW_MESSAGES = 6;
    private static final int TAIL_PREVIEW_MESSAGES = 12;
    private static final int PREVIEW_CHARS = 180;

    private IncrementalCompressionDiagnostics() {}

    static CompressionPlan plan(List<LlmClient.Message> history, int retainRecentRounds) {
        List<LlmClient.Message> tail = CompressionBenchmark.recentTail(history, retainRecentRounds);
        int start = CompressionBenchmark.compressStart(history);
        int end = CompressionBenchmark.compressEnd(history, tail.size());
        List<LlmClient.Message> compressible = List.copyOf(history.subList(start, end));
        return new CompressionPlan(start, end, tail, compressible,
                TokenBudget.estimateMessagesTokens(compressible),
                TokenBudget.estimateMessagesTokens(tail));
    }

    static String checkpoint(int replayMessage, int totalMessages, LlmClient.Message incoming,
                             int currentTokens, int thresholdTokens, boolean triggered) {
        return """
                === REPLAY CHECKPOINT ===
                readMessages=%d/%d
                incomingRole=%s
                currentTokens=%d
                thresholdTokens=%d
                triggered=%s
                triggerReason=%s
                """.formatted(replayMessage, totalMessages, incoming.role(), currentTokens,
                thresholdTokens, triggered,
                triggered
                        ? "currentTokens " + currentTokens + " >= thresholdTokens " + thresholdTokens
                        : "currentTokens " + currentTokens + " < thresholdTokens " + thresholdTokens);
    }

    static String planLog(int index, CompressionPlan plan, List<LlmClient.Message> history,
                          int retainRecentRounds) {
        int totalTokens = TokenBudget.estimateMessagesTokens(history);
        double compressibleRatio = totalTokens == 0 ? 0.0 : plan.compressibleTokens() / (double) totalTokens;
        return """
                === COMPRESSION PLAN #%d ===
                retainRecentRounds=%d
                compressRange=[%d,%d)
                tailRange=[%d,%d)
                compressibleMessages=%d
                tailMessages=%d
                compressibleTokens=%d
                tailTokens=%d
                compressibleTokenRatio=%.2f%%
                roles.all=%s
                roles.compressible=%s
                roles.tail=%s
                toolProtocolBefore=%s
                planNote=%s
                """.formatted(index, retainRecentRounds, plan.compressStart(), plan.compressEnd(),
                plan.compressEnd(), history.size(), plan.compressibleMessages().size(), plan.tail().size(),
                plan.compressibleTokens(), plan.tailTokens(), compressibleRatio * 100.0,
                roleCounts(history), roleCounts(plan.compressibleMessages()), roleCounts(plan.tail()),
                CompressionBenchmark.toolProtocolValid(history), planNote(compressibleRatio));
    }

    static String resultLog(CompressionEvent event) {
        return """
                === COMPRESSION RESULT #%d ===
                beforeTokens=%d
                afterTokens=%d
                tokenReductionPercent=%.2f
                effect=%s
                beforeMessages=%d
                afterMessages=%d
                summaryPresent=%s
                summaryChars=%d
                recentTailPreserved=%s
                toolProtocolAfter=%s
                modelInputTokens=%d
                modelOutputTokens=%d
                modelCachedInputTokens=%d
                summaryPreview=%s
                """.formatted(event.index(), event.beforeTokens(), event.afterTokens(),
                event.reductionRatio() * 100.0, event.effect(), event.beforeMessages(),
                event.afterMessages(), event.summaryPresent(), event.summaryChars(),
                event.recentTailPreserved(), event.toolProtocolValid(), event.modelInputTokens(),
                event.modelOutputTokens(), event.modelCachedInputTokens(), event.summaryPreview());
    }

    static String coverageLog(int index, CompressionPlan plan, String requestText,
                              String summary, List<LlmClient.Message> finalHistory) {
        int requestChars = requestText == null ? 0 : requestText.length();
        int compressibleChars = plan.compressibleMessages().stream()
                .map(CompressionBenchmark::semanticText).mapToInt(String::length).sum();
        double inputCoverage = compressibleChars == 0 ? 1.0
                : Math.min(1.0, requestChars / (double) compressibleChars);
        Map<String, Double> roleCoverage = CompressionBenchmark.roleCoverage(
                join(plan.compressibleMessages(), plan.tail()), plan.tail().size(), requestText == null ? "" : requestText);
        String sourceBeforeCompression = join(plan.compressibleMessages(), plan.tail()).stream()
                .map(CompressionBenchmark::semanticText)
                .reduce("", (left, right) -> left + "\n" + right);
        String finalContext = finalHistory.stream().map(CompressionBenchmark::semanticText)
                .reduce("", (left, right) -> left + "\n" + right);
        List<FactEvaluation> facts = CriticalFactCatalog.evaluateKnown(
                sourceBeforeCompression, summary, finalContext);
        return """
                === HISTORY COVERAGE #%d ===
                summaryInputChars=%d
                compressibleChars=%d
                summaryInputCoveragePercent=%.2f
                roleCoverage=%s
                criticalFactRecall=%s
                coverageVerdict=%s
                factDetails=%s
                """.formatted(index, requestChars, compressibleChars, inputCoverage * 100.0,
                roleCoverage, CriticalFactCatalog.summary(facts),
                coverageVerdict(inputCoverage, CriticalFactCatalog.recall(facts)), factDetails(facts));
    }

    static String historyPreview(List<LlmClient.Message> history) {
        StringBuilder out = new StringBuilder();
        int size = history.size();
        for (int i = 0; i < size; i++) {
            if (i >= HEAD_PREVIEW_MESSAGES && i < size - TAIL_PREVIEW_MESSAGES) {
                if (i == HEAD_PREVIEW_MESSAGES) {
                    out.append("... omitted ").append(size - HEAD_PREVIEW_MESSAGES - TAIL_PREVIEW_MESSAGES)
                            .append(" middle messages ...\n");
                }
                continue;
            }
            LlmClient.Message message = history.get(i);
            out.append('[').append(i).append("] role=").append(message.role())
                    .append(" chars=").append(CompressionBenchmark.semanticText(message).length())
                    .append(" toolCallId=").append(message.toolCallId())
                    .append(" toolCalls=").append(message.toolCalls() == null ? 0 : message.toolCalls().size())
                    .append(" preview=").append(preview(CompressionBenchmark.semanticText(message)))
                    .append('\n');
        }
        return out.toString();
    }

    static String preview(String text) {
        String safe = CompressionBenchmarkReport.redact(text == null ? "" : text)
                .replaceAll("\\s+", " ").trim();
        return safe.length() <= PREVIEW_CHARS ? safe : safe.substring(0, PREVIEW_CHARS) + "...";
    }

    static Map<String, Integer> roleCounts(List<LlmClient.Message> history) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LlmClient.Message message : history) counts.merge(message.role(), 1, Integer::sum);
        return counts;
    }

    private static List<LlmClient.Message> join(List<LlmClient.Message> compressible,
                                                List<LlmClient.Message> tail) {
        java.util.ArrayList<LlmClient.Message> result = new java.util.ArrayList<>(compressible);
        result.addAll(tail);
        return List.copyOf(result);
    }

    private static String planNote(double compressibleRatio) {
        if (compressibleRatio >= 0.60) return "compressible area is large; expected reduction should be strong";
        if (compressibleRatio >= 0.30) return "compressible area is medium; reduction depends on summary length";
        return "compressible area is small; weak compression is expected; consider minCompressibleTokens later";
    }

    private static String coverageVerdict(double inputCoverage, double factRecall) {
        if (inputCoverage >= 0.60 && factRecall >= 0.80) return "PASS";
        if (factRecall >= 0.80) return "WARN_LOW_INPUT_COVERAGE";
        return "FAIL_FACT_RECALL";
    }

    private static String factDetails(List<FactEvaluation> facts) {
        return facts.stream()
                .map(fact -> fact.id() + "=" + fact.location())
                .reduce("", (left, right) -> left.isEmpty() ? right : left + ", " + right);
    }

    record CompressionPlan(int compressStart, int compressEnd, List<LlmClient.Message> tail,
                           List<LlmClient.Message> compressibleMessages,
                           int compressibleTokens, int tailTokens) {}

    record CompressionEvent(
            int index,
            int replayMessageIndex,
            int beforeMessages,
            int afterMessages,
            int beforeTokens,
            int afterTokens,
            boolean recentTailPreserved,
            boolean toolProtocolValid,
            boolean summaryPresent,
            int summaryChars,
            int modelInputTokens,
            int modelOutputTokens,
            int modelCachedInputTokens,
            String summaryPreview) {
        double reductionRatio() {
            return beforeTokens == 0 ? 0.0 : (beforeTokens - afterTokens) / (double) beforeTokens;
        }

        String effect() {
            double ratio = reductionRatio();
            if (ratio >= 0.60) return "GOOD";
            if (ratio >= 0.20) return "OK";
            return "WEAK";
        }

        String toSummaryLine() {
            return String.format(Locale.ROOT,
                    "compression #%d at replayMessage=%d: messages=%d->%d tokens=%d->%d reduction=%.2f%% effect=%s tailPreserved=%s toolProtocolValid=%s summaryPresent=%s modelTokens(in=%d,out=%d,cache=%d) summaryPreview=%s",
                    index, replayMessageIndex, beforeMessages, afterMessages, beforeTokens,
                    afterTokens, reductionRatio() * 100.0, effect(), recentTailPreserved,
                    toolProtocolValid, summaryPresent, modelInputTokens, modelOutputTokens,
                    modelCachedInputTokens, summaryPreview);
        }
    }
}
