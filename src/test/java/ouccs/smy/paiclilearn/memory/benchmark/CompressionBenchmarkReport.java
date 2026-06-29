package ouccs.smy.paiclilearn.memory.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 把压缩基准写成安全的文本日志和机器可读 JSON。 */
final class CompressionBenchmarkReport {
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s,;]+", Pattern.MULTILINE);
    private static final Pattern SECRET = Pattern.compile("(?i)\\bsk-[a-z0-9_-]{8,}\\b");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(?:api[_-]?key|authorization)\\s*[:=]\\s*[^\\s,;]+", Pattern.MULTILINE);

    private CompressionBenchmarkReport() {}

    static void write(Path outputDirectory, BenchmarkReportSnapshot snapshot) throws IOException {
        Files.createDirectories(outputDirectory);
        Map<String, Object> safe = safePayload(snapshot);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(outputDirectory.resolve("report.json"),
                mapper.writeValueAsString(safe), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("benchmark.log"),
                textLog(snapshot), StandardCharsets.UTF_8);
        printSummary(snapshot);
    }

    static void writeFailure(Path outputDirectory, Path fixture, String mode, Throwable error)
            throws IOException {
        Files.createDirectories(outputDirectory);
        String errorType = error == null ? "UnknownFailure" : error.getClass().getSimpleName();
        String message = redact(error == null ? "unknown failure" : String.valueOf(error.getMessage()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fixture", fixture.toAbsolutePath().normalize().toString());
        payload.put("mode", mode);
        payload.put("status", "failed");
        payload.put("errorType", errorType);
        payload.put("error", message);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(outputDirectory.resolve("report.json"),
                mapper.writeValueAsString(payload), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("benchmark.log"),
                "status=failed\nmode=" + mode + "\nfixture=" + fixture.toAbsolutePath().normalize()
                        + "\nerrorType=" + errorType + "\nerror=" + message + "\n",
                StandardCharsets.UTF_8);
    }

    private static Map<String, Object> safePayload(BenchmarkReportSnapshot item) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("fixture", item.fixture().toAbsolutePath().normalize().toString());
        root.put("baselineFixture", item.baselineFixture());
        root.put("mode", item.mode());
        root.put("provider", item.provider());
        root.put("model", item.model());
        root.put("events", item.stats());

        List<Map<String, Object>> messages = new ArrayList<>();
        for (TracedMessage message : item.messages()) {
            int index = messages.size();
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("sourceLine", message.sourceLine());
            safe.put("sourceType", message.sourceType());
            safe.put("role", message.message().role());
            safe.put("characters", message.characters());
            safe.put("sha256", message.sha256());
            safe.put("preview", redact(message.preview()));
            safe.put("toolCallId", message.message().toolCallId());
            safe.put("toolCallCount", message.message().toolCalls() == null
                    ? 0 : message.message().toolCalls().size());
            safe.put("zone", zone(item, index, message));
            messages.add(safe);
        }
        root.put("messages", messages);
        root.put("pairingIssues", item.pairingIssues());

        Map<String, Object> compression = new LinkedHashMap<>();
        compression.put("beforeTokens", item.beforeTokens());
        compression.put("afterTokens", item.afterTokens());
        compression.put("compressedMessageCount", item.compressedMessageCount());
        compression.put("retainedMessageCount", item.retainedMessageCount());
        compression.put("tokenReductionRatio", item.tokenReductionRatio());
        compression.put("inputCoverageRatio", item.inputCoverageRatio());
        compression.put("inputCoverageByRole", item.inputCoverageByRole());
        compression.put("elapsedMillis", item.elapsedMillis());
        compression.put("summary", redact(item.summary()));
        root.put("compression", compression);
        root.put("facts", item.facts());
        root.put("gates", item.gates());

        Map<String, Object> modelUsage = new LinkedHashMap<>();
        modelUsage.put("inputTokens", item.modelInputTokens());
        modelUsage.put("outputTokens", item.modelOutputTokens());
        modelUsage.put("cachedInputTokens", item.modelCachedInputTokens());
        root.put("modelUsage", modelUsage);
        return root;
    }

    private static String textLog(BenchmarkReportSnapshot item) {
        StringBuilder out = new StringBuilder();
        out.append("=== s09 REAL ROLLOUT COMPRESSION BENCHMARK ===\n");
        out.append("fixture=").append(item.fixture().toAbsolutePath().normalize()).append('\n');
        out.append("baselineFixture=").append(item.baselineFixture()).append('\n');
        out.append("mode=").append(item.mode()).append(" provider=").append(item.provider())
                .append(" model=").append(item.model()).append('\n');
        out.append("events=").append(item.stats().totalEvents())
                .append(" responseItems=").append(item.stats().responseItems())
                .append(" parseErrors=").append(item.stats().parseErrors()).append('\n');
        out.append("functionCalls=").append(item.stats().functionCalls())
                .append(" outputs=").append(item.stats().functionCallOutputs())
                .append(" pairingIssues=").append(item.pairingIssues().size()).append('\n');
        out.append("beforeTokens=").append(item.beforeTokens())
                .append(" afterTokens=").append(item.afterTokens()).append('\n');
        out.append("compressedMessages=").append(item.compressedMessageCount())
                .append(" retainedMessages=").append(item.retainedMessageCount()).append('\n');
        out.append(String.format(Locale.ROOT, "tokenReductionPercent=%.2f%n",
                item.tokenReductionRatio() * 100.0));
        out.append(String.format(Locale.ROOT, "inputCoveragePercent=%.2f%n",
                item.inputCoverageRatio() * 100.0));
        item.inputCoverageByRole().forEach((role, ratio) -> out.append(String.format(Locale.ROOT,
                "inputCoverage.%s=%.2f%%%n", role, ratio * 100.0)));
        out.append("elapsedMillis=").append(item.elapsedMillis()).append('\n');

        out.append("\n--- MAPPED MESSAGES (SAFE) ---\n");
        for (int i = 0; i < item.messages().size(); i++) {
            TracedMessage message = item.messages().get(i);
            out.append('[').append(i).append("] line=").append(message.sourceLine())
                    .append(" source=").append(message.sourceType())
                    .append(" role=").append(message.message().role())
                    .append(" chars=").append(message.characters())
                    .append(" sha256=").append(message.sha256())
                    .append(" zone=").append(zone(item, i, message))
                    .append(" preview=").append(redact(message.preview())).append('\n');
        }

        out.append("\n--- FACT RECALL ---\n");
        for (FactEvaluation fact : item.facts()) {
            out.append(fact.id()).append(" matched=").append(fact.matched())
                    .append(" location=").append(fact.location())
                    .append(" expected=").append(redact(fact.expected())).append('\n');
        }
        out.append("\n--- GATES ---\n");
        for (GateEvaluation gate : item.gates()) {
            out.append(gate.name()).append(" passed=").append(gate.passed())
                    .append(" expected=").append(gate.expected())
                    .append(" actual=").append(gate.actual()).append('\n');
        }
        out.append("\n--- SUMMARY ---\n").append(redact(item.summary())).append('\n');
        return out.toString();
    }

    private static void printSummary(BenchmarkReportSnapshot item) {
        long matched = item.facts().stream().filter(FactEvaluation::matched).count();
        long failed = item.gates().stream().filter(gate -> !gate.passed()).count();
        System.out.printf(Locale.ROOT,
                "[s09-compression] events=%d messages=%d tokens=%d->%d reduction=%.2f%% facts=%d/%d gatesFailed=%d report=%s%n",
                item.stats().totalEvents(), item.messages().size(), item.beforeTokens(), item.afterTokens(),
                item.tokenReductionRatio() * 100.0, matched, item.facts().size(), failed,
                Path.of("target", "compression-benchmark", "report.json"));
    }

    static String redact(String text) {
        if (text == null) return "";
        String safe = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        safe = SECRET.matcher(safe).replaceAll("sk-[REDACTED]");
        return KEY_VALUE.matcher(safe).replaceAll("credential=[REDACTED]");
    }

    private static String zone(BenchmarkReportSnapshot item, int index, TracedMessage message) {
        if (index == 0 && "system".equals(message.message().role())) return "system-preserved";
        return index >= item.messages().size() - item.retainedMessageCount()
                ? "recent-tail" : "compressed";
    }
}

record BenchmarkReportSnapshot(
        Path fixture,
        boolean baselineFixture,
        RolloutStats stats,
        List<TracedMessage> messages,
        List<String> pairingIssues,
        int compressedMessageCount,
        int retainedMessageCount,
        Map<String, Double> inputCoverageByRole,
        int beforeTokens,
        int afterTokens,
        double tokenReductionRatio,
        double inputCoverageRatio,
        long elapsedMillis,
        String summary,
        List<FactEvaluation> facts,
        List<GateEvaluation> gates,
        String mode,
        String provider,
        String model,
        int modelInputTokens,
        int modelOutputTokens,
        int modelCachedInputTokens) {}

record FactEvaluation(String id, String expected, boolean matched, String location) {}

record GateEvaluation(String name, boolean passed, String expected, String actual) {}
