package ouccs.smy.paiclilearn.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 教学演示：ReAct 循环的输入→转换→输出全流程。
 * <p>
 * 本测试使用 StubLlmClient 模拟 LLM 分两轮返回
 * （第一轮：工具调用；第二轮：最终回答），
 * 让学习者直观看到：
 * <ol>
 *   <li><b>输入</b>：构造 Agent + ToolRegistry + 用户查询</li>
 *   <li><b>转换</b>：Agent.run() 内部经历的 ReAct 循环——
 *       user message → LLM chat → tool call → execute tool →
 *       tool result → LLM chat → final answer</li>
 *   <li><b>输出</b>：conversationHistory 中的消息序列</li>
 * </ol>
 * </p>
 */
class ReActDemoTest {

    @TempDir
    Path tempDir;

    /**
     * 演示完整的 ReAct 循环：工具调用 → 回灌 → 最终回答。
     * <p>
     * 模拟场景：LLM 先要求读取一个文件，然后基于文件内容回答问题。</p>
     */
    @Test
    void demoReActLoop() throws IOException {
        System.out.println("================ ReAct 循环演示 ================");

        // ========== 1. 输入 ==========
        System.out.println("【1. 输入】构造 Agent + ToolRegistry + 用户查询");

        var stub = new DemoReActLlmClient();
        var toolRegistry = new ToolRegistry();
        Files.writeString(tempDir.resolve("test.txt"), "Hello World");
        toolRegistry.setProjectPath(tempDir.toString());
        var agent = new Agent(stub, toolRegistry);

        LlmClient.Tool readFile = toolRegistry.getToolDefinitions().stream()
                .filter(tool -> "read_file".equals(tool.name()))
                .findFirst()
                .orElseThrow();
        System.out.println("  真实工具 schema: " + readFile.name() + " - " + readFile.parameters());
        System.out.println("  等待 llmClient.chat() 返回工具调用...\n");

        // 注册流式监听器
        var contentReceived = new StringBuilder();
        agent.setStreamListener(new LlmClient.StreamListener() {
            @Override
            public void onContentDelta(String delta) {
                contentReceived.append(delta);
                System.out.print(delta);
                System.out.flush();
            }

            @Override
            public void onReasoningDelta(String delta) {
                System.out.print("\uD83E\uDDE0 " + delta);
                System.out.flush();
            }
        });

        // ========== 2. 转换 ==========
        System.out.println("【2. 转换】Agent.run() 内部 ReAct 循环");
        System.out.println("  (第一轮: LLM 返回 tool call → 执行工具 → 回灌结果)");
        System.out.println("  (第二轮: LLM 返回最终回答)");

        String result = agent.run("请读取 test.txt 的内容并告诉我");

        // ========== 3. 输出 ==========
        System.out.println("\n【3. 输出】");

        // 3a. conversationHistory 消息序列
        System.out.println("  对话历史消息序列:");
        List<LlmClient.Message> history = agent.getConversationHistory();
        for (int i = 0; i < history.size(); i++) {
            LlmClient.Message msg = history.get(i);
            System.out.printf("    [%d] role=%-10s content=%-30s toolCalls=%s%n",
                    i, msg.role(),
                    truncate(msg.content(), 28),
                    msg.toolCalls() != null ? msg.toolCalls().size() + "个" : "无");
        }

        // 3b. 验证结构：system → user → assistant(tool_call) → tool → user(第二轮) → assistant(content)
        assertTrue(history.size() >= 5,
                "ReAct 循环后应至少有 system + user + assistant(tool) + tool + assistant(content), 实际: " + history.size());
        assertEquals("system", history.get(0).role());
        assertEquals("user", history.get(1).role());
        assertEquals("assistant", history.get(2).role(), "第三条应为 assistant (含 tool calls)");

        // 3c. 验证最终回答
        if (!result.isEmpty()) {
            System.out.println("  最终回答: " + result);
        }
        if (contentReceived.length() > 0) {
            System.out.println("  流式内容: " + contentReceived);
        }

        // 验证至少有一条 tool 消息
        boolean hasToolMsg = history.stream().anyMatch(m -> "tool".equals(m.role()));
        assertTrue(hasToolMsg, "ReAct 循环应产生至少一条 tool 消息");
        assertTrue(history.stream()
                        .filter(message -> "tool".equals(message.role()))
                        .anyMatch(message -> message.content().contains("Hello World")),
                "read_file 的真实文件内容应回灌到 tool 消息");

        System.out.println("================ 演示结束 ================");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ========== DemoReActLlmClient ==========

    /**
     * 模拟一个两轮 ReAct 循环的 LLM：
     * 第一轮返回 read_file 工具调用，第二轮返回基于文件内容的最终回答。
     */
    static class DemoReActLlmClient implements LlmClient {
        private int callCount = 0;

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            callCount++;

            if (callCount == 1) {
                // ---- 第一轮：返回工具调用 ----
                String reasoning = "用户需要读取 test.txt 文件，我来调用 read_file 工具。";
                listener.onReasoningDelta(reasoning);

                var toolCall = new ToolCall("call_read_1",
                        new ToolCall.Function("read_file", "{\"path\":\"test.txt\"}"));

                String content = "我来读取这个文件。";
                return new ChatResponse("assistant", content, reasoning,
                        List.of(toolCall), 15, 20, 0);
            }

            // ---- 第二轮：返回最终回答 ----
            String reasoning = "文件内容显示 test.txt 包含'Hello World'。";
            listener.onReasoningDelta(reasoning);

            String content = "根据读取结果，test.txt 文件的内容是：Hello World！";
            listener.onContentDelta(content);

            return new ChatResponse("assistant", content, reasoning,
                    null, 20, 15, 0);
        }

        @Override
        public String getModelName() { return "demo-react-model"; }

        @Override
        public String getProviderName() { return "demo"; }
    }
}
