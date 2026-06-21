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
     * 模拟场景：LLM 先要求列出真实临时目录，然后基于工具结果回答问题。</p>
     */
    @Test
    void demoReActLoop() throws IOException {
        System.out.println("================ ReAct 循环演示 ================");

        // ========== 1. 输入 ==========
        System.out.println("【1. 输入】构造 Agent + ToolRegistry + 用户查询");

        var stub = new DemoReActLlmClient();
        var toolRegistry = new ToolRegistry();
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Files.createDirectory(tempDir.resolve("src"));
        toolRegistry.setProjectPath(tempDir.toString());
        var agent = new Agent(stub, toolRegistry);

        LlmClient.Tool listDir = toolRegistry.getToolDefinitions().get(0);
        System.out.println("  真实工具 schema: " + listDir.name() + " - " + listDir.parameters());
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

        String result = agent.run("请列出项目根目录并告诉我有哪些内容");

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
        assertTrue(history.stream()
                .filter(message -> "tool".equals(message.role()))
                .anyMatch(message -> message.content().contains("pom.xml")
                        && message.content().contains("src/")),
                "真实 list_dir 结果应回灌到 tool 消息");
        assertTrue(stub.receivedRealToolSchema,
                "LLM 第一轮必须收到生产 ToolRegistry 提供的 list_dir schema");

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

        System.out.println("================ 演示结束 ================");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ========== DemoReActLlmClient ==========

    /**
     * 模拟一个两轮 ReAct 循环的 LLM：
     * 第一轮返回 list_dir 工具调用，第二轮返回基于目录结果的最终回答。
     */
    static class DemoReActLlmClient implements LlmClient {
        private int callCount = 0;
        private boolean receivedRealToolSchema;

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            callCount++;

            if (callCount == 1) {
                receivedRealToolSchema = tools.size() == 1
                        && "list_dir".equals(tools.get(0).name())
                        && tools.get(0).parameters() != null;
                // ---- 第一轮：返回工具调用 ----
                String reasoning = "用户需要查看项目根目录，我来调用 list_dir 工具。";
                listener.onReasoningDelta(reasoning);

                var toolCall = new ToolCall("call_list_1",
                        new ToolCall.Function("list_dir", "{\"path\":\".\"}"));

                String content = "我来查看项目根目录。";
                return new ChatResponse("assistant", content, reasoning,
                        List.of(toolCall), 15, 20, 0);
            }

            // ---- 第二轮：返回最终回答 ----
            String reasoning = "目录工具结果中包含 pom.xml 和 src 目录。";
            listener.onReasoningDelta(reasoning);

            String content = "项目根目录包含 pom.xml 和 src/。";
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
