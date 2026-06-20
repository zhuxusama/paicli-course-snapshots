package ouccs.smy.paiclilearn.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningHistoryTest {
    @Test
    void preservesReasoningForThinkingProvidersOnly() {
        LlmClient.Message assistant = LlmClient.Message.assistant("内部推理", "", List.of(
                new LlmClient.ToolCall("call-1", new LlmClient.ToolCall.Function("read_file", "{}"))));

        ObjectNode deepSeekBody = new DeepSeekClient("key", null, null).buildRequestBody(List.of(assistant), List.of());
        ObjectNode glmBody = new GLMClient("key", null, null).buildRequestBody(List.of(assistant), List.of());

        assertTrue(deepSeekBody.toString().contains("reasoning_content"));
        assertFalse(glmBody.toString().contains("reasoning_content"));
    }
}
