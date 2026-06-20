package ouccs.smy.paiclilearn.llm;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientTest {
    @Test
    void aggregatesReasoningContentAndFragmentedToolCall() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody("""
                    data: {"choices":[{"delta":{"role":"assistant","reasoning_content":"先定位。","tool_calls":[{"index":0,"id":"call-1","function":{"name":"read_","arguments":"{\\\"path\\\":"}}]}}]}

                    data: {"choices":[{"delta":{"content":"正在读取","tool_calls":[{"index":0,"function":{"name":"file","arguments":"\\\"pom.xml\\\"}"}}]}}]}

                    data: {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":5,"prompt_tokens_details":{"cached_tokens":4}}}

                    data: [DONE]

                    """));
            server.start();
            LlmConfig config = new LlmConfig("openai-compatible", "test-key", "test-model",
                    server.url("/v1/chat/completions").toString());
            OpenAiCompatibleClient client = new OpenAiCompatibleClient(config, new OkHttpClient());
            LlmClient.Tool tool = new LlmClient.Tool("read_file", "读取文件",
                    JsonNodeFactory.instance.objectNode().put("type", "object"));

            LlmClient.ChatResponse response = client.chat(List.of(LlmClient.Message.user("读取 pom")), List.of(tool));

            assertEquals("先定位。", response.reasoningContent());
            assertEquals("正在读取", response.content());
            assertEquals("read_file", response.toolCalls().get(0).function().name());
            assertEquals("{\"path\":\"pom.xml\"}", response.toolCalls().get(0).function().arguments());
            assertEquals(4, response.cachedInputTokens());
            String request = server.takeRequest().getBody().readUtf8();
            assertTrue(request.contains("\"tools\""));
            assertTrue(request.contains("\"read_file\""));
        }
    }
}
