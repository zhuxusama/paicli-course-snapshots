package ouccs.smy.paiclilearn.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码搜索引擎的确定性 gold set 测试。
 * <p>使用纯 Java 搜索（禁用 ripgrep）对项目源码执行 grep + read_file，
 * 验证搜索输出在预算内且能找到预期代码位置。</p>
 */
class CodeSearchGoldenSetTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CHARS = 6_000;

    @Test
    void grepThenReadGoldenSetStaysWithinBudgetAndFindsExpectedCode() throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        ToolRegistry registry = new ToolRegistry();
        registry.setProjectPath(projectRoot.toString());
        List<GoldenCase> cases = loadGoldenSet();

        String previous = System.getProperty("paicli.search.disable.rg");
        System.setProperty("paicli.search.disable.rg", "true");
        try {
            for (GoldenCase goldenCase : cases) {
                Path expectedFile = projectRoot.resolve(goldenCase.expectedPath());
                int expectedLine = lineContaining(expectedFile, goldenCase.expectedText());

                String grepJson = """
                        {"pattern":"%s","glob":"%s","max_results":20,"head_limit":5}
                        """.formatted(
                        jsonEscape(goldenCase.pattern()),
                        jsonEscape(goldenCase.glob()));

                String grepResult = registry.executeTool("grep_code", grepJson);

                assertTrue(grepResult.length() <= MAX_CHARS + 500,
                        () -> goldenCase.id() + " 超出 grep 输出预算: " + grepResult.length());
                assertTrue(grepResult.contains(expectedFile.getFileName().toString())
                                || grepResult.contains(
                                expectedFile.toString().replace('\\', '/')),
                        () -> goldenCase.id() + " 未找到预期文件。输出:\n" + grepResult);

                int offset = Math.max(1, expectedLine - 5);
                String readJson = """
                        {"path":"%s","offset":%d,"limit":30}
                        """.formatted(jsonEscape(goldenCase.expectedPath()), offset);
                String readResult = registry.executeTool("read_file", readJson);
                assertTrue(readResult.contains(goldenCase.expectedText()),
                        () -> goldenCase.id() + " read_file 未找到预期文本。输出:\n" + readResult);
            }
        } finally {
            restoreSystemProperty("paicli.search.disable.rg", previous);
        }
    }

    private List<GoldenCase> loadGoldenSet() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/code-search/golden-set.json")) {
            assertNotNull(in, "golden-set.json 应存在于测试资源中");
            List<GoldenCase> cases = MAPPER.readValue(in, new TypeReference<>() {});
            assertFalse(cases.isEmpty(), "golden set 应包含至少一条用例");
            return cases;
        }
    }

    private int lineContaining(Path file, String text) throws Exception {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) {
                return i + 1;
            }
        }
        throw new AssertionError("未在 " + file + " 中找到: " + text);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void restoreSystemProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private record GoldenCase(
            String id, String question, String pattern,
            String glob, String expectedPath, String expectedText) {}
}
