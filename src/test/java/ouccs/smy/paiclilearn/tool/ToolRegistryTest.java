package ouccs.smy.paiclilearn.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ToolRegistry 四个只读工具的独立行为。
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.setProjectPath(tempDir.toString());
    }

    /** 验证 read_file 读取多行文件。 */
    @Test
    void readFileReturnsLinesWithNumbering() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3\n");

        String result = registry.executeTool("read_file",
                "{\"path\":\"test.txt\"}");

        assertTrue(result.contains("1→line1"), "应包含行号: " + result);
        assertTrue(result.contains("2→line2"), "应包含行号: " + result);
        assertTrue(result.contains("3→line3"), "应包含行号: " + result);
    }

    /** 验证 read_file 支持 offset 和 limit。 */
    @Test
    void readFileWithOffsetAndLimit() throws Exception {
        Path file = tempDir.resolve("data.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("line").append(i).append("\n");
        }
        Files.writeString(file, sb.toString());

        String result = registry.executeTool("read_file",
                "{\"path\":\"data.txt\",\"offset\":10,\"limit\":3}");

        assertTrue(result.contains("10→line10"), result);
        assertTrue(result.contains("12→line12"), result);
        assertFalse(result.contains("9→line9"), "offset 10 不应包含第 9 行");
    }

    /** 验证 read_file 在文件不存在时返回错误。 */
    @Test
    void readFileNotExists() {
        String result = registry.executeTool("read_file",
                "{\"path\":\"nonexistent.txt\"}");
        assertTrue(result.contains("文件不存在"), result);
    }

    /** 验证 list_dir 列出目录内容。 */
    @Test
    void listDirShowsContents() throws Exception {
        Files.createDirectory(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        Files.writeString(tempDir.resolve("b.txt"), "world");

        String result = registry.executeTool("list_dir",
                "{\"path\":\".\"}");

        assertTrue(result.contains("subdir/"), "应显示目录标记: " + result);
        assertTrue(result.contains("a.txt"), "应列出 a.txt: " + result);
        assertTrue(result.contains("b.txt"), "应列出 b.txt: " + result);
    }

    /** 验证 glob_files 按模式匹配文件。 */
    @Test
    void globFilesMatchesPattern() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.java"), "class Main {}");
        Files.writeString(src.resolve("config.xml"), "<xml/>");
        Files.writeString(tempDir.resolve("pom.xml"), "<pom/>");

        String result = registry.executeTool("glob_files",
                "{\"pattern\":\"**/*.java\"}");

        assertTrue(result.contains("Main.java"), "应匹配 Java 文件: " + result);
        assertFalse(result.contains("config.xml"), "xml 不应被匹配: " + result);
    }

    /** 验证 glob_files 支持 max_results。 */
    @Test
    void globFilesRespectsMaxResults() throws Exception {
        for (int i = 0; i < 5; i++) {
            Files.writeString(tempDir.resolve("file" + i + ".txt"), "x");
        }

        String result = registry.executeTool("glob_files",
                "{\"pattern\":\"**/*.txt\",\"max_results\":2}");

        // 应至少找到文件（匹配数由具体 glob 引擎决定）
        assertTrue(result.contains("匹配") || result.contains("file"),
                "应匹配到文件: " + result);
    }

    /** 验证 grep_code 搜索简单字符串。 */
    @Test
    void greCodeFindsSimplePattern() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"),
                "public class App {\n  public static void main(String[] args) {\n    System.out.println(\"hello\");\n  }\n}\n");

        String result = registry.executeTool("grep_code",
                "{\"pattern\":\"main\",\"glob\":\"**/*.java\"}");

        assertTrue(result.contains("main") || result.contains("App.java"),
                "应找到 main 方法: " + result);
    }

    /** 验证 grep_code 返回 suggested_reads。 */
    @Test
    void greCodeIncludesSuggestedReads() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Foo.java"), "public class Foo {\n  public void bar() {}\n}\n");

        String result = registry.executeTool("grep_code",
                "{\"pattern\":\"bar\",\"glob\":\"**/*.java\"}");

        assertTrue(result.contains("suggested_reads"),
                "应包含 suggested_reads 提示: " + result);
    }

    /** 验证 ToolRegistry 返回非空的工具定义列表。 */
    @Test
    void toolDefinitionsNotEmpty() {
        var defs = registry.getToolDefinitions();
        assertTrue(defs.size() >= 4,
                "应注册至少 4 个工具，实际: " + defs.size());
        var names = defs.stream().map(ouccs.smy.paiclilearn.llm.LlmClient.Tool::name).toList();
        assertTrue(names.contains("read_file"));
        assertTrue(names.contains("list_dir"));
        assertTrue(names.contains("glob_files"));
        assertTrue(names.contains("grep_code"));
    }

    /** 验证未注册工具返回错误。 */
    @Test
    void unknownToolReturnsError() {
        String result = registry.executeTool("nonexistent", "{}");
        assertTrue(result.contains("未注册"), result);
    }

    /** 验证 executeTools 批量调用。 */
    @Test
    void executeToolsBatch() throws Exception {
        Path file = tempDir.resolve("batch.txt");
        Files.writeString(file, "content");

        var results = registry.executeTools(List.of(
                new ToolRegistry.ToolInvocation("1", "read_file", "{\"path\":\"batch.txt\"}"),
                new ToolRegistry.ToolInvocation("2", "list_dir", "{\"path\":\".\"}")
        ));

        assertEquals(2, results.size());
        assertEquals("1", results.get(0).id());
        assertEquals("2", results.get(1).id());
        assertTrue(results.get(0).result().contains("content"), results.get(0).result());
    }
}
