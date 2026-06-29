package ouccs.smy.paiclilearn.memory.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;

/**
 * 流式读取 Codex rollout JSONL，并映射为 s09 压缩器可消费的消息历史。
 * 测试日志只暴露哈希、长度和短预览，避免复制真实会话全文。
 */
final class CodexRolloutLoader {
    static final String FIXTURE_PROPERTY = "paicli.rollout.fixture";
    static final String BASELINE_FILE =
            "rollout-2026-06-05T09-20-05-019e955d-9a1c-7512-8442-c5c655d938ac.jsonl";

    private final ObjectMapper mapper = new ObjectMapper();

    static Path locateFixture() {
        String explicit = System.getProperty(FIXTURE_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("显式 rollout fixture 不存在: " + path);
            }
            return path;
        }

        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            Path direct = cursor.resolve(BASELINE_FILE);
            if (Files.isRegularFile(direct)) return direct;
            if (cursor.getFileName() != null && "chapters".equals(cursor.getFileName().toString())) {
                Path chapterFixture = cursor.resolve("s09_context_budget").resolve(BASELINE_FILE);
                if (Files.isRegularFile(chapterFixture)) return chapterFixture;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("未找到真实 rollout fixture；可设置 -D" + FIXTURE_PROPERTY + "=<绝对路径>");
    }

    /** 课程完整目录外单独运行快照时明确跳过；显式错误路径仍然失败。 */
    static Path locateFixtureOrSkip() {
        try {
            return locateFixture();
        } catch (IllegalStateException missingDefaultFixture) {
            Assumptions.assumeTrue(false, missingDefaultFixture.getMessage());
            throw missingDefaultFixture;
        }
    }

    LoadedRollout load(Path fixture) throws IOException {
        Path normalized = fixture.toAbsolutePath().normalize();
        MutableStats stats = new MutableStats();
        List<TracedMessage> messages = new ArrayList<>();
        List<String> pairingIssues = new ArrayList<>();
        Map<String, Integer> calls = new LinkedHashMap<>();
        Map<String, Integer> outputs = new LinkedHashMap<>();
        boolean baseInstructionsAdded = false;

        try (BufferedReader reader = Files.newBufferedReader(normalized, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                stats.totalEvents++;
                JsonNode root;
                try {
                    root = mapper.readTree(line);
                } catch (Exception error) {
                    stats.parseErrors++;
                    pairingIssues.add("line " + lineNumber + " JSON 解析失败 sha256=" + sha256(line));
                    continue;
                }

                String eventType = root.path("type").asText("");
                stats.eventTypes.merge(eventType, 1, Integer::sum);
                JsonNode payload = root.path("payload");

                if ("session_meta".equals(eventType)) {
                    if (!baseInstructionsAdded) {
                        String text = payload.path("base_instructions").path("text").asText("");
                        if (!text.isBlank()) {
                            messages.add(trace(lineNumber, "session_meta/base_instructions",
                                    LlmClient.Message.system(text)));
                            baseInstructionsAdded = true;
                        }
                    }
                    continue;
                }

                if (!"response_item".equals(eventType)) {
                    stats.ignoredEvents++;
                    continue;
                }

                stats.responseItems++;
                String payloadType = payload.path("type").asText("");
                stats.payloadTypes.merge(payloadType, 1, Integer::sum);
                switch (payloadType) {
                    case "message" -> mapMessage(payload, lineNumber, messages, stats);
                    case "function_call" -> {
                        stats.functionCalls++;
                        String callId = payload.path("call_id").asText("");
                        if (callId.isBlank() || calls.putIfAbsent(callId, lineNumber) != null) {
                            pairingIssues.add("重复或空 function_call id，line=" + lineNumber + ", id=" + callId);
                        }
                        var function = new LlmClient.ToolCall.Function(
                                payload.path("name").asText(""), payload.path("arguments").asText(""));
                        var call = new LlmClient.ToolCall(callId, function);
                        messages.add(trace(lineNumber, "response_item/function_call",
                                LlmClient.Message.assistant(null, "", List.of(call))));
                    }
                    case "function_call_output" -> {
                        stats.functionCallOutputs++;
                        String callId = payload.path("call_id").asText("");
                        if (callId.isBlank() || outputs.putIfAbsent(callId, lineNumber) != null) {
                            pairingIssues.add("重复或空 function_call_output id，line=" + lineNumber + ", id=" + callId);
                        }
                        messages.add(trace(lineNumber, "response_item/function_call_output",
                                LlmClient.Message.tool(callId, payload.path("output").asText(""))));
                    }
                    default -> stats.ignoredEvents++;
                }
            }
        }

        for (Map.Entry<String, Integer> call : calls.entrySet()) {
            if (!outputs.containsKey(call.getKey())) {
                pairingIssues.add("function_call 缺少 output，line=" + call.getValue() + ", id=" + call.getKey());
            }
        }
        for (Map.Entry<String, Integer> output : outputs.entrySet()) {
            if (!calls.containsKey(output.getKey())) {
                pairingIssues.add("function_call_output 缺少 call，line=" + output.getValue() + ", id=" + output.getKey());
            }
        }

        return new LoadedRollout(normalized, List.copyOf(messages), stats.freeze(),
                List.copyOf(pairingIssues), BASELINE_FILE.equals(normalized.getFileName().toString()));
    }

