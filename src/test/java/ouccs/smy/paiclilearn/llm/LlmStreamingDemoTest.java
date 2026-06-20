package ouccs.smy.paiclilearn.llm;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 演示一条消息如何经过真实 HTTP/SSE 客户端变成流式响应。
 */
class LlmStreamingDemoTest {

    @Test
    void demonstratesInputTransformationAndOutput() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"content":"Pai"}}]}

                            data: {"choices":[{"delta":{"content":"CLI"}}],"usage":{"prompt_tokens":8,"completion_tokens":2}}

                            data: [DONE]

                            """));
            server.start();

            LlmConfig inputConfig = new LlmConfig(
                    server.url("/v1/chat/completions").toString(),
                    "demo-key",
                    "demo-model"
            );
            List<LlmClient.Message> inputMessages = List.of(
                    LlmClient.Message.user("介绍 PaiCLI")
            );
            System.out.println("输入配置: " + inputConfig);
            System.out.println("输入消息: " + inputMessages);

            StringBuilder streamed = new StringBuilder();
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(inputConfig, new OkHttpClient());
            LlmClient.ChatResponse output = client.chat(inputMessages, new LlmClient.StreamListener() {
                @Override
                public void onContentDelta(String delta) {
                    streamed.append(delta);
                    System.out.println("转换中的 SSE 增量: " + delta);
                }
            });

            System.out.println("输出响应: " + output);
            assertEquals("PaiCLI", streamed.toString());
            assertEquals("PaiCLI", output.content());
            assertEquals(8, output.inputTokens());
            assertEquals(2, output.outputTokens());
        }
    }
}
