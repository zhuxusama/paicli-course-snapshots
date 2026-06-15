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
import okio.BufferedSource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OpenAI 兼容协议（SSE 流式）的 LLM 客户端实现。
 * <p>
 * 适用于任何遵循 {@code /v1/chat/completions} + SSE 格式的 API 端点，
 * 包括 OpenAI、GLM（智谱）、DeepSeek、Step、Kimi 等。
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmConfig config;
    private final OkHttpClient httpClient;

    /**
     * 使用默认超时配置创建客户端。
     *
     * @param config LLM 配置（URL / Key / Model）
     */
    public OpenAiCompatibleClient(LlmConfig config) {
        this(config, defaultHttpClient());
    }

    /**
     * 注入自定义 OkHttpClient（主要用于测试）。
     *
     * @param config      LLM 配置
     * @param httpClient  OkHttp 客户端实例
     */
    public OpenAiCompatibleClient(LlmConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    // ── LlmClient 实现 ────────────────────────────────────────────

    @Override
    public ChatResponse chat(List<Message> messages, StreamListener listener) throws IOException {
        RequestBody body = buildRequestBody(messages);
        Request request = new Request.Builder()
                .url(config.apiUrl())
                .header("Authorization", "Bearer " + config.apiKey())
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("LLM API 请求失败: HTTP " + response.code() + " - " + errorBody);
            }
            if (response.body() == null) {
                throw new IOException("LLM API 返回空响应体");
            }
            return readSse(response.body().source(), listener);
        }
    }

    // ── SSE 流解析 ────────────────────────────────────────────────

    /**
     * 解析 Server-Sent Events 流，通过 listener 回调增量内容。
     *
     * @param source   OkHttp 响应体源
     * @param listener 流式事件监听器
     * @return 聚合后的完整响应
     * @throws IOException 读取异常
     */
    private ChatResponse readSse(BufferedSource source, StreamListener listener) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        int inputTokens = 0;
        int outputTokens = 0;

        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) {
                break;
            }

            line = line.trim();
            // 跳过空行和注释
            if (line.isEmpty() || line.startsWith(":")) {
                continue;
            }

            // 只处理 data: 行
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring(5).trim();

            // 流结束标记
            if ("[DONE]".equals(data)) {
                break;
            }

            try {
                JsonNode json = MAPPER.readTree(data);

                // 兼容 delta 和 message 两种格式
                JsonNode deltaOrMessage = json.path("choices")
                        .path(0)
                        .has("delta")
                        ? json.path("choices").path(0).path("delta")
                        : json.path("choices").path(0).path("message");

                // 正文内容增量
                if (deltaOrMessage.has("content") && !deltaOrMessage.path("content").isNull()) {
                    String contentDelta = deltaOrMessage.path("content").asText("");
                    if (!contentDelta.isEmpty()) {
                        contentBuilder.append(contentDelta);
                        listener.onContentDelta(contentDelta);
                    }
                }

                // 推理内容增量（reasoning_content 或 reasoning）
                if (deltaOrMessage.has("reasoning_content")) {
                    String reasoningDelta = deltaOrMessage.path("reasoning_content").asText("");
                    if (!reasoningDelta.isEmpty()) {
                        reasoningBuilder.append(reasoningDelta);
                        listener.onReasoningDelta(reasoningDelta);
                    }
                } else if (deltaOrMessage.has("reasoning")) {
                    String reasoningDelta = deltaOrMessage.path("reasoning").asText("");
                    if (!reasoningDelta.isEmpty()) {
                        reasoningBuilder.append(reasoningDelta);
                        listener.onReasoningDelta(reasoningDelta);
                    }
                }

                // Token 统计（通常在最后一块或独立 usage 字段）
                JsonNode usage = json.path("usage");
                if (usage.has("prompt_tokens")) {
                    inputTokens = usage.path("prompt_tokens").asInt();
                }
                if (usage.has("completion_tokens")) {
                    outputTokens = usage.path("completion_tokens").asInt();
                }

            } catch (Exception e) {
                // 单行解析失败不中断整条流，跳过即可
            }
        }

        return new ChatResponse(
                contentBuilder.toString(),
                reasoningBuilder.toString(),
                inputTokens,
                outputTokens
        );
    }

    // ── 请求体构建 ────────────────────────────────────────────────

    private RequestBody buildRequestBody(List<Message> messages) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", config.model());
        root.put("stream", true);

        ArrayNode msgArray = root.putArray("messages");
        for (Message msg : messages) {
            ObjectNode msgNode = msgArray.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }

        return RequestBody.create(MAPPER.writeValueAsString(root), JSON);
    }

    // ── 默认 HttpClient ───────────────────────────────────────────

    private static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(600, TimeUnit.SECONDS)
                .build();
    }
}