    private static void mapMessage(JsonNode payload, int lineNumber,
                                   List<TracedMessage> messages, MutableStats stats) {
        String originalRole = payload.path("role").asText("");
        StringBuilder text = new StringBuilder();
        for (JsonNode content : payload.path("content")) {
            String part = content.path("text").asText("");
            if (!part.isEmpty()) {
                if (!text.isEmpty()) text.append('\n');
                text.append(part);
            }
        }
        switch (originalRole) {
            case "user" -> {
                stats.userMessages++;
                messages.add(trace(lineNumber, "response_item/message/user",
                        LlmClient.Message.user(text.toString())));
            }
            case "assistant" -> {
                stats.assistantMessages++;
                messages.add(trace(lineNumber, "response_item/message/assistant",
                        LlmClient.Message.assistant(text.toString())));
            }
            case "developer" -> messages.add(trace(lineNumber, "response_item/message/developer",
                    LlmClient.Message.system(text.toString())));
            default -> stats.ignoredEvents++;
        }
    }

    private static TracedMessage trace(int sourceLine, String sourceType, LlmClient.Message message) {
        String auditText = auditText(message);
        return new TracedMessage(sourceLine, sourceType, message, auditText.length(),
                sha256(auditText), preview(auditText));
    }

    private static String auditText(LlmClient.Message message) {
        StringBuilder value = new StringBuilder(message.role()).append('|');
        if (message.content() != null) value.append(message.content());
        if (message.toolCalls() != null) {
            for (LlmClient.ToolCall call : message.toolCalls()) {
                value.append('|').append(call.id()).append('|').append(call.function().name())
                        .append('|').append(call.function().arguments());
            }
        }
        if (message.toolCallId() != null) value.append('|').append(message.toolCallId());
        return value.toString();
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String preview(String value) {
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= 80 ? flat : flat.substring(0, 80) + "…";
    }

    private static final class MutableStats {
        int totalEvents;
        int responseItems;
        int userMessages;
        int assistantMessages;
        int functionCalls;
        int functionCallOutputs;
        int parseErrors;
        int ignoredEvents;
        final Map<String, Integer> eventTypes = new LinkedHashMap<>();
        final Map<String, Integer> payloadTypes = new LinkedHashMap<>();

        RolloutStats freeze() {
            return new RolloutStats(totalEvents, responseItems, userMessages, assistantMessages,
                    functionCalls, functionCallOutputs, parseErrors, ignoredEvents,
                    Map.copyOf(eventTypes), Map.copyOf(payloadTypes));
        }
    }
}

record LoadedRollout(Path fixture, List<TracedMessage> messages, RolloutStats stats,
                     List<String> pairingIssues, boolean baselineFixture) {}

record TracedMessage(int sourceLine, String sourceType, LlmClient.Message message,
                     int characters, String sha256, String preview) {}

record RolloutStats(int totalEvents, int responseItems, int userMessages, int assistantMessages,
                    int functionCalls, int functionCallOutputs, int parseErrors, int ignoredEvents,
                    Map<String, Integer> eventTypes, Map<String, Integer> payloadTypes) {}
