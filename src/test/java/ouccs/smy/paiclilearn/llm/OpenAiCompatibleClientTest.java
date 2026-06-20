package ouccs.smy.paiclilearn.llm;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class OpenAiCompatibleClientTest {

    @Test
    void parsesContentAndReasoningFromSse() throws IOException, InterruptedException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"content":"你好"}}]}

                            data: {"choices":[{"delta":{"content":"，PaiCLI"}}],"usage":{"prompt_tokens":11,"completion_tokens":3}}

                            data: [DONE]

                            """));

            server.start();

            LlmConfig config = new LlmConfig(server.url("/v1/chat/completions").toString(), "test-key", "test-model");
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(config, new OkHttpClient());

            AtomicReference<String> streamedRef = new AtomicReference<>("");
            LlmClient.StreamListener listener = new LlmClient.StreamListener() {
                @Override
                public void onContentDelta(String delta) {
                    streamedRef.set(streamedRef.get() + delta);
                }
            };

            List<LlmClient.Message> messages = List.of(LlmClient.Message.user("hello"));
            LlmClient.ChatResponse response = client.chat(messages, listener);

            Assertions.assertEquals("你好，PaiCLI", response.content());
            Assertions.assertEquals("你好，PaiCLI", streamedRef.get());
            Assertions.assertEquals(11, response.inputTokens());
            Assertions.assertEquals(3, response.outputTokens());

            RecordedRequest request = server.takeRequest();
            String requestBody = request.getBody().readUtf8();
            Assertions.assertTrue(requestBody.contains("\"stream\":true"));
            Assertions.assertTrue(requestBody.contains("\"model\":\"test-model\""));
            Assertions.assertTrue(requestBody.contains("\"role\":\"user\""));
        }
    }
}
