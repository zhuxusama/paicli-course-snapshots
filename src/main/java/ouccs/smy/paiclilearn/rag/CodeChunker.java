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
 * 浠ｇ爜鍒嗗潡鍣?鈥斺€?灏嗘簮浠ｇ爜鏂囦欢鎸夎涔夌矑搴︽媶鍒嗕负閫傚悎 Embedding 鐨勪唬鐮佸潡銆? *
 * <p>鍒囧垎绛栫暐锛? * <ul>
 *   <li><b>闈?Java 鏂囦欢</b>锛氭暣涓枃浠朵綔涓轰竴涓?chunk锛涜嫢瓒呰繃 2000 瀛楃鍒欐寜琛屽垎娈点€?/li>
 *   <li><b>Java 鏂囦欢</b>锛氬厛鐢?JavaParser 瑙ｆ瀽 AST锛屾彁鍙栫被绾у埆 chunk锛堢被绛惧悕+鍓嶅嚑琛岋級
 *       鍜屾柟娉曠骇鍒?chunk锛堟瘡涓柟娉曠殑瀹屾暣澹版槑锛夛紱瑙ｆ瀽澶辫触鏃跺洖閫€鍒版寜澶у皬鍒嗘銆?/li>
 * </ul>
 *
 * <p>姣忎釜 {@link CodeChunk} 鐨?{@code toEmbeddingText()} 鏂规硶浼氬湪鍚庣画绔犺妭涓
 * {@code EmbeddingClient} 璋冪敤锛岀敓鎴愬悜閲忓悗瀛樺叆 {@code VectorStore}銆? *
 * @since s15
 */
public class CodeChunker {

    /** Java 17 AST 瑙ｆ瀽鍣?*/
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    /** 鍗曚釜 chunk 鏈€澶у瓧绗︽暟锛屼繚璇侀€傞厤甯歌 Embedding 妯″瀷鐨勪笂涓嬫枃绐楀彛銆?*/
    private static final int MAX_CHUNK_CHARS = 2000;

    /**
     * 瀵瑰崟涓枃浠惰繘琛屽垎鍧椼€傛牴鎹枃浠跺悗缂€閫夋嫨 Java AST 鍒嗗潡鎴栫函鏂囨湰鍒嗘銆?     *
     * @param filePath 鏂囦欢璺緞
     * @return 璇ユ枃浠剁殑鎵€鏈変唬鐮佸潡
     * @throws IOException 璇诲彇鏂囦欢澶辫触鏃舵姏鍑?     */
    public List<CodeChunk> chunkFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String relativePath = filePath.toString();

        if (!relativePath.endsWith(".java")) {
            return chunkLargeText(relativePath, content);
        }

        return chunkJavaFile(filePath, content);
    }

    /**
     * 灏嗗ぇ鏂囨湰鎸夎鍒嗘锛屾瘡娈典笉瓒呰繃 {@link #MAX_CHUNK_CHARS} 瀛楃銆?     * 鐢ㄤ簬闈?Java 鏂囦欢鎴?AST 瑙ｆ瀽澶辫触鏃剁殑鍥為€€璺緞銆?     */
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
     * 瀵?Java 鏂囦欢鍋?AST 椹卞姩鐨勮涔夊垎鍧楋細閬嶅巻姣忎釜绫?鎺ュ彛澹版槑锛?     * 鐢熸垚"绫荤骇 chunk"鍜?鏂规硶绾?chunk"銆?     */
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

            // 绫荤骇鍧楋細鎻愬彇绫诲０鏄庡ご閮紙绛惧悕 + 寮€澶村嚑琛屽瓧娈?鎴愬憳锛?            String classHeader = extractLines(content, classStart,
                    Math.min(classStart + 5, classEnd));
            chunks.add(CodeChunk.classChunk(
                    filePath.toString(), className, classHeader, classStart, classEnd));

            // 鏂规硶绾у潡锛氭瘡涓柟娉曠嫭绔嬫垚鍧?            clazz.getMethods().forEach(method -> {
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
     * 浠庡畬鏁存簮鐮佷腑鎻愬彇鎸囧畾琛岃寖鍥寸殑鍐呭銆?     */
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
