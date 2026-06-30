package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker();

    @Test
    void chunksJavaFileIntoClassAndMethods() throws Exception {
        Path path = Paths.get("src/test/resources/rag/SampleService.java").toAbsolutePath();
        List<CodeChunk> chunks = chunker.chunkFile(path);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(c ->
                c.chunkType().equals("class") && c.name().equals("SampleService")));
        assertTrue(chunks.stream().anyMatch(c ->
                c.chunkType().equals("method") && c.name().contains("findUserById")));
        assertTrue(chunks.stream().anyMatch(c ->
                c.chunkType().equals("method") && c.name().contains("initialize")));
    }

    @Test
    void formatsEmbeddingTextWithMetadata() {
        CodeChunk chunk = CodeChunk.classChunk("Test.java", "TestClass",
                "public class TestClass {\n    private int value;\n}", 1, 3);

        String text = chunk.toEmbeddingText();

        assertTrue(text.contains("type: class"));
        assertTrue(text.contains("name: TestClass"));
        assertTrue(text.contains("public class TestClass"));
    }

    @Test
    void chunksLargeNonJavaTextBySize() throws Exception {
        Path file = Files.createTempFile("paicli-rag", ".md");
        Files.writeString(file, "x".repeat(2500) + "\n" + "y".repeat(2500));

        List<CodeChunk> chunks = chunker.chunkFile(file);

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().allMatch(c -> c.chunkType().equals("file")));
    }
}
