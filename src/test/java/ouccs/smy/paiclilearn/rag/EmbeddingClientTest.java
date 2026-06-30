package ouccs.smy.paiclilearn.rag;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingClientTest {

    @Test
    void parsesOllamaEmbeddingResponse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"embedding\":[0.1,0.2,0.3]}"));
            server.start();

            EmbeddingClient client = new EmbeddingClient(
                    "ollama", "nomic-embed-text", server.url("").toString(), null);

            float[] embedding = client.embed("public class UserService {}");

            assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, embedding, 0.0001f);
            assertEquals("/api/embeddings", server.takeRequest().getPath());
        }
    }

    @Test
    void parsesOpenAiCompatibleEmbeddingResponseAndSendsBearerToken() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"data\":[{\"embedding\":[0.7,0.8]}]}"));
            server.start();

            EmbeddingClient client = new EmbeddingClient(
                    "openai", "text-embedding-3-small", server.url("").toString(), "secret");

            float[] embedding = client.embed("find user repository");

            assertArrayEquals(new float[]{0.7f, 0.8f}, embedding, 0.0001f);
            var request = server.takeRequest();
            assertEquals("/embeddings", request.getPath());
            assertEquals("Bearer secret", request.getHeader("Authorization"));
        }
    }

    @Test
    void truncatesLongInputBeforeSending() {
        String text = "x".repeat(2500);

        String truncated = EmbeddingClient.truncate(text);

        assertEquals(2000, truncated.length());
        assertTrue(text.startsWith(truncated));
    }
}
