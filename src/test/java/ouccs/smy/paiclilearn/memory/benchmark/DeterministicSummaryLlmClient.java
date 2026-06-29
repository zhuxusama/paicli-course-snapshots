package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 离线确定性摘要客户端：只读取生产 compactor 实际提交的摘要请求，
 * 按通用的目标、约束、命令、错误和待办关键词抽取证据行。
 */
final class DeterministicSummaryLlmClient implements LlmClient {
    private static final Pattern IMPORTANT = Pattern.compile(
            "(?i)(目标|要求|允许|禁止|必须|记住|提交|备份|验证|运行|错误|报错|失败|待办|"
                    + "git|driver|tag|MODEL_|HTTP|D\\d{3}|\\d+万|market-[a-z0-9-]+)");

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
        String input = messages == null ? "" : messages.stream()
                .map(Message::content)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + "\n" + right)
                .replace("\\n", "\n");
        Set<String> selected = new LinkedHashSet<>();
        for (String raw : input.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || !IMPORTANT.matcher(line).find()) continue;
            selected.add(line.length() > 600 ? line.substring(0, 600) + "…" : line);
        }
        List<String> stable = new ArrayList<>(selected);
        String summary = stable.isEmpty() ? "未抽取到关键事实" : String.join("\n", stable);
        return new ChatResponse("assistant", summary, null, null,
                Math.max(1, input.length() / 4), Math.max(1, summary.length() / 4), 0);
    }

    @Override public String getModelName() { return "deterministic-extractive-v1"; }
    @Override public String getProviderName() { return "offline"; }
}
