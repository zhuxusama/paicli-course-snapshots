package ouccs.smy.paiclilearn.memory;

import org.junit.jupiter.api.Test;
import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** [s09 新增] 验证短期记忆压缩会真实调用 LLM，并把摘要写回 memory。 */
class ContextCompressorTest {
    @Test void skipsCompressionWhenEntriesAreFew() throws Exception {
        var memory = new ConversationMemory(20);
        memory.store(MemoryEntry.message("用户问题", MemoryEntry.MemoryType.USER, "p"));
        memory.store(MemoryEntry.message("助手回答", MemoryEntry.MemoryType.ASSISTANT, "p"));

        var client = new CountingLlmClient("摘要");
        var compressor = new ContextCompressor(client, 3);

        assertNull(compressor.compress(memory));
        assertEquals(0, client.calls);
        assertEquals(2, memory.size());
    }

    @Test void compressesOldEntriesAndKeepsRecentEntries() throws Exception {
        var memory = new ConversationMemory(20);
        for (int i = 1; i <= 8; i++) {
            memory.store(MemoryEntry.message("消息 " + i, MemoryEntry.MemoryType.USER, "p"));
        }

        var client = new CountingLlmClient("用户确认使用真实 LLM 客户端");
        var compressor = new ContextCompressor(client, 3);
        String summary = compressor.compress(memory);

        assertEquals("用户确认使用真实 LLM 客户端", summary);
        assertEquals(1, client.calls);
        assertEquals(4, memory.size());

        List<MemoryEntry> entries = memory.getAll();
        assertEquals(MemoryEntry.MemoryType.SUMMARY, entries.get(0).type());
        assertTrue(entries.get(0).content().contains("真实 LLM 客户端"));
        assertEquals("消息 6", entries.get(1).content());
        assertEquals("消息 8", entries.get(3).content());
    }

    private static final class CountingLlmClient implements LlmClient {
        private final String response;
        private int calls;

        private CountingLlmClient(String response) {
            this.response = response;
        }

        @Override public ChatResponse chat(List<Message> messages, List<Tool> tools,
                                           StreamListener listener) throws IOException {
            calls++;
            assertEquals("system", messages.get(0).role());
            assertTrue(messages.get(1).content().contains("对话片段"));
            return new ChatResponse("assistant", response, null, null, 0, 0, 0);
        }

        @Override public String getModelName() { return "test-real-boundary"; }
        @Override public String getProviderName() { return "test"; }
    }
}
