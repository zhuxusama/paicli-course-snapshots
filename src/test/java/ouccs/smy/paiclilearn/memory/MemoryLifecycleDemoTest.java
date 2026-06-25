package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 教学演示：保存、重载、检索、注入和删除的完整生命周期。 */
class MemoryLifecycleDemoTest {
    @TempDir Path tempDir;

    @Test void demoPersistentMemoryLifecycle() {
        var stubClient = new LlmClient() {
            @Override public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) { return new ChatResponse("assistant", "OK", null, null, 0, 0, 0); }
            @Override public String getModelName() { return "test"; }
            @Override public String getProviderName() { return "test"; }
        };
        Path file = tempDir.resolve("long-term.json");
        var firstRun = new MemoryManager(stubClient, new LongTermMemory(file), tempDir.toString());
        MemoryEntry saved = firstRun.saveFact("项目构建必须使用 Java 17", "project");
        System.out.println("【保存】" + saved);

        var restarted = new MemoryManager(stubClient, new LongTermMemory(file), tempDir.toString());
        System.out.println("【重启加载】条目数=" + restarted.listLongTerm().size());
        String context = restarted.buildContextForQuery("Java 构建版本", 100);
        System.out.println("【检索并注入】\n" + context);
        assertTrue(context.contains("Java 17"));

        assertTrue(restarted.deleteLongTerm(saved.id()));
        System.out.println("【删除】剩余=" + restarted.listLongTerm().size());
        assertTrue(new LongTermMemory(file).getAll().isEmpty());
    }
}
