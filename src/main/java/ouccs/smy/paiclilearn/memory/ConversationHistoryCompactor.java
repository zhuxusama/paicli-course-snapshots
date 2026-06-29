package ouccs.smy.paiclilearn.memory;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 对话历史压缩器：当对话即将超过模型窗口时，把中间轮次的
 * user/assistant/tool 消息摘要成一条压缩消息，保留最近的原始消息。
 * <p>
 * [s09 新增] 压缩策略：
 * <ol>
 *   <li>找到最早的几条完整 user→assistant（→tool→assistant）轮次</li>
 *   <li>调用真实 LLM 生成摘要</li>
 *   <li>用一条 user("[已压缩]" + 摘要) 替换被压缩的消息</li>
 *   <li>保留最近 N 轮原始消息</li>
 * </ol>
 * </p>
 *
 * @since s09
 */
public class ConversationHistoryCompactor {
    private static final Logger log = LoggerFactory.getLogger(ConversationHistoryCompactor.class);
    private static final int MAX_SUMMARY_INPUT_CHARS = 60_000;
    private static final String SUMMARY_PROMPT = """
            请把下面的旧对话压缩成可供后续任务继续使用的摘要，保留：
            1. 用户目标、数值目标和明确约束
            2. 已达成的决策、允许/禁止事项
            3. 已执行的关键命令、工具、文件和核心结果
            4. 错误、失败原因、验证结论和仍未完成的待办

            不要复述无关闲聊，不要虚构未出现的信息。输出简洁中文摘要。

            === 待压缩历史（已按角色有界采样）===
            %s
            === 待压缩历史结束 ===
            """;
    private LlmClient llmClient;
    private final int retainRecentRounds;

    /** [s09 新增] 模型热切换时更新 LLM 客户端。 */
    public void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ConversationHistoryCompactor(LlmClient llmClient) {
        this(llmClient, 3);
    }

    public ConversationHistoryCompactor(LlmClient llmClient, int retainRecentRounds) {
        this.llmClient = llmClient;
        this.retainRecentRounds = Math.max(1, retainRecentRounds);
    }

    /**
     * 检查并压缩历史。
     * @param history 可变对话历史列表
     * @param triggerTokens 触发压缩的 token 阈值
     * @return true 表示执行了压缩
     */
    public boolean compactIfNeeded(List<LlmClient.Message> history, int triggerTokens) {
        if (history == null || history.size() < 3) return false;
        int estimated = TokenBudget.estimateMessagesTokens(history);
        if (estimated < triggerTokens) return false;

        // 找到可以压缩的用户消息边界
        int compressEnd = findCompressBoundary(history);
        if (compressEnd < 3) return false;

        LlmClient.Message system = "system".equals(history.get(0).role()) ? history.get(0) : null;
        int compressStart = system == null ? 0 : 1;
        // 只压缩 system 之后、切点之前的旧消息；system 必须原样放回第一位。
        List<LlmClient.Message> toCompress = new ArrayList<>(history.subList(compressStart, compressEnd));
        List<LlmClient.Message> tail = new ArrayList<>(history.subList(compressEnd, history.size()));
        if (toCompress.isEmpty()) return false;

        try {
            String summary = summarize(toCompress);
            if (summary == null || summary.isBlank()) return false;
            history.clear();
            if (system != null) history.add(system);
            history.add(LlmClient.Message.user("[已压缩] " + summary.trim()));
            // 尾部 + 辅助确认
            history.add(LlmClient.Message.assistant("好的，已理解前面的对话摘要。"));
            history.addAll(tail);
            int afterTokens = TokenBudget.estimateMessagesTokens(history);
            log.info("conversationHistory 压缩完成: tokens {} -> {}, messages {} -> {}, summaryChars={}",
                    estimated, afterTokens, toCompress.size() + tail.size() + (system == null ? 0 : 1),
                    history.size(), summary.length());
            return true;
        } catch (Exception e) {
            log.warn("conversationHistory 摘要失败，本轮保留原历史", e);
            return false;
        }
    }

    /** 找到最早的轮次边界——以 user 消息为分割点。 */
    private int findCompressBoundary(List<LlmClient.Message> history) {
        if (history.size() < 3) return history.size();
        // 从后往前找用户消息，保留 retainRecentRounds 轮
        int userCount = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) userCount++;
            if (userCount >= retainRecentRounds) return i;
        }
        // 如果保留轮次不够，至少保留一条
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) return i;
        }
        return history.size() - 1;
    }

    /** 调用真实 LLM 生成摘要。 */
    protected String summarize(List<LlmClient.Message> messages) throws IOException {
        if (llmClient == null) throw new IOException("LLM client not configured");
        String text = buildBalancedSummaryInput(messages);

        var response = llmClient.chat(
                List.of(
                        LlmClient.Message.system("你是对话压缩助手，只输出忠实摘要。"),
                        LlmClient.Message.user(String.format(SUMMARY_PROMPT, text))
                ),
                List.of()
        );
        return response.content() != null ? response.content() : "";
    }

    /**
     * 在固定 60k 字符预算内覆盖整段旧历史，而不是只保留开头。
     * user 权重最高，assistant 次之，tool 结果最低；每条长消息同时采样头尾。
     */
    private static String buildBalancedSummaryInput(List<LlmClient.Message> messages) {
        if (messages.isEmpty()) return "";
        int overhead = messages.stream().mapToInt(msg -> msg.role().length() + 5).sum();
        int contentBudget = Math.max(messages.size(), MAX_SUMMARY_INPUT_CHARS - overhead);
        int totalWeight = messages.stream().mapToInt(ConversationHistoryCompactor::weight).sum();
        double unit = contentBudget / (double) Math.max(1, totalWeight);

        StringBuilder result = new StringBuilder(MAX_SUMMARY_INPUT_CHARS);
        for (LlmClient.Message message : messages) {
            String prefix = "[" + message.role().toUpperCase(Locale.ROOT) + "] ";
            int remaining = MAX_SUMMARY_INPUT_CHARS - result.length();
            if (remaining <= prefix.length() + 1) break;
            int allocated = Math.max(1, (int) Math.floor(unit * weight(message)));
            allocated = Math.min(allocated, remaining - prefix.length() - 1);
            result.append(prefix).append(abbreviate(semanticText(message), allocated)).append('\n');
        }
        return result.toString();
    }

    private static int weight(LlmClient.Message message) {
        return switch (message.role()) {
            case "user" -> 4;
            case "system", "assistant" -> 2;
            case "tool" -> 1;
            default -> 1;
        };
    }

    private static String semanticText(LlmClient.Message message) {
        StringBuilder text = new StringBuilder();
        if (message.content() != null) text.append(message.content());
        if (message.toolCalls() != null) {
            for (LlmClient.ToolCall call : message.toolCalls()) {
                text.append(" TOOL_CALL ").append(call.function().name()).append(' ')
                        .append(call.function().arguments());
            }
        }
        if (message.toolCallId() != null) text.append(" TOOL_RESULT_FOR ").append(message.toolCallId());
        return text.toString().replaceAll("\\s+", " ").trim();
    }

    private static String abbreviate(String text, int budget) {
        if (text.length() <= budget) return text;
        if (budget <= 5) return text.substring(0, Math.max(0, budget));
        int head = (budget - 1) / 2;
        int tail = budget - head - 1;
        return text.substring(0, head) + "…" + text.substring(text.length() - tail);
    }
}
