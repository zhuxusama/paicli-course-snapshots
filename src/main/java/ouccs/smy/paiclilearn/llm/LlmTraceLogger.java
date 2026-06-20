package ouccs.smy.paiclilearn.llm;

import org.slf4j.Logger;

/** 将完整 reasoning 写入诊断日志，而不记录请求中的 API Key。 */
public final class LlmTraceLogger {
    private LlmTraceLogger() {}
    public static void logReasoning(Logger log, String scope, LlmClient client, String reasoning) {
        if (log == null || reasoning == null || reasoning.isBlank()) return;
        String text = reasoning.replace("\r\n", "\n").replace('\r', '\n').trim();
        log.info("LLM reasoning [{}] provider={} model={} chars={}\n{}",
                scope == null || scope.isBlank() ? "unknown" : scope,
                client == null ? "unknown" : client.getProviderName(),
                client == null ? "unknown" : client.getModelName(), text.length(), text);
    }
}
