package ouccs.smy.paiclilearn.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心——本章（s04）注册四个只读文件工具。
 * <p>
 * 本章新增工具（全部为只读，不修改文件系统）：
 * <ul>
 *   <li><b>read_file</b> — 读取文件内容，支持 offset/limit 分页</li>
 *   <li><b>list_dir</b> — 列出目录内容</li>
 *   <li><b>glob_files</b> — 按 glob 模式匹配文件路径</li>
 *   <li><b>grep_code</b> — 代码搜索（ripgrep 优先，Java 回退）</li>
 * </ul>
 * 副作用工具（write_file、execute_command 等）在 s05 加入。
 * 并行执行、超时和 MCP 工具在后续章节加入。</p>
 *
 * @since s04（从 s03 的单一 list_dir 工具扩展而来）
 */
public class ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "target", ".git", "node_modules", ".svn", "build", "dist");

    // ========== 类型定义 ==========

    /** 一次工具调用的输入。 */
    public record ToolInvocation(String id, String name, String argumentsJson) {}

    /** 一次工具调用的执行结果。 */
    public record ToolExecutionResult(
            String id, String name, String result,
            long elapsedMillis, boolean timedOut) {
        static ToolExecutionResult completed(ToolInvocation inv, String result, long elapsed) {
            return new ToolExecutionResult(inv.id(), inv.name(), result, elapsed, false);
        }
        static ToolExecutionResult failed(ToolInvocation inv, String message, long elapsed) {
            return new ToolExecutionResult(inv.id(), inv.name(),
                    "错误: " + message, elapsed, false);
        }
        static ToolExecutionResult timedOut(ToolInvocation inv, long timeoutSeconds) {
            return new ToolExecutionResult(inv.id(), inv.name(),
                    "错误: 工具执行超时 (" + timeoutSeconds + "s)", 0, true);
        }
    }

    /** 工具定义：名称、描述、参数 schema 和执行器。 */
    record Tool(String name, String description, JsonNode parameters, ToolExecutor executor) {}

    /** 工具执行器函数式接口：接收参数 Map，返回文本结果。 */
    @FunctionalInterface
    interface ToolExecutor {
        String execute(Map<String, String> args);
    }

    // ========== 状态 ==========

    /** 已注册的工具表（工具名 → Tool）。 */
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /** ripgrep 驱动的代码搜索引擎。 */
    private final CodeSearchEngine searchEngine;

    /** 项目根目录（所有路径操作的 sandbox 上限）。 */
    private Path projectPath;

    // ========== 构造 ==========

    public ToolRegistry() {
        this.searchEngine = new RipgrepCodeSearchEngine(EXCLUDED_DIRS);
        this.projectPath = Path.of("").toAbsolutePath();
        registerFileTools();
    }

    /**
     * 设置项目路径（所有文件操作的根目录）。
     * <p>测试中通过 {@code @TempDir} 设置，生产环境指向项目根。</p>
     */
    public void setProjectPath(String projectPath) {
        this.projectPath = Path.of(projectPath).toAbsolutePath().normalize();
    }

    /** 获取当前项目路径。 */
    public Path getProjectPath() {
        return projectPath;
    }

    // ========== 对外接口 ==========

    /** 返回当前注册的 LLM 工具定义列表。 */
    public List<LlmClient.Tool> getToolDefinitions() {
        return tools.values().stream()
                .map(t -> new LlmClient.Tool(t.name(), t.description(), t.parameters()))
                .toList();
    }

    /** 将 LLM 返回的 ToolCall 列表转换为 ToolInvocation。 */
    public List<ToolInvocation> convertToolCalls(List<LlmClient.ToolCall> calls) {
        return calls.stream()
                .map(tc -> new ToolInvocation(tc.id(), tc.function().name(), tc.function().arguments()))
                .toList();
    }

    /**
     * 单工具执行（供测试和 s03 兼容调用）。
     * <p>参数为 JSON 字符串，内部解析为 Map 后调执行器。</p>
     */
    public String executeTool(String name, String argumentsJson) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "错误: 工具 '" + name + "' 未注册";
        }
        try {
            Map<String, String> args = parseArgs(argumentsJson);
            return tool.executor().execute(args);
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 批量执行工具调用（同步顺序执行，并行在 s13 引入）。
     */
    public List<ToolExecutionResult> executeTools(List<ToolInvocation> invocations) {
        List<ToolExecutionResult> results = new ArrayList<>();
        for (ToolInvocation inv : invocations) {
            long start = System.currentTimeMillis();
            Tool tool = tools.get(inv.name());
            if (tool == null) {
                results.add(ToolExecutionResult.failed(inv,
                        "工具 '" + inv.name() + "' 未注册",
                        System.currentTimeMillis() - start));
                continue;
            }
            try {
                Map<String, String> args = parseArgs(inv.argumentsJson());
                String result = tool.executor().execute(args);
                results.add(ToolExecutionResult.completed(inv, result,
                        System.currentTimeMillis() - start));
            } catch (Exception e) {
                results.add(ToolExecutionResult.failed(inv, e.getMessage(),
                        System.currentTimeMillis() - start));
            }
        }
        return results;
    }

    // ========== 工具注册（只读文件工具） ==========

    /** 注册所有只读文件工具。s05 在此之后注册副作用工具。 */
    private void registerFileTools() {
        // ---- read_file ----
        tools.put("read_file", new Tool(
                "read_file",
                "读取文件全部或指定行段。offset 从 1 开始；limit 控制最大返回行数。",
                createParameters(
                        param("path", "string", "文件路径（相对于项目根）", true),
                        param("offset", "integer", "起始行号（1-based，默认 1）", false),
                        param("limit", "integer", "最大返回行数（默认 2000）", false)
                ),
                args -> {
                    Path file = resolvePath(args.get("path"));
                    int offset = parseInt(args.get("offset"), 1);
                    int limit = parseInt(args.get("limit"), 2000);
                    return readFileLines(file, offset, limit);
                }
        ));

        // ---- list_dir ----
        tools.put("list_dir", new Tool(
                "list_dir",
                "列出目录内容。目录名后加 /；文件显示大小。",
                createParameters(
                        param("path", "string", "目录路径（相对于项目根）", true)
                ),
                args -> {
                    Path dir = resolvePath(args.get("path"));
                    return listDirectory(dir);
                }
        ));

        // ---- glob_files ----
        tools.put("glob_files", new Tool(
                "glob_files",
                "按 glob 模式匹配文件路径。支持 ** 递归匹配。",
                createParameters(
                        param("pattern", "string", "glob 模式，如 **/*.java", true),
                        param("path", "string", "搜索根目录（默认项目根）", false),
                        param("max_results", "integer", "最大结果数（默认 100）", false)
                ),
                args -> {
                    String pattern = args.get("pattern");
                    Path root = args.containsKey("path")
                            ? resolvePath(args.get("path")) : projectPath;
                    int maxResults = parseInt(args.get("max_results"), 100);
                    return globFiles(root, pattern, maxResults);
                }
        ));

        // ---- grep_code ----
        tools.put("grep_code", new Tool(
                "grep_code",
                "在项目文件中搜索文本模式。使用 ripgrep（优先）或 Java 回退。支持 glob 过滤、正则、上下文行和预算控制。",
                createParameters(
                        param("pattern", "string", "搜索模式（纯文本或正则）", true),
                        param("path", "string", "搜索根目录（默认项目根）", false),
                        param("glob", "string", "文件 glob 过滤，如 **/*.java", false),
                        param("regex", "boolean", "是否为正则搜索（默认 false：纯文本）", false),
                        param("case_sensitive", "boolean", "是否区分大小写（默认 false）", false),
                        param("context_lines", "integer", "每侧上下文行数（默认 1）", false),
                        param("max_results", "integer", "最大匹配数（默认 50）", false),
                        param("head_limit", "integer", "每文件最大匹配数（默认 3）", false)
                ),
                args -> {
                    CodeSearchRequest req = new CodeSearchRequest(
                            args.get("pattern"),
                            args.containsKey("path") ? resolvePath(args.get("path")) : projectPath,
                            projectPath,
                            args.get("glob"),
                            parseBool(args.get("regex")),
                            parseBool(args.get("case_sensitive")),
                            parseInt(args.get("context_lines"), 1),
                            parseInt(args.get("max_results"), 50),
                            parseInt(args.get("head_limit"), 3)
                    );
                    return formatGrepResult(searchEngine.search(req));
                }
        ));
    }

    // ========== 工具实现 ==========

    /** 读取文件指定行段。 */
    private String readFileLines(Path file, int offset, int limit) {
        if (!Files.exists(file)) {
            return "错误: 文件不存在: " + projectPath.relativize(file);
        }
        if (Files.isDirectory(file)) {
            return "错误: 路径是目录而非文件: " + projectPath.relativize(file);
        }
        try {
            List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int startIdx = Math.max(0, offset - 1);
            int endIdx = Math.min(allLines.size(), startIdx + limit);
            if (startIdx >= allLines.size()) {
                return "错误: offset " + offset + " 超出文件总行数 " + allLines.size();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = startIdx; i < endIdx; i++) {
                sb.append(String.format("%5d→%s%n", i + 1, allLines.get(i)));
            }
            if (endIdx < allLines.size()) {
                sb.append(String.format("... (共 %d 行，已显示第 %d-%d 行)%n",
                        allLines.size(), startIdx + 1, endIdx));
            }
            return sb.toString();
        } catch (IOException e) {
            return "错误: 读取文件失败: " + e.getMessage();
        }
    }

    /** 列出目录内容。 */
    private String listDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return "错误: 目录不存在: " + projectPath.relativize(dir);
        }
        if (!Files.isDirectory(dir)) {
            return "错误: 路径不是目录: " + projectPath.relativize(dir);
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(projectPath.relativize(dir)).append(":\n");
            try (var stream = Files.list(dir)) {
                stream.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> {
                            String name = p.getFileName().toString();
                            if (Files.isDirectory(p)) {
                                sb.append("  ").append(name).append("/\n");
                            } else {
                                try {
                                    sb.append(String.format("  %s  (%d bytes)%n",
                                            name, Files.size(p)));
                                } catch (IOException e) {
                                    sb.append("  ").append(name).append("\n");
                                }
                            }
                        });
            }
            return sb.toString();
        } catch (IOException e) {
            return "错误: 列出目录失败: " + e.getMessage();
        }
    }

    /** glob 文件匹配。 */
    private String globFiles(Path root, String pattern, int maxResults) {
        try {
            PathMatcher matcher = root.getFileSystem()
                    .getPathMatcher("glob:" + JavaCodeSearchEngine.normalizeGlob(pattern));
            List<String> results = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(root) && EXCLUDED_DIRS.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= maxResults) {
                        return FileVisitResult.TERMINATE;
                    }
                    Path relative = root.relativize(file);
                    if (matcher.matches(relative)) {
                        results.add(relative.toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
            StringBuilder sb = new StringBuilder();
            sb.append("匹配 ").append(results.size()).append(" 个文件");
            if (results.size() >= maxResults) {
                sb.append("（已截断，max_results=").append(maxResults).append("）");
            }
            sb.append(":\n");
            results.forEach(f -> sb.append("  ").append(f).append("\n"));
            return sb.toString();
        } catch (IOException e) {
            return "错误: glob 文件匹配失败: " + e.getMessage();
        }
    }

    /** 格式化 grep 搜索结果。 */
    private String formatGrepResult(CodeSearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(result.engine()).append("] 找到 ")
                .append(result.matches().size()).append(" 个匹配");
        if (result.partial()) {
            sb.append("（partial: ").append(result.partialReason()).append("）");
        }
        sb.append(":\n\n");
        if (result.matches().isEmpty()) {
            sb.append("未找到匹配。\n");
            return sb.toString();
        }
        // 按文件分组
        Map<String, List<GrepMatch>> byFile = new LinkedHashMap<>();
        for (GrepMatch m : result.matches()) {
            byFile.computeIfAbsent(m.file(), k -> new ArrayList<>()).add(m);
        }
        for (var entry : byFile.entrySet()) {
            sb.append(entry.getKey()).append(":\n");
            for (GrepMatch m : entry.getValue()) {
                sb.append("  ").append(m.lineNumber()).append(":\n");
                for (ContextLine cl : m.context()) {
                    sb.append("    ").append(cl.text()).append("\n");
                }
            }
            sb.append("\n");
        }
        // 建议后续读取
        if (result.partial() || result.matches().size() > 0) {
            sb.append("suggested_reads: 使用 read_file 精确读取匹配文件的相关行段。\n");
        }
        return sb.toString();
    }

    // ========== 辅助方法 ==========

    /** 将参数 JSON 解析为 String→String Map。 */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseArgs(String json) {
        try {
            Map<String, Object> raw = MAPPER.readValue(json, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> result.put(k, v == null ? null : v.toString()));
            return result;
        } catch (IOException e) {
            return Map.of();
        }
    }

    /** 将相对路径（或绝对路径）解析为项目根下的绝对路径。 */
    private Path resolvePath(String path) {
        if (path == null || path.isBlank()) {
            return projectPath;
        }
        Path p = Path.of(path);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        return projectPath.resolve(p).normalize();
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return defaultValue; }
    }

    private boolean parseBool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /** 构建 JSON Schema 参数对象。 */
    private static JsonNode createParameters(Param... params) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        List<String> required = new ArrayList<>();
        for (Param p : params) {
            ObjectNode prop = properties.putObject(p.name());
            prop.put("type", p.type());
            prop.put("description", p.description());
            if (p.required()) {
                required.add(p.name());
            }
        }
        if (!required.isEmpty()) {
            var reqArray = schema.putArray("required");
            required.forEach(reqArray::add);
        }
        return schema;
    }

    private static Param param(String name, String type, String description, boolean required) {
        return new Param(name, type, description, required);
    }

    private record Param(String name, String type, String description, boolean required) {}
}
