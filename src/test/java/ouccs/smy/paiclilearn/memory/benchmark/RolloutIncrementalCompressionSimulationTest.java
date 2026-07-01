package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.memory.ConversationHistoryCompactor;
import ouccs.smy.paiclilearn.memory.TokenBudget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回放真实 rollout，模拟运行时 conversationHistory 逐步增长，
 * 只有达到上下文阈值后才触发压缩。
 */
class RolloutIncrementalCompressionSimulationTest {
    private static final int SIMULATED_CONTEXT_THRESHOLD = 32_000;
    private static final int RETAIN_RECENT_ROUNDS = 2;

    @Test
    void replaysRealRolloutAndCompactsWhenSimulatedContextThresholdIsReached()
            throws Exception {
        var rollout = new CodexRolloutLoader().load(CodexRolloutLoader.locateFixtureOrSkip());
        List<LlmClient.Message> history = new ArrayList<>();
        ConversationHistoryCompactor compactor = new ConversationHistoryCompactor(
                new DeterministicSummaryLlmClient(), RETAIN_RECENT_ROUNDS);
        List<CompressionEvent> events = new ArrayList<>();

        for (int i = 0; i < rollout.messages().size(); i++) {
            LlmClient.Message incoming = rollout.messages().get(i).message();
            history.add(incoming);
            if (!"user".equals(incoming.role())) continue;

            int beforeTokens = TokenBudget.estimateMessagesTokens(history);
            if (beforeTokens < SIMULATED_CONTEXT_THRESHOLD) continue;

            int beforeMessages = history.size();
            List<LlmClient.Message> expectedTail = recentTail(history, RETAIN_RECENT_ROUNDS);
            boolean compacted = compactor.compactIfNeeded(history, SIMULATED_CONTEXT_THRESHOLD);
            if (!compacted) continue;

            int afterTokens = TokenBudget.estimateMessagesTokens(history);
            events.add(new CompressionEvent(
                    events.size() + 1,
                    i + 1,
                    beforeMessages,
                    history.size(),
                    beforeTokens,
                    afterTokens,
                    endsWith(history, expectedTail),
                    toolProtocolValid(history),
                    !findSummary(history).isBlank()));
        }

        writeIncrementalLog(rollout.fixture(), events, history);

        assertFalse(events.isEmpty(), "真实 rollout 回放过程中应该至少触发一次压缩");
        assertTrue(events.stream().allMatch(event -> event.afterTokens() < event.beforeTokens()),
                "每次压缩后 token 都应该下降");
        assertTrue(events.stream().allMatch(CompressionEvent::recentTailPreserved),
                "每次压缩后都应该保留最近两轮上下文");
        assertTrue(events.stream().allMatch(CompressionEvent::toolProtocolValid),
                "每次压缩后 tool call / tool output 协议都应该保持合法");
        assertTrue(events.stream().allMatch(CompressionEvent::summaryPresent),
                "每次压缩后都应该插入历史摘要");
        assertTrue(!history.isEmpty() && "system".equals(history.get(0).role()),
                "最终 history 应该继续保留 system 消息");
        assertTrue(toolProtocolValid(history), "最终 history 的工具调用协议应该合法");
    }

    private static List<LlmClient.Message> recentTail(List<LlmClient.Message> history, int rounds) {
        int users = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) users++;
            if (users >= rounds) return List.copyOf(history.subList(i, history.size()));
        }
        return List.copyOf(history);
    }

    private static boolean endsWith(List<LlmClient.Message> actual, List<LlmClient.Message> suffix) {
        if (suffix.size() > actual.size()) return false;
        return actual.subList(actual.size() - suffix.size(), actual.size()).equals(suffix);
    }

    private static boolean toolProtocolValid(List<LlmClient.Message> history) {
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

    private static String findSummary(List<LlmClient.Message> history) {
        for (LlmClient.Message message : history) {
            if (!"user".equals(message.role()) || message.content() == null) continue;
            if (message.content().startsWith("[已压缩]")
                    || message.content().startsWith("[宸插帇缂")) {
                return message.content();
            }
        }
        return "";
    }

    private static void writeIncrementalLog(Path fixture, List<CompressionEvent> events,
                                            List<LlmClient.Message> finalHistory)
            throws Exception {
        Path output = Path.of("target", "compression-benchmark", "incremental");
        Files.createDirectories(output);
        StringBuilder log = new StringBuilder();
        log.append("=== s09 INCREMENTAL ROLLOUT COMPRESSION SIMULATION ===\n");
        log.append("fixture=").append(fixture.toAbsolutePath().normalize()).append('\n');
        log.append("thresholdTokens=").append(SIMULATED_CONTEXT_THRESHOLD).append('\n');
        log.append("retainRecentRounds=").append(RETAIN_RECENT_ROUNDS).append('\n');
        log.append("compressionCount=").append(events.size()).append('\n');
        log.append("finalMessages=").append(finalHistory.size()).append('\n');
        log.append("finalTokens=").append(TokenBudget.estimateMessagesTokens(finalHistory)).append('\n');
        log.append('\n');

        for (CompressionEvent event : events) {
            log.append(String.format(Locale.ROOT,
                    "compression #%d at replayMessage=%d: messages=%d->%d tokens=%d->%d reduction=%.2f%% tailPreserved=%s toolProtocolValid=%s summaryPresent=%s%n",
                    event.index(), event.replayMessageIndex(), event.beforeMessages(),
                    event.afterMessages(), event.beforeTokens(), event.afterTokens(),
                    event.reductionRatio() * 100.0, event.recentTailPreserved(),
                    event.toolProtocolValid(), event.summaryPresent()));
        }
        Files.writeString(output.resolve("benchmark.log"), log.toString(), StandardCharsets.UTF_8);
    }

    private record CompressionEvent(
            int index,
            int replayMessageIndex,
            int beforeMessages,
            int afterMessages,
            int beforeTokens,
            int afterTokens,
            boolean recentTailPreserved,
            boolean toolProtocolValid,
            boolean summaryPresent) {
        double reductionRatio() {
            return beforeTokens == 0 ? 0.0 : (beforeTokens - afterTokens) / (double) beforeTokens;
        }
    }
}
