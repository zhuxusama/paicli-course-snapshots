package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.ConversationHistoryCompactor;
import ouccs.smy.paiclilearn.memory.TokenBudget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 回放真实 rollout，模拟运行时 conversationHistory 逐步增长，到阈值后才压缩。 */
class RolloutIncrementalCompressionSimulationTest {
    private static final int SIMULATED_CONTEXT_THRESHOLD = 32_000;
    private static final int RETAIN_RECENT_ROUNDS = 2;

    @Test
    void replaysRealRolloutAndCompactsWhenSimulatedContextThresholdIsReached()
            throws Exception {
        var rollout = new CodexRolloutLoader().load(CodexRolloutLoader.locateFixtureOrSkip());
        List<LlmClient.Message> history = new ArrayList<>();
        var recording = new CompressionBenchmark.RecordingClient(new DeterministicSummaryLlmClient());
        ConversationHistoryCompactor compactor =
                new ConversationHistoryCompactor(recording, RETAIN_RECENT_ROUNDS);
        List<IncrementalCompressionDiagnostics.CompressionEvent> events = new ArrayList<>();
        StringBuilder log = new StringBuilder();

        append(log, "=== s09 INCREMENTAL ROLLOUT COMPRESSION SIMULATION ===");
        append(log, "fixture=" + rollout.fixture().toAbsolutePath().normalize());
        append(log, "mode=offline");
        append(log, "thresholdTokens=" + SIMULATED_CONTEXT_THRESHOLD);
        append(log, "retainRecentRounds=" + RETAIN_RECENT_ROUNDS);
        append(log, "totalReplayMessages=" + rollout.messages().size());
        append(log, "");

        for (int i = 0; i < rollout.messages().size(); i++) {
            LlmClient.Message incoming = rollout.messages().get(i).message();
            history.add(incoming);
            if (!"user".equals(incoming.role())) continue;

            int beforeTokens = TokenBudget.estimateMessagesTokens(history);
            boolean triggered = beforeTokens >= SIMULATED_CONTEXT_THRESHOLD;
            append(log, IncrementalCompressionDiagnostics.checkpoint(
                    i + 1, rollout.messages().size(), incoming, beforeTokens,
                    SIMULATED_CONTEXT_THRESHOLD, triggered));
            if (!triggered) continue;

            int eventIndex = events.size() + 1;
            int beforeMessages = history.size();
            var plan = IncrementalCompressionDiagnostics.plan(history, RETAIN_RECENT_ROUNDS);
            append(log, IncrementalCompressionDiagnostics.planLog(
                    eventIndex, plan, history, RETAIN_RECENT_ROUNDS));
            append(log, "--- BEFORE HISTORY PREVIEW ---");
            append(log, IncrementalCompressionDiagnostics.historyPreview(history));

            boolean compacted = compactor.compactIfNeeded(history, SIMULATED_CONTEXT_THRESHOLD);
            if (!compacted) {
                append(log, "compressionSkipped=true");
                continue;
            }

            int afterTokens = TokenBudget.estimateMessagesTokens(history);
            String summary = CompressionBenchmark.findSummary(history);
            var event = new IncrementalCompressionDiagnostics.CompressionEvent(
                    eventIndex,
                    i + 1,
                    beforeMessages,
                    history.size(),
                    beforeTokens,
                    afterTokens,
                    CompressionBenchmark.endsWith(history, plan.tail()),
                    CompressionBenchmark.toolProtocolValid(history),
                    !summary.isBlank(),
                    summary.length(),
                    recording.inputTokens(),
                    recording.outputTokens(),
                    recording.cachedInputTokens(),
                    IncrementalCompressionDiagnostics.preview(summary));
            events.add(event);

            append(log, IncrementalCompressionDiagnostics.resultLog(event));
            append(log, IncrementalCompressionDiagnostics.coverageLog(
                    eventIndex, plan, recording.requestText(), summary, history));
            append(log, "--- AFTER HISTORY PREVIEW ---");
            append(log, IncrementalCompressionDiagnostics.historyPreview(history));
            append(log, "=== CONTINUE REPLAY ===");
            append(log, "nextReadFromMessage=" + (i + 2));
            append(log, "");
        }

        appendFooter(log, events, history);
        writeIncrementalLog(log);

        assertFalse(events.isEmpty(), "真实 rollout 回放过程中应该至少触发一次压缩");
        assertTrue(events.stream().allMatch(event -> event.afterTokens() < event.beforeTokens()),
                "每次压缩后 token 都应该下降");
        assertTrue(events.stream().allMatch(IncrementalCompressionDiagnostics.CompressionEvent::recentTailPreserved),
                "每次压缩后都应该保留最近两轮上下文");
        assertTrue(events.stream().allMatch(IncrementalCompressionDiagnostics.CompressionEvent::toolProtocolValid),
                "每次压缩后 tool call / tool output 协议都应该保持合法");
        assertTrue(events.stream().allMatch(IncrementalCompressionDiagnostics.CompressionEvent::summaryPresent),
                "每次压缩后都应该插入历史摘要");
        assertTrue(!history.isEmpty() && "system".equals(history.get(0).role()),
                "最终 history 应该继续保留 system 消息");
        assertTrue(CompressionBenchmark.toolProtocolValid(history), "最终 history 的工具调用协议应该合法");
    }

    private static void appendFooter(StringBuilder log,
                                     List<IncrementalCompressionDiagnostics.CompressionEvent> events,
                                     List<LlmClient.Message> history) {
        append(log, "=== FINAL STATE ===");
        append(log, "compressionCount=" + events.size());
        append(log, "finalMessages=" + history.size());
        append(log, "finalTokens=" + TokenBudget.estimateMessagesTokens(history));
        append(log, "finalRoles=" + IncrementalCompressionDiagnostics.roleCounts(history));
        append(log, "finalToolProtocolValid=" + CompressionBenchmark.toolProtocolValid(history));
        append(log, "");
        append(log, "--- compression events ---");
        for (var event : events) append(log, event.toSummaryLine());
    }

    private static void append(StringBuilder log, String text) {
        log.append(text).append('\n');
    }

    private static void writeIncrementalLog(StringBuilder log) throws Exception {
        Path output = Path.of("target", "compression-benchmark", "incremental");
        Files.createDirectories(output);
        Files.writeString(output.resolve("benchmark.log"), log.toString(), StandardCharsets.UTF_8);
    }
}
