package ouccs.smy.paiclilearn.agent;

import org.junit.jupiter.api.Test;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Agent 内部 StreamRenderer 的流式输出行为。
 * <p>
 * 教学目的：理解 ReAct 循环中的流式回调机制——
 * reasoning delta 和 content delta 如何从 LLM 到达用户监听器，
 * 以及 hasStreamedOutput / resetBetweenIterations 对跨迭代状态的影响。</p>
 */
class AgentStreamRendererTest {

    /** 验证正常的 content 流式输出被转发到用户监听器。 */
    @Test
    void contentDeltaForwardedToUserListener() throws IOException {
        // 1. 输入：Agent + StepByStepLlmClient（分两次返回工具调用 + 内容）
        var stub = new ToolThenContentLlmClient();
        var agent = new Agent(stub);

        var received = new StringBuilder();
        agent.setStreamListener(new LlmClient.StreamListener() {
            @Override
            public void onContentDelta(String delta) {
                received.append(delta);
            }
        });

        // 2. 转换：执行 ReAct 循环
        String result = agent.run("1+1=?");

        // 3. 输出：验证最终回答
        System.out.println("收到的 content delta: " + received);
        System.out.println("最终结果: " + result);

        // 第一个工具调用应触发 ToolThenContentLlmClient 中的 tool call 分支
        // 第二次迭代返回 content
        // 总应该收到 content delta
        assertTrue(received.length() > 0 || result.contains("2"),
                "应收到最终回答内容");
    }

    /** 验证 StreamRenderer 正确跟踪 hasStreamedOutput 状态。 */
    @Test
    void hasStreamedOutputTracksContentDelta() throws IOException {
        var stub = new ContentOnlyLlmClient("hello world");
        var agent = new Agent(stub);

        // 不设置监听器 → NO_OP; StreamRenderer 仍会被 LLM client 调用 onContentDelta
        String result = agent.run("hi");

        // 由于 stub 调用了 listener.onContentDelta(), streamedOutput = true
        // Agent返回空字符串（内容已流式输出）
        assertEquals("", result,
                "流式输出后 Agent 应返回空字符串（内容已由 listener 消费）");
    }

    /** 验证监听器的 onReasoningDelta 确实被调用。 */
    @Test
    void reasoningDeltaFiresOnUserListener() throws IOException {
        var stub = new ContentOnlyLlmClient("answer");
        var agent = new Agent(stub);

        var reasoningReceived = new StringBuilder();
        agent.setStreamListener(new LlmClient.StreamListener() {
            @Override
            public void onReasoningDelta(String delta) {
                reasoningReceived.append(delta);
            }
        });

        agent.run("hi");
        // 当前 ContentOnlyLlmClient 不产生 reasoning
        assertEquals(0, reasoningReceived.length(),
                "ContentOnlyLlmClient 不产生 reasoning");
    }

    /** 验证 reasoning delta 被转发。 */
    @Test
    void reasoningDeltaForwarded() throws IOException {
        var stub = new ReasoningLlmClient();
        var agent = new Agent(stub);

        var reasoningReceived = new StringBuilder();
        agent.setStreamListener(new LlmClient.StreamListener() {
            @Override
            public void onReasoningDelta(String delta) {
                reasoningReceived.append(delta);
            }
        });

        agent.run("思考一下");

        System.out.println("收到的 reasoning delta: " + reasoningReceived);
        assertTrue(reasoningReceived.length() > 0,
                "reasoning delta 应被转发到用户监听器");
    }

    // ========== Stub LLM Clients ==========

    /** 第一次调用返回工具调用，第二次返回最终回答。 */
    static class ToolThenContentLlmClient implements LlmClient {
        private int callCount = 0;

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            callCount++;
            if (callCount == 1) {
                // 第一次：返回工具调用（模拟 LLM 请求调用 read_file）
                var toolCall = new ToolCall("call_1",
                        new ToolCall.Function("read_file", "{\"path\":\"test.txt\"}"));
                listener.onContentDelta("让我查一下文件...");
                return new ChatResponse("assistant", "让我查一下文件",
                        "我需要查看文件", List.of(toolCall), 10, 15, 0);
            }
            // 第二次：返回最终回答
            String content = "最终回答：2";
            listener.onContentDelta("最终回答：2");
            return new ChatResponse("assistant", content, null, null, 10, 5, 0);
        }

        @Override
        public String getModelName() { return "tool-model"; }

        @Override
        public String getProviderName() { return "stub-tool"; }
    }

    /** 返回固定 content（无 tool call、无 reasoning）。 */
    static class ContentOnlyLlmClient implements LlmClient {
        private final String content;
        ContentOnlyLlmClient(String content) { this.content = content; }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            listener.onContentDelta(content);
            return new ChatResponse("assistant", content, null, null, 10, 10, 0);
        }

        @Override
        public String getModelName() { return "content-model"; }

        @Override
        public String getProviderName() { return "stub-content"; }
    }

    /** 返回带 reasoning 内容的响应。 */
    static class ReasoningLlmClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            listener.onReasoningDelta("让我逐步思考");
            listener.onReasoningDelta("这个问题很简单");
            String content = "答案是 42";
            listener.onContentDelta(content);
            return new ChatResponse("assistant", content,
                    "让我逐步思考这个问题很简单", null, 10, 10, 0);
        }

        @Override
        public String getModelName() { return "reasoning-model"; }

        @Override
        public String getProviderName() { return "stub-reasoning"; }
    }
}
