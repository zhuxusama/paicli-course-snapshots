package ouccs.smy.paiclilearn.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

/**
 * 统一模型客户端边界，承载消息、工具定义、流式事件和 token 统计。
 */
public interface LlmClient {
    default ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
        return chat(messages, tools, StreamListener.NO_OP);
    }

    ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException;

    String getModelName();
    String getProviderName();

    default int maxContextWindow() { return 128_000; }
    default boolean supportsPromptCaching() { return false; }
    default String promptCacheMode() { return "none"; }

    /** 一条可进入请求历史的消息。 */
    record Message(String role, String content, String reasoningContent,
                   List<ToolCall> toolCalls, String toolCallId) {
        public Message(String role, String content) { this(role, content, null, null, null); }
        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
        public static Message assistant(String reasoning, String content, List<ToolCall> calls) {
            return new Message("assistant", content, reasoning, calls, null);
        }
        public static Message tool(String callId, String content) {
            return new Message("tool", content, null, null, callId);
        }
    }

    /** 模型发起的一次函数调用。 */
    record ToolCall(String id, Function function) {
        /** 函数名和逐段拼装后的 JSON 参数。 */
        public record Function(String name, String arguments) {}
    }

    /** 发送给模型的函数工具定义。 */
    record Tool(String name, String description, JsonNode parameters) {}

    /** 流式正文和推理增量监听器。 */
    interface StreamListener {
        StreamListener NO_OP = new StreamListener() {};
        default void onReasoningDelta(String delta) {}
        default void onContentDelta(String delta) {}
    }

    /** 聚合后的完整模型响应。 */
    record ChatResponse(String role, String content, String reasoningContent,
                        List<ToolCall> toolCalls, int inputTokens,
                        int outputTokens, int cachedInputTokens) {
        public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    }
}
