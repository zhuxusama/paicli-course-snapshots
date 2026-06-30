package ouccs.smy.paiclilearn.rag;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码切块器：Java 文件按 class/method 切块，其他文本按大小分段。
 */
public class CodeChunker {

    private static final int MAX_CHUNK_CHARS = 2000;

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    public List<CodeChunk> chunkFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        if (!filePath.toString().endsWith(".java")) {
            return chunkText(filePath.toString(), content);
        }
        return chunkJava(filePath, content);
    }

    private List<CodeChunk> chunkJava(Path filePath, String content) {
        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return chunkText(filePath.toString(), content);
        }

        List<CodeChunk> chunks = new ArrayList<>();
        CompilationUnit cu = result.getResult().get();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            int classStart = clazz.getBegin().map(p -> p.line).orElse(1);
            int classEnd = clazz.getEnd().map(p -> p.line).orElse(classStart);
            String className = clazz.getNameAsString();
            String classHeader = extractLines(content, classStart, Math.min(classStart + 5, classEnd));
            chunks.add(CodeChunk.classChunk(filePath.toString(), className, classHeader, classStart, classEnd));

            clazz.getMethods().forEach(method -> {
                int methodStart = method.getBegin().map(p -> p.line).orElse(classStart);
                int methodEnd = method.getEnd().map(p -> p.line).orElse(methodStart);
                String signature = method.getDeclarationAsString(false, false, false);
                String methodContent = extractLines(content, methodStart, methodEnd);
                chunks.add(CodeChunk.methodChunk(filePath.toString(),
                        className + "." + signature, methodContent, methodStart, methodEnd));
            });
        });

        return chunks.isEmpty() ? chunkText(filePath.toString(), content) : chunks;
    }

    private List<CodeChunk> chunkText(String filePath, String content) {
        if (content.length() <= MAX_CHUNK_CHARS) {
            return List.of(CodeChunk.fileChunk(filePath, content));
        }

        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        StringBuilder segment = new StringBuilder();
        int startLine = 1;
        int segmentIndex = 1;
        for (int i = 0; i < lines.length; i++) {
            if (segment.length() + lines[i].length() + 1 > MAX_CHUNK_CHARS && !segment.isEmpty()) {
                chunks.add(new CodeChunk(filePath, "file",
                        filePath + "#" + segmentIndex, segment.toString().trim(), startLine, i));
                segment.setLength(0);
                startLine = i + 1;
                segmentIndex++;
            }
            segment.append(lines[i]).append('\n');
        }
        if (!segment.isEmpty()) {
            chunks.add(new CodeChunk(filePath, "file",
                    filePath + "#" + segmentIndex, segment.toString().trim(), startLine, lines.length));
        }
        return chunks;
    }

    private String extractLines(String content, int startLine, int endLine) {
        String[] lines = content.split("\\R", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, startLine - 1); i < Math.min(endLine, lines.length); i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString().trim();
    }
}
