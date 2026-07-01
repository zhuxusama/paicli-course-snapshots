package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmClientFactory;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import ouccs.smy.paiclilearn.memory.ConversationHistoryCompactor;
import ouccs.smy.paiclilearn.memory.TokenBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 显式开启后，使用真实 provider 回放真实 rollout，模拟运行时上下文增长和多次压缩。
 *
 * <p>默认跳过，避免普通单测误调用真实模型。运行示例：
 * <pre>
 * mvn test -Dtest=RealLlmRolloutIncrementalCompressionSimulationTest \
 *   -DskipTests=false -Dpaicli.compression.real=true
 * </pre>
 */
class RealLlmRolloutIncrementalCompressionSimulationTest {
    private static final int SIMULATED_CONTEXT_THRESHOLD = 32_000;
    private static final int RETAIN_RECENT_ROUNDS = 2;
    private static final int HEAD_PREVIEW_MESSAGES = 6;
    private static final int TAIL_PREVIEW_MESSAGES = 12;
    private static final int PREVIEW_CHARS = 180;

    @Test
    void replaysRealRolloutAndCompactsIncrementallyWithConfiguredRealProvider()
            throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("paicli.compression.real"),
                "设置 -Dpaicli.compression.real=true 后才调用真实 LLM");

        Path fixture = CodexRolloutLoader.locateFixtureOrSkip();
        Path output = Path.of("target", "compression-benchmark", "incremental-real");
        StringBuilder log = new StringBuilder();
        try {
            var rollout = new CodexRolloutLoader().load(fixture);
            var client = new RecordingLlmClient(LlmClientFactory.create(LlmConfig.fromEnvironment()));
            ConversationHistoryCompactor compactor =
                    new ConversationHistoryCompactor(client, RETAIN_RECENT_ROUNDS);
            List<LlmClient.Message> history = new ArrayList<>();
            List<CompressionEvent> events = new ArrayList<>();

            appendHeader(log, fixture, client);

            for (int i = 0; i < rollout.messages().size(); i++) {
                LlmClient.Message incoming = rollout.messages().get(i).message();
                history.add(incoming);
                if (!"user".equals(incoming.role())) continue;

                int beforeTokens = TokenBudget.estimateMessagesTokens(history);
                if (beforeTokens < SIMULATED_CONTEXT_THRESHOLD) continue;

                int eventIndex = events.size() + 1;
                int beforeMessages = history.size();
                List<LlmClient.Message> expectedTail = recentTail(history, RETAIN_RECENT_ROUNDS);
                appendState(log, "BEFORE COMPRESSION #" + eventIndex, i + 1,
                        beforeTokens, history, "");

                boolean compacted = compactor.compactIfNeeded(history, SIMULATED_CONTEXT_THRESHOLD);
                if (!compacted) {
                    appendLine(log, "compressionSkipped=true");
                    continue;
                }

                int afterTokens = TokenBudget.estimateMessagesTokens(history);
                String summary = findSummary(history);
                CompressionEvent event = new CompressionEvent(
                        eventIndex,
                        i + 1,
                        beforeMessages,
                        history.size(),
                        beforeTokens,
                        afterTokens,
                        endsWith(history, expectedTail),
                        toolProtocolValid(history),
                        !summary.isBlank(),
                        client.lastInputTokens(),
                        client.lastOutputTokens(),
                        client.lastCachedInputTokens(),
                        preview(summary));
                events.add(event);

                appendState(log, "AFTER COMPRESSION #" + eventIndex, i + 1,
                        afterTokens, history, event.toSummaryLine());
            }

            appendFooter(log, events, history);
            writeLog(output, log);

            assertFalse(events.isEmpty(), "真实 rollout 增量回放过程中应该至少触发一次真实 LLM 压缩");
            assertTrue(events.stream().allMatch(event -> event.afterTokens() < event.beforeTokens()),
                    "每次真实 LLM 压缩后 token 都应该下降；详见 " + output.resolve("benchmark.log").toAbsolutePath());
            assertTrue(events.stream().allMatch(CompressionEvent::recentTailPreserved),
                    "每次压缩后都应该保留最近两轮上下文");
            assertTrue(events.stream().allMatch(CompressionEvent::toolProtocolValid),
                    "每次压缩后 tool call / tool output 协议都应该保持合法");
            assertTrue(events.stream().allMatch(CompressionEvent::summaryPresent),
                    "每次压缩后都应该插入历史摘要");
            assertTrue(!history.isEmpty() && "system".equals(history.get(0).role()),
                    "最终 history 应该继续保留 system 消息");
            assertTrue(toolProtocolValid(history), "最终 history 的工具调用协议应该合法");
        } catch (Throwable failure) {
            appendLine(log, "");
            appendLine(log, "=== FAILURE ===");
            appendLine(log, "errorType=" + failure.getClass().getSimpleName());
            appendLine(log, "error=" + CompressionBenchmarkReport.redact(String.valueOf(failure.getMessage())));
            CompressionBenchmarkReport.writeFailure(output, fixture, "incremental-real", failure);
            writeLog(output, log);
            throw failure;
        }
    }

    private static void appendHeader(StringBuilder log, Path fixture, LlmClient client) {
        appendLine(log, "=== s09 REAL LLM INCREMENTAL ROLLOUT COMPRESSION SIMULATION ===");
        appendLine(log, "fixture=" + fixture.toAbsolutePath().normalize());
        appendLine(log, "thresholdTokens=" + SIMULATED_CONTEXT_THRESHOLD);
        appendLine(log, "retainRecentRounds=" + RETAIN_RECENT_ROUNDS);
        appendLine(log, "provider=" + client.getProviderName());
        appendLine(log, "model=" + client.getModelName());
        appendLine(log, "");
    }

    private static void appendState(StringBuilder log, String title, int replayMessage,
                                    int tokens, List<LlmClient.Message> history,
                                    String extra) {
        appendLine(log, "=== " + title + " ===");
        appendLine(log, "replayMessage=" + replayMessage);
        appendLine(log, "tokens=" + tokens);
        appendLine(log, "messages=" + history.size());
        appendLine(log, "roles=" + roleCounts(history));
        appendLine(log, "toolProtocolValid=" + toolProtocolValid(history));
        if (!extra.isBlank()) appendLine(log, extra);
        appendLine(log, "--- history preview (safe, truncated) ---");
        appendLine(log, historyPreview(history));
        appendLine(log, "");
    }

    private static void appendFooter(StringBuilder log, List<CompressionEvent> events,
                                     List<LlmClient.Message> history) {
        appendLine(log, "=== FINAL STATE ===");
        appendLine(log, "compressionCount=" + events.size());
        appendLine(log, "finalMessages=" + history.size());
        appendLine(log, "finalTokens=" + TokenBudget.estimateMessagesTokens(history));
        appendLine(log, "finalRoles=" + roleCounts(history));
        appendLine(log, "finalToolProtocolValid=" + toolProtocolValid(history));
        appendLine(log, "");
        appendLine(log, "--- compression events ---");
        for (CompressionEvent event : events) appendLine(log, event.toSummaryLine());
    }

    private static String historyPreview(List<LlmClient.Message> history) {
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
                    .append(" chars=").append(semanticText(message).length())
                    .append(" toolCallId=").append(message.toolCallId())
                    .append(" toolCalls=").append(message.toolCalls() == null ? 0 : message.toolCalls().size())
                    .append(" preview=").append(preview(semanticText(message)))
                    .append('\n');
        }
        return out.toString();
    }

    private static Map<String, Integer> roleCounts(List<LlmClient.Message> history) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LlmClient.Message message : history) counts.merge(message.role(), 1, Integer::sum);
        return counts;
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

    private static String semanticText(LlmClient.Message message) {
        StringBuilder text = new StringBuilder();
        if (message.content() != null) text.append(message.content());
        if (message.toolCalls() != null) {
            for (LlmClient.ToolCall call : message.toolCalls()) {
                text.append(" TOOL_CALL ").append(call.function().name()).append(' ')
                        .append(call.function().arguments());
            }
        }
        if (message.toolCallId() != null) text.append(" TOOL_RESULT_FOR ").append(message.toolCallId());
        return text.toString().replaceAll("\\s+", " ").trim();
    }

    private static String preview(String text) {
        String safe = CompressionBenchmarkReport.redact(text == null ? "" : text)
                .replaceAll("\\s+", " ").trim();
        return safe.length() <= PREVIEW_CHARS ? safe : safe.substring(0, PREVIEW_CHARS) + "...";
    }

    private static void appendLine(StringBuilder log, String line) {
        System.out.println(line);
        log.append(line).append('\n');
    }

    private static void writeLog(Path output, StringBuilder log) throws IOException {
        Files.createDirectories(output);
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
            boolean summaryPresent,
            int modelInputTokens,
            int modelOutputTokens,
            int modelCachedInputTokens,
            String summaryPreview) {
        double reductionRatio() {
            return beforeTokens == 0 ? 0.0 : (beforeTokens - afterTokens) / (double) beforeTokens;
        }

        String toSummaryLine() {
            return String.format(Locale.ROOT,
                    "compression #%d at replayMessage=%d: messages=%d->%d tokens=%d->%d reduction=%.2f%% tailPreserved=%s toolProtocolValid=%s summaryPresent=%s modelTokens(in=%d,out=%d,cache=%d) summaryPreview=%s",
                    index, replayMessageIndex, beforeMessages, afterMessages, beforeTokens,
                    afterTokens, reductionRatio() * 100.0, recentTailPreserved,
                    toolProtocolValid, summaryPresent, modelInputTokens, modelOutputTokens,
                    modelCachedInputTokens, summaryPreview);
        }
    }

    private static final class RecordingLlmClient implements LlmClient {
        private final LlmClient delegate;
        private int lastInputTokens;
        private int lastOutputTokens;
        private int lastCachedInputTokens;

        private RecordingLlmClient(LlmClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener)
                throws IOException {
            ChatResponse response = delegate.chat(messages, tools, listener);
            if (response != null) {
                lastInputTokens = response.inputTokens();
                lastOutputTokens = response.outputTokens();
                lastCachedInputTokens = response.cachedInputTokens();
            }
            return response;
        }

        @Override public String getModelName() { return delegate.getModelName(); }
        @Override public String getProviderName() { return delegate.getProviderName(); }
        @Override public int maxContextWindow() { return delegate.maxContextWindow(); }
        @Override public boolean supportsPromptCaching() { return delegate.supportsPromptCaching(); }
        @Override public String promptCacheMode() { return delegate.promptCacheMode(); }
        int lastInputTokens() { return lastInputTokens; }
        int lastOutputTokens() { return lastOutputTokens; }
        int lastCachedInputTokens() { return lastCachedInputTokens; }
    }
}
