package ouccs.smy.paiclilearn.rag;

/**
 * 代码块模型：把源文件拆成适合后续 embedding 的结构化片段。
 *
 * @param filePath 源文件路径
 * @param chunkType 块类型：file / class / method
 * @param name 块名称：文件名、类名或方法签名
 * @param content 块内容
 * @param startLine 起始行号，1-based；未知时为 0
 * @param endLine 结束行号，1-based；未知时为 0
 */
public record CodeChunk(String filePath, String chunkType, String name,
                        String content, int startLine, int endLine) {

    public CodeChunk {
        filePath = filePath == null ? "" : filePath;
        chunkType = chunkType == null ? "file" : chunkType;
        name = name == null || name.isBlank() ? filePath : name;
        content = content == null ? "" : content;
    }

    public static CodeChunk fileChunk(String filePath, String content) {
        return new CodeChunk(filePath, "file", filePath, content, 0, 0);
    }

    public static CodeChunk classChunk(String filePath, String className,
                                       String content, int startLine, int endLine) {
        return new CodeChunk(filePath, "class", className, content, startLine, endLine);
    }

    public static CodeChunk methodChunk(String filePath, String methodName,
                                        String content, int startLine, int endLine) {
        return new CodeChunk(filePath, "method", methodName, content, startLine, endLine);
    }

    public String toEmbeddingText() {
        return """
                type: %s
                name: %s
                file: %s
                lines: %d-%d

                %s
                """.formatted(chunkType, name, filePath, startLine, endLine, content).trim();
    }
}
