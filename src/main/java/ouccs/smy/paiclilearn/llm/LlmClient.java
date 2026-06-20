package ouccs.smy.paiclilearn.llm;

import java.io.IOException;
import java.util.List;

/**
 * LLM 客户端抽象接口。
 * <p>
 * 第一章只接入一个 OpenAI-compatible 客户端，但先保留接口边界，
 * 让后续章节可以在不改 CLI 的前提下增加更多 provider。
 */
public interface LlmClient {

    /**
     * 发送聊天请求，支持流式输出。
     *
     * @param messages 对话消息列表
     * @param listener 流式事件监听器，可为 {@link StreamListener#NO_OP} 以禁用流式回调
     * @return 包含完整回复内容和 token 用量的响应对象
     * @throws IOException 网络或协议异常
     */
    ChatResponse chat(List<Message> messages, StreamListener listener) throws IOException;

    /**
     * 同步聊天便捷方法，不监听流式增量。
     *
     * @param messages 对话消息列表
     * @return 包含完整回复内容和 token 用量的响应对象
     * @throws IOException 网络或协议异常
     */
    default ChatResponse chat(List<Message> messages) throws IOException {
        return chat(messages, StreamListener.NO_OP);
    }

    /**
     * 单条对话消息。
     *
     * @param role    角色：system / user / assistant
     * @param content 消息文本内容
     */
    record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    /**
     * LLM 聊天响应。
     *
     * @param content          助手正文回复
     * @param inputTokens      输入 token 数（prompt_tokens）
     * @param outputTokens     输出 token 数（completion_tokens）
     */
    record ChatResponse(String content, int inputTokens, int outputTokens) {
    }

    /**
     * 流式 SSE 事件监听器。
     * <p>
     * 实现类可按需覆写以收集增量片段、实时渲染等。
     */
    interface StreamListener {

        StreamListener NO_OP = new StreamListener() {
        };

        /**
         * 收到正文内容增量。
         */
        default void onContentDelta(String delta) {
        }

    }
}
