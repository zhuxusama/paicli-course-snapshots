package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeChunker 教学测试 —— 展示代码分块器的三种核心能力：
 * <ol>
 *   <li>Java 文件的 AST 驱动语义分块（类级 + 方法级）</li>
 *   <li>Embedding 文本格式化</li>
 *   <li>非 Java 后缀文件的回退处理</li>
 * </ol>
 */
class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker();

    @Test
    void demoJavaFileChunking() throws Exception {
        System.out.println("【输入】SampleService.java —— 一个包含继承、接口实现、构造器注入的示例类");

        Path path = Paths.get("src/test/resources/rag/SampleService.java").toAbsolutePath();
        List<CodeChunk> chunks = chunker.chunkFile(path);

        System.out.println("【转换】AST 解析后生成 " + chunks.size() + " 个代码块：");
        for (CodeChunk c : chunks) {
            System.out.printf("  [%s] %s (行 %d-%d)%n", c.chunkType(), c.name(), c.startLine(), c.endLine());
        }

        System.out.println("【输出】验证 chunk 类型与关键内容");
        assertFalse(chunks.isEmpty());

        // 应包含类级别的 chunk
        assertTrue(chunks.stream().anyMatch(c ->
                c.chunkType().equals("class") && c.name().equals("SampleService")));

        // 应包含方法级别的 chunk
        assertTrue(chunks.stream().anyMatch(c ->
                c.chunkType().equals("method") && c.name().contains("findUserById")));
        assertTrue(chunks.stream().anyMatch(c ->
                c.chunkType().equals("method") && c.name().contains("initialize")));
    }

    @Test
    void demoEmbeddingTextFormat() {
        System.out.println("【输入】构造一个 class 级别的 CodeChunk");

        CodeChunk chunk = CodeChunk.classChunk("Test.java", "TestClass",
                "public class TestClass {\n    private int value;\n}", 1, 3);

        System.out.println("【转换】调用 toEmbeddingText() 生成 Embedding 文本");

        String text = chunk.toEmbeddingText();

        System.out.println("【输出】Embedding 文本: " + text);
        assertTrue(text.contains("[class:TestClass]"));
        assertTrue(text.contains("public class TestClass"));
    }
}
