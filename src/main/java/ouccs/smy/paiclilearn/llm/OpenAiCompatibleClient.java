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
 * 第一章使用 GLM 作为真实运行示例，兼容端点也可以通过三项环境变量配置。
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
                if (errorBody.length() > 1000) {
                    errorBody = errorBody.substring(0, 1000) + "...";
                }
                throw new IOException("LLM API 请求失败: HTTP " + response.code() + " - " + errorBody);
            }
            if (response.body() == null) {
                throw new IOException("LLM API 返回空响应体");
            }
            return readSse(response.body().source(), listener);
        }
    }

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
        int inputTokens = 0;
        int outputTokens = 0;

        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) {
                break;
            }

            line = line.trim();
            // SSE 空行分隔事件，以冒号开头的是服务端注释。
            if (line.isEmpty() || line.startsWith(":")) {
                continue;
            }

            // 第一章只消费聊天接口使用的 data 字段。
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring(5).trim();

            if ("[DONE]".equals(data)) {
                break;
            }

            JsonNode json;
            try {
                json = MAPPER.readTree(data);
            } catch (Exception e) {
                throw new IOException("无法解析 LLM SSE 数据: " + data, e);
            }

            JsonNode choice = json.path("choices").path(0);
            JsonNode deltaOrMessage = choice.has("delta")
                    ? choice.path("delta")
                    : choice.path("message");

            if (deltaOrMessage.has("content") && !deltaOrMessage.path("content").isNull()) {
                String contentDelta = deltaOrMessage.path("content").asText("");
                if (!contentDelta.isEmpty()) {
                    contentBuilder.append(contentDelta);
                    listener.onContentDelta(contentDelta);
                }
            }

            JsonNode usage = json.path("usage");
            if (usage.has("prompt_tokens")) {
                inputTokens = usage.path("prompt_tokens").asInt();
            }
            if (usage.has("completion_tokens")) {
                outputTokens = usage.path("completion_tokens").asInt();
            }
        }

        return new ChatResponse(
                contentBuilder.toString(),
                inputTokens,
                outputTokens
        );
    }

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

    private static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(600, TimeUnit.SECONDS)
                .build();
    }
}
