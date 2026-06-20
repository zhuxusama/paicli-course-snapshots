package ouccs.smy.paiclilearn.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 共享 OpenAI-compatible 请求序列化、SSE 聚合与错误处理。
 */
public abstract class AbstractOpenAiCompatibleClient implements LlmClient {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    private final OkHttpClient httpClient;

    protected AbstractOpenAiCompatibleClient() { this(defaultHttpClient()); }
    protected AbstractOpenAiCompatibleClient(OkHttpClient httpClient) { this.httpClient = httpClient; }

    protected abstract String getApiUrl();
    protected abstract String getModel();
    protected abstract String getApiKey();
    protected boolean shouldSendReasoningContentInRequestHistory() { return false; }
    protected void customizeRequestBody(ObjectNode requestBody) {}

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
        StreamListener target = listener == null ? StreamListener.NO_OP : listener;
        RequestBody body = RequestBody.create(buildRequestBody(messages, tools).toString(),
                MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(getApiUrl())
                .header("Authorization", "Bearer " + getApiKey()).post(body).build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful()) {
                String error = responseBody == null ? "无响应体" : responseBody.string();
                if (error.length() > 1000) error = error.substring(0, 1000) + "...";
                throw new IOException("LLM API 请求失败: HTTP " + response.code() + " - " + error);
            }
            if (responseBody == null) throw new IOException("LLM API 返回空响应体");
            return readSse(responseBody.source(), target);
        }
    }

    ObjectNode buildRequestBody(List<Message> messages, List<Tool> tools) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", getModel());
        root.put("stream", true);
        ArrayNode history = root.putArray("messages");
        for (Message message : messages) {
            ObjectNode item = history.addObject().put("role", message.role()).put("content", message.content());
            if (shouldSendReasoningContentInRequestHistory() && "assistant".equals(message.role())
                    && message.reasoningContent() != null && !message.reasoningContent().isBlank()) {
                item.put("reasoning_content", message.reasoningContent());
            }
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                ArrayNode calls = item.putArray("tool_calls");
                for (ToolCall call : message.toolCalls()) {
                    ObjectNode callNode = calls.addObject().put("id", call.id()).put("type", "function");
                    callNode.putObject("function").put("name", call.function().name())
                            .put("arguments", call.function().arguments());
                }
            }
            if (message.toolCallId() != null) item.put("tool_call_id", message.toolCallId());
        }
        if (tools != null && !tools.isEmpty()) {
            ArrayNode definitions = root.putArray("tools");
            for (Tool tool : tools) {
                ObjectNode function = definitions.addObject().put("type", "function").putObject("function");
                function.put("name", tool.name()).put("description", tool.description()).set("parameters", tool.parameters());
            }
        }
        customizeRequestBody(root);
        return root;
    }

    private ChatResponse readSse(BufferedSource source, StreamListener listener) throws IOException {
        String role = "assistant";
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ToolCallAccumulator> calls = new ArrayList<>();
        int input = 0, output = 0, cached = 0;
        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) break;
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;
            String payload = trimmed.substring(5).trim();
            if (payload.isEmpty()) continue;
            if ("[DONE]".equals(payload)) break;
            JsonNode root;
            try { root = MAPPER.readTree(payload); }
            catch (Exception e) { throw new IOException("无法解析 LLM SSE 数据: " + payload, e); }
            JsonNode usage = root.path("usage");
            input = usage.path("prompt_tokens").asInt(input);
            output = usage.path("completion_tokens").asInt(output);
            cached = cachedTokens(usage, cached);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) continue;
            JsonNode delta = choices.get(0).path("delta");
            if (delta.isMissingNode() || delta.isNull()) delta = choices.get(0).path("message");
            String nextRole = delta.path("role").asText("");
            if (!nextRole.isEmpty()) role = nextRole;
            String reasoningDelta = reasoningDelta(delta);
            if (!reasoningDelta.isEmpty()) { reasoning.append(reasoningDelta); listener.onReasoningDelta(reasoningDelta); }
            String contentDelta = delta.path("content").asText("");
            if (!contentDelta.isEmpty()) { content.append(contentDelta); listener.onContentDelta(contentDelta); }
            mergeToolCalls(calls, delta.path("tool_calls"));
        }
        return new ChatResponse(role, content.toString(), reasoning.toString(), buildToolCalls(calls), input, output, cached);
    }

    private static String reasoningDelta(JsonNode delta) {
        for (String field : new String[]{"reasoning_content", "reasoning"}) {
            String value = delta.path(field).asText("");
            if (!value.isEmpty()) return value;
        }
        StringBuilder value = new StringBuilder();
        JsonNode details = delta.path("reasoning_details");
        if (details.isArray()) for (JsonNode detail : details) {
            String text = detail.path("text").asText(detail.path("content").asText(""));
            value.append(text);
        }
        return value.toString();
    }

    private static int cachedTokens(JsonNode usage, int fallback) {
        int value = usage.path("cached_tokens").asInt(fallback);
        value = usage.path("prompt_cache_hit_tokens").asInt(value);
        value = usage.path("input_cache_hit_tokens").asInt(value);
        value = usage.path("prompt_tokens_details").path("cached_tokens").asInt(value);
        return usage.path("input_tokens_details").path("cached_tokens").asInt(value);
    }

    private static void mergeToolCalls(List<ToolCallAccumulator> calls, JsonNode node) {
        if (!node.isArray()) return;
        for (JsonNode item : node) {
            int index = item.path("index").asInt(calls.size());
            while (calls.size() <= index) calls.add(new ToolCallAccumulator());
            ToolCallAccumulator target = calls.get(index);
            String id = item.path("id").asText("");
            if (!id.isEmpty()) target.id = id;
            target.name.append(item.path("function").path("name").asText(""));
            target.arguments.append(item.path("function").path("arguments").asText(""));
        }
    }

    private static List<ToolCall> buildToolCalls(List<ToolCallAccumulator> accumulators) {
        List<ToolCall> result = new ArrayList<>();
        for (ToolCallAccumulator item : accumulators) if (item.id != null && !item.id.isBlank()) {
            result.add(new ToolCall(item.id, new ToolCall.Function(item.name.toString(), item.arguments.toString())));
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    protected static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(600, TimeUnit.SECONDS).build();
    }

    private static final class ToolCallAccumulator {
        private String id;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }
}
