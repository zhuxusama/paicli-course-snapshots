package ouccs.smy.paiclilearn.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ouccs.smy.paiclilearn.llm.LlmClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 教学演示：四个只读工具的输入→转换→输出全流程。
 * <p>
 * 在一个临时目录中创建真实文件，然后用 ToolRegistry 的工具逐一操作，
 * 打印每一步的输入参数、执行结果的数据流。</p>
 */
class ToolRegistryDemoTest {

    private ToolRegistry registry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        registry = new ToolRegistry();
        registry.setProjectPath(tempDir.toString());

        // 创建测试项目结构
        Path src = Files.createDirectories(tempDir.resolve("src/main/java/example"));
        Files.writeString(src.resolve("App.java"),
                "package example;\n\npublic class App {\n  public static void main(String[] args) {\n    System.out.println(\"Hello\");\n  }\n}\n");
        Files.writeString(src.resolve("Helper.java"),
                "package example;\n\nclass Helper {\n  static String greet(String name) {\n    return \"Hi \" + name;\n  }\n}\n");
        Files.writeString(tempDir.resolve("README.md"), "# Demo Project\n\nThis is a test.\n");
    }

    @Test
    void demoAllFourTools() {
        System.out.println("========== s04 只读工具演示 ==========\n");

        // ===== 1. list_dir：输入目录路径，输出内容列表 =====
        System.out.println("【1. list_dir】输入: {\"path\":\"src/main/java/example\"}");
        String listResult = registry.executeTool("list_dir",
                "{\"path\":\"src/main/java/example\"}");
        System.out.println(listResult);

        // ===== 2. glob_files：输入 glob 模式，输出匹配文件列表 =====
        System.out.println("\n【2. glob_files】输入: {\"pattern\":\"**/*.java\"}");
        String globResult = registry.executeTool("glob_files",
                "{\"pattern\":\"**/*.java\"}");
        System.out.println(globResult);

        // 验证匹配结果
        assertTrue(globResult.contains("App.java"), "应匹配 App.java");
        assertTrue(globResult.contains("Helper.java"), "应匹配 Helper.java");

        // ===== 3. grep_code：输入搜索模式，输出匹配行 =====
        System.out.println("\n【3. grep_code】输入: {\"pattern\":\"greet\",\"glob\":\"**/*.java\"}");
        String grepResult = registry.executeTool("grep_code",
                "{\"pattern\":\"greet\",\"glob\":\"**/*.java\"}");
        System.out.println(grepResult);

        assertTrue(grepResult.contains("Helper.java"), "应定位到 Helper.java");
        assertTrue(grepResult.contains("greet"), "应高亮 greet");
        assertTrue(grepResult.contains("suggested_reads"),
                "应引导后续 read_file 操作");

        // ===== 4. read_file：输入文件路径+offset/limit，输出行段 =====
        System.out.println("【4. read_file】输入: {\"path\":\"src/main/java/example/App.java\",\"offset\":1,\"limit\":5}");
        String readResult = registry.executeTool("read_file",
                "{\"path\":\"src/main/java/example/App.java\",\"offset\":1,\"limit\":5}");
        System.out.println(readResult);

        assertTrue(readResult.contains("1→"), "应有行号前缀");
        assertTrue(readResult.contains("App"), "应显示类名");

        // ===== 5. 展示工具定义（发送给 LLM 的 schema） =====
        System.out.println("【5. 工具定义】");
        List<LlmClient.Tool> defs = registry.getToolDefinitions();
        for (LlmClient.Tool t : defs) {
            System.out.printf("  %s: %s (schema: %s)%n",
                    t.name(), t.description(),
                    t.parameters() != null ? "有" : "无");
        }
        assertTrue(defs.size() >= 4, "应注册至少 4 个工具");

        System.out.println("\n========== 演示结束 ==========");
    }

    /** 演示 executeTools 批量执行——Agent.run() 中如何并行调用多个工具。 */
    @Test
    void demoBatchExecution() {
        System.out.println("========== 批量工具执行演示 ==========");

        var invocations = List.of(
                new ToolRegistry.ToolInvocation("call_1", "read_file",
                        "{\"path\":\"README.md\"}"),
                new ToolRegistry.ToolInvocation("call_2", "list_dir",
                        "{\"path\":\"src/main/java/example\"}")
        );

        // 2. 转换：批量执行（s04 顺序执行，s13 改为并行）
        List<ToolRegistry.ToolExecutionResult> results =
                registry.executeTools(invocations);

        // 3. 输出
        for (ToolRegistry.ToolExecutionResult r : results) {
            System.out.printf("[%s] %s (%.1fms)%n工具输出:%n%s%n",
                    r.id(), r.name(),
                    (double) r.elapsedMillis(), r.result());
        }

        assertEquals(2, results.size());
        for (var r : results) {
            assertFalse(r.timedOut(), "s04 不应超时");
        }
        System.out.println("========== 演示结束 ==========");
    }
}
