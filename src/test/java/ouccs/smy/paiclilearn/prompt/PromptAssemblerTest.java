package ouccs.smy.paiclilearn.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ouccs.smy.paiclilearn.agent.Agent;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 prompt 分层、覆盖优先级和 Agent 生产接入。 */
class PromptAssemblerTest {

    @TempDir
    Path tempDir;

    @Test
    void assemblesAgentPromptInStableOrder() {
        PromptContext context = PromptContext.builder()
                .approvalMode("auto")
                .personality("calm")
                .currentDate(LocalDate.of(2026, 6, 21))
                .zoneId(ZoneId.of("Asia/Shanghai"))
                .build();

        String prompt = PromptAssembler.createDefault().assemble(PromptMode.AGENT, context);

        assertOrdered(prompt, "## Identity", "## Personality", "## Mode: ReAct Agent",
                "## Approval Mode", "## Runtime Context");
        assertTrue(prompt.contains("2026-06-21"));
        assertTrue(prompt.contains("Asia/Shanghai"));
        assertTrue(prompt.contains("偏自动执行"));
    }

    @Test
    void projectOverrideWinsOverUserAndBuiltin() throws Exception {
        Path user = tempDir.resolve("user");
        Path project = tempDir.resolve("project");
        Files.createDirectories(user.resolve("personalities"));
        Files.createDirectories(project.resolve("personalities"));
        Files.writeString(user.resolve("personalities/calm.md"), "## Personality\n\n用户覆盖");
        Files.writeString(project.resolve("personalities/calm.md"), "## Personality\n\n项目覆盖");

        PromptAssembler assembler = new PromptAssembler(new PromptRepository(user, project));
        String prompt = assembler.assemble(PromptMode.AGENT, fixedContext());

        assertTrue(prompt.contains("项目覆盖"));
        assertFalse(prompt.contains("用户覆盖"));
    }

    @Test
    void rejectsPathEscapeAndUnknownApprovalMode() {
        PromptRepository repository = new PromptRepository(
                tempDir.resolve("user"), tempDir.resolve("project"));

        assertThrows(IllegalArgumentException.class,
                () -> repository.loadRequired("../secret.md"));
        assertThrows(IllegalArgumentException.class,
                () -> PromptAssembler.createDefault().assemble(PromptMode.AGENT,
                        PromptContext.builder().approvalMode("unsafe").build()));
    }

    @Test
    void agentAndClearHistoryUseAssemblerOutput() throws Exception {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve("personalities"));
        Files.writeString(project.resolve("personalities/calm.md"),
                "## Personality\n\n来自 Agent 集成测试");
        PromptAssembler assembler = new PromptAssembler(new PromptRepository(
                tempDir.resolve("user"), project));

        Agent agent = new Agent(new NoOpLlmClient(), new ToolRegistry(), assembler, fixedContext());
        assertTrue(agent.getConversationHistory().get(0).content().contains("来自 Agent 集成测试"));

        agent.clearHistory();
        assertEquals(1, agent.getConversationHistory().size());
        assertTrue(agent.getConversationHistory().get(0).content().contains("来自 Agent 集成测试"));
    }

    private static PromptContext fixedContext() {
        return PromptContext.builder()
                .currentDate(LocalDate.of(2026, 6, 21))
                .zoneId(ZoneId.of("Asia/Shanghai"))
                .build();
    }

    private static void assertOrdered(String text, String... sections) {
        int previous = -1;
        for (String section : sections) {
            int current = text.indexOf(section);
            assertTrue(current > previous, section + " 应按设计顺序出现");
            previous = current;
        }
    }

    private static final class NoOpLlmClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return new ChatResponse("assistant", "", null, null, 0, 0, 0);
        }

        @Override public String getModelName() { return "prompt-test"; }
        @Override public String getProviderName() { return "test"; }
    }
}
