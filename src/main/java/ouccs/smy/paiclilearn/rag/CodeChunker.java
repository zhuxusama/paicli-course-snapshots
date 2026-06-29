package ouccs.smy.paiclilearn.rag;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码分块器 —— 将源代码文件按语义粒度拆分为适合 Embedding 的代码块。
 *
 * <p>切分策略：
 * <ul>
 *   <li><b>非 Java 文件</b>：整个文件作为一个 chunk；若超过 2000 字符则按行分段。</li>
 *   <li><b>Java 文件</b>：先用 JavaParser 解析 AST，提取类级别 chunk（类签名+前几行）
 *       和方法级别 chunk（每个方法的完整声明）；解析失败时回退到按大小分段。</li>
 * </ul>
 *
 * <p>每个 {@link CodeChunk} 的 {@code toEmbeddingText()} 方法会在后续章节中被
 * {@code EmbeddingClient} 调用，生成向量后存入 {@code VectorStore}。
 *
 * @since s14
 */
public class CodeChunker {

    /** Java 17 AST 解析器 */
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    /** 单个 chunk 最大字符数，保证适配常见 Embedding 模型的上下文窗口。 */
    private static final int MAX_CHUNK_CHARS = 2000;

    /**
     * 对单个文件进行分块。根据文件后缀选择 Java AST 分块或纯文本分段。
     *
     * @param filePath 文件路径
     * @return 该文件的所有代码块
     * @throws IOException 读取文件失败时抛出
     */
    public List<CodeChunk> chunkFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String relativePath = filePath.toString();

        if (!relativePath.endsWith(".java")) {
            return chunkLargeText(relativePath, content);
        }

        return chunkJavaFile(filePath, content);
    }

    /**
     * 将大文本按行分段，每段不超过 {@link #MAX_CHUNK_CHARS} 字符。
     * 用于非 Java 文件或 AST 解析失败时的回退路径。
     */
    private List<CodeChunk> chunkLargeText(String filePath, String content) {
        if (content.length() <= MAX_CHUNK_CHARS) {
            return List.of(CodeChunk.fileChunk(filePath, content));
        }

        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\r?\n");
        StringBuilder segment = new StringBuilder();
        int segIndex = 1;
        int startLine = 1;

        for (int i = 0; i < lines.length; i++) {
            if (segment.length() + lines[i].length() + 1 > MAX_CHUNK_CHARS && !segment.isEmpty()) {
                chunks.add(new CodeChunk(filePath, "file",
                        filePath + "#" + segIndex, segment.toString().trim(), startLine, i));
                segment.setLength(0);
                segIndex++;
                startLine = i + 1;
            }
            segment.append(lines[i]).append("\n");
        }

        if (!segment.isEmpty()) {
            chunks.add(new CodeChunk(filePath, "file",
                    filePath + "#" + segIndex, segment.toString().trim(), startLine, lines.length));
        }

        return chunks;
    }

    /**
     * 对 Java 文件做 AST 驱动的语义分块：遍历每个类/接口声明，
     * 生成"类级 chunk"和"方法级 chunk"。
     */
    private List<CodeChunk> chunkJavaFile(Path filePath, String content) {
        List<CodeChunk> chunks = new ArrayList<>();
        ParseResult<CompilationUnit> result = parser.parse(content);

        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return chunkLargeText(filePath.toString(), content);
        }

        CompilationUnit cu = result.getResult().get();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            int classStart = clazz.getBegin().map(p -> p.line).orElse(0);
            int classEnd = clazz.getEnd().map(p -> p.line).orElse(0);
            String className = clazz.getNameAsString();

            // 类级块：提取类声明头部（签名 + 开头几行字段/成员）
            String classHeader = extractLines(content, classStart,
                    Math.min(classStart + 5, classEnd));
            chunks.add(CodeChunk.classChunk(
                    filePath.toString(), className, classHeader, classStart, classEnd));

            // 方法级块：每个方法独立成块
            clazz.getMethods().forEach(method -> {
                int methodStart = method.getBegin().map(p -> p.line).orElse(0);
                int methodEnd = method.getEnd().map(p -> p.line).orElse(0);
                String methodSignature = method.getDeclarationAsString(false, false, false);
                String methodContent = extractLines(content, methodStart, methodEnd);

                chunks.add(CodeChunk.methodChunk(
                        filePath.toString(),
                        className + "." + methodSignature,
                        methodContent, methodStart, methodEnd));
            });
        });

        if (chunks.isEmpty()) {
            return chunkLargeText(filePath.toString(), content);
        }

        return chunks;
    }

    /**
     * 从完整源码中提取指定行范围的内容。
     */
    private String extractLines(String content, int startLine, int endLine) {
        String[] lines = content.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (int i = startLine - 1; i < Math.min(endLine, lines.length); i++) {
            if (i >= 0) {
                sb.append(lines[i]).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
