package ouccs.smy.paiclilearn.memory;

import org.junit.jupiter.api.Test;
import ouccs.smy.paiclilearn.agent.Agent;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.prompt.PromptAssembler;
import ouccs.smy.paiclilearn.prompt.PromptContext;
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 MemoryManager 管理操作和 Agent prompt 注入。 */
class MemoryManagerTest {
    @Test void injectsRelevantFactAndClearKeepsLongTerm() throws Exception {
        var longTerm = LongTermMemory.inMemory();
        var manager = new MemoryManager(longTerm, ".");
        manager.saveFact("项目使用 Java 17", "project");
        var client = new CapturingClient();
        var agent = new Agent(client, new ToolRegistry(), PromptAssembler.createDefault(),
                PromptContext.empty(), manager);

        agent.run("这个 Java 项目使用什么版本？");
        assertTrue(client.systemPrompt.contains("项目使用 Java 17"));
        assertTrue(manager.shortTermSize() > 0);

        agent.clearHistory();
        assertEquals(0, manager.shortTermSize());
        assertEquals(1, manager.listLongTerm().size());
    }

    private static final class CapturingClient implements LlmClient {
        String systemPrompt;
        @Override public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            systemPrompt = messages.get(0).content();
            return new ChatResponse("assistant", "Java 17", null, null, 0, 0, 0);
        }
        @Override public String getModelName() { return "memory-test"; }
        @Override public String getProviderName() { return "test"; }
    }
}
