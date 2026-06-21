package ouccs.smy.paiclilearn.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ouccs.smy.paiclilearn.llm.LlmClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * s03 的最小真实工具注册中心。
 * <p>本章只注册无副作用的 {@code list_dir}，目的不是提前实现完整工具集，
 * 而是让生产 Agent 真正经历 schema 下发、tool call、工具执行和结果回灌。
 * s04 会在同一边界上补齐 read_file、glob_files 和 grep_code。</p>
 *
 * @since s03
 */
public class ToolRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Path projectPath = Path.of("").toAbsolutePath().normalize();

    /** 一次工具调用的输入。 */
    public record ToolInvocation(String id, String name, String arguments) {}

    /** 一次工具调用的执行结果。 */
    public record ToolExecutionResult(String id, String name, String output) {}

    /** 设置目录工具允许访问的项目根。 */
    public void setProjectPath(String projectPath) {
        this.projectPath = Path.of(projectPath).toAbsolutePath().normalize();
    }

    /** 返回本章唯一的真实工具 schema。 */
    public List<LlmClient.Tool> getToolDefinitions() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("path")
                .put("type", "string")
                .put("description", "相对于项目根的目录路径，使用 . 表示项目根");
        parameters.putArray("required").add("path");

        return List.of(new LlmClient.Tool(
                "list_dir",
                "列出项目根内指定目录的直接子项。目录名以 / 结尾。",
                parameters));
    }

    /** 将模型返回的 tool call 转成执行输入。 */
    public List<ToolInvocation> convertToolCalls(List<LlmClient.ToolCall> calls) {
        return calls.stream()
                .map(tc -> new ToolInvocation(tc.id(), tc.function().name(), tc.function().arguments()))
                .toList();
    }

    /** 同步执行本轮工具调用；并行执行在 s13 引入。 */
    public List<ToolExecutionResult> executeTools(List<ToolInvocation> invocations) {
        return invocations.stream().map(this::executeTool).toList();
    }

    private ToolExecutionResult executeTool(ToolInvocation invocation) {
        if (!"list_dir".equals(invocation.name())) {
            return new ToolExecutionResult(invocation.id(), invocation.name(),
                    "错误: 工具 '" + invocation.name() + "' 未注册");
        }

        try {
            JsonNode arguments = MAPPER.readTree(invocation.arguments());
            String relativePath = arguments.path("path").asText("");
            if (relativePath.isBlank()) {
                return new ToolExecutionResult(invocation.id(), invocation.name(),
                        "错误: 缺少必填参数 path");
            }

            Path directory = projectPath.resolve(relativePath).normalize();
            if (!directory.startsWith(projectPath)) {
                return new ToolExecutionResult(invocation.id(), invocation.name(),
                        "错误: 路径超出项目根");
            }
            if (!Files.isDirectory(directory)) {
                return new ToolExecutionResult(invocation.id(), invocation.name(),
                        "错误: 目录不存在: " + relativePath);
            }

            try (var entries = Files.list(directory)) {
                String output = entries
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(path -> path.getFileName() + (Files.isDirectory(path) ? "/" : ""))
                        .reduce((left, right) -> left + System.lineSeparator() + right)
                        .orElse("(空目录)");
                return new ToolExecutionResult(invocation.id(), invocation.name(), output);
            }
        } catch (Exception e) {
            return new ToolExecutionResult(invocation.id(), invocation.name(),
                    "错误: " + e.getMessage());
        }
    }
}
