package ouccs.smy.paiclilearn.rag;

/**
 * 代码块数据模型 —— 将源代码文件按粒度拆分为结构化的"块"，供后续 Embedding 使用。
 *
 * <p>三种切块粒度：
 * <ul>
 *   <li><b>file</b>：整个文件（非 Java 文件或大文本分段后的逻辑段）</li>
 *   <li><b>class</b>：类/接口声明（含类签名和前几行成员）</li>
 *   <li><b>method</b>：方法声明（含方法签名和完整方法体）</li>
 * </ul>
 *
 * @param filePath  源文件相对路径
 * @param chunkType 块类型（"file" / "class" / "method"）
 * @param name      块名称（类名、方法签名或文件名）
 * @param content   块文本内容
 * @param startLine 起始行号（1-based）
 * @param endLine   结束行号（1-based）
 * @since s14
 */
public record CodeChunk(String filePath, String chunkType, String name,
                        String content, int startLine, int endLine) {

    /**
     * 构造文件级别的代码块——用于非 Java 文件或按大小切分后的文本段。
     */
    public static CodeChunk fileChunk(String filePath, String content) {
        return new CodeChunk(filePath, "file", filePath, content, 0, 0);
    }

    /**
     * 构造类级别的代码块——包含类签名和开头几行的成员/字段信息。
     */
    public static CodeChunk classChunk(String filePath, String className,
                                       String content, int startLine, int endLine) {
        return new CodeChunk(filePath, "class", className, content, startLine, endLine);
    }

    /**
     * 构造方法级别的代码块——包含完整方法签名和方法体。
     */
    public static CodeChunk methodChunk(String filePath, String methodName,
                                        String content, int startLine, int endLine) {
        return new CodeChunk(filePath, "method", methodName, content, startLine, endLine);
    }

    /**
     * 生成用于 Embedding 的文本表示：{@code [chunkType:name] content}。
     * 后续章节的 EmbeddingClient 将读取此文本生成向量。
     */
    public String toEmbeddingText() {
        return String.format("[%s:%s] %s", chunkType, name, content);
    }
}
