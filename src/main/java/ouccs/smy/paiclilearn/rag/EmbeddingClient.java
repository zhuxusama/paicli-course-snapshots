package ouccs.smy.paiclilearn.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * s16: EmbeddingClient 是 RAG 链路进入向量世界的边界。
 * <p>
 * 它保留原项目的真实 provider 设计：默认可连 Ollama，也可以按 OpenAI 兼容协议请求
 * `/embeddings`。测试可以继承并覆盖 {@link #embed(String)}，但运行时代码默认不会返回假向量。
 */
public class EmbeddingClient {
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_INPUT_CHARS = 2000;

    private final String provider;
    private final String model;
    private final String baseUrl;
    private final String apiKey;

    public EmbeddingClient() {
        this(
                System.getenv().getOrDefault("EMBEDDING_PROVIDER", "ollama"),
                System.getenv().getOrDefault("EMBEDDING_MODEL", "nomic-embed-text"),
                System.getenv().getOrDefault("EMBEDDING_BASE_URL", "http://localhost:11434"),
                System.getenv("EMBEDDING_API_KEY")
        );
    }

    public EmbeddingClient(String provider, String model, String baseUrl, String apiKey) {
        this.provider = normalizeProvider(provider);
        this.model = model == null || model.isBlank() ? "nomic-embed-text" : model;
        this.baseUrl = trimTrailingSlash(baseUrl == null || baseUrl.isBlank()
                ? "http://localhost:11434"
                : baseUrl);
        this.apiKey = apiKey;
    }

    /**
     * 把代码块文本送到真实 embedding 服务。
     *
     * @param text 代码块、文件路径和符号名组合后的文本
     * @return provider 返回的浮点向量
     */
    public float[] embed(String text) throws IOException {
        String input = truncate(text == null ? "" : text);
        if (input.isBlank()) {
            return new float[0];
        }
        if ("ollama".equals(provider)) {
            return embedWithOllama(input);
        }
        return embedWithOpenAiCompatible(input);
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private float[] embedWithOllama(String input) throws IOException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.put("prompt", input);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/embeddings")
                .post(RequestBody.create(mapper.writeValueAsString(payload), JSON))
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String body = readSuccessfulBody(response);
            JsonNode embedding = mapper.readTree(body).path("embedding");
            return toFloatArray(embedding);
        }
    }

    private float[] embedWithOpenAiCompatible(String input) throws IOException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.put("input", input);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + "/embeddings")
                .post(RequestBody.create(mapper.writeValueAsString(payload), JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try (Response response = HTTP_CLIENT.newCall(builder.build()).execute()) {
            String body = readSuccessfulBody(response);
            JsonNode embedding = mapper.readTree(body).path("data").path(0).path("embedding");
            return toFloatArray(embedding);
        }
    }

    private String readSuccessfulBody(Response response) throws IOException {
        String body = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
            throw new IOException("Embedding 请求失败: HTTP " + response.code() + " " + body);
        }
        return body;
    }

    private float[] toFloatArray(JsonNode embedding) throws IOException {
        if (!embedding.isArray()) {
            throw new IOException("Embedding 响应缺少 embedding 数组");
        }
        List<Float> values = new ArrayList<>();
        for (JsonNode node : embedding) {
            values.add((float) node.asDouble());
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    static String truncate(String text) {
        return text.length() <= MAX_INPUT_CHARS ? text : text.substring(0, MAX_INPUT_CHARS);
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "ollama";
        }
        return provider.trim().toLowerCase();
    }

    private static String trimTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

}
