package ouccs.smy.paiclilearn.llm;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 展示工具 schema 如何经过 SSE 增量转换为完整 ToolCall。 */
class ToolCallStreamingDemoTest {
    @Test
    void demonstratesToolCallDataFlow() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody("""
                    data: {"choices":[{"delta":{"reasoning":"需要读取文件。","tool_calls":[{"index":0,"id":"call-demo","function":{"name":"read_","arguments":"{\\\"path\\\":"}}]}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"file","arguments":"\\\"README.md\\\"}"}}]}}]}

                    data: [DONE]

                    """));
            server.start();
            LlmClient.Tool input = new LlmClient.Tool("read_file", "读取项目文件",
                    JsonNodeFactory.instance.objectNode().put("type", "object"));
            System.out.println("输入工具 schema: " + input);

            OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                    new LlmConfig("openai-compatible", "demo-key", "demo-model", server.url("/chat").toString()),
                    new OkHttpClient());
            LlmClient.ChatResponse output = client.chat(List.of(LlmClient.Message.user("读取 README")),
                    List.of(input), new LlmClient.StreamListener() {
                        @Override public void onReasoningDelta(String delta) {
                            System.out.println("转换中的 reasoning 增量: " + delta);
                        }
                    });

            System.out.println("输出响应: " + output);
            System.out.println("输出 ToolCall: " + output.toolCalls().get(0));
            assertEquals("read_file", output.toolCalls().get(0).function().name());
            assertEquals("{\"path\":\"README.md\"}", output.toolCalls().get(0).function().arguments());
        }
    }
}
