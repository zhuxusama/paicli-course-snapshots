package ouccs.smy.paiclilearn.rag;

/**
 * 浠ｇ爜鍧楁暟鎹ā鍨?鈥斺€?灏嗘簮浠ｇ爜鏂囦欢鎸夌矑搴︽媶鍒嗕负缁撴瀯鍖栫殑"鍧?锛屼緵鍚庣画 Embedding 浣跨敤銆? *
 * <p>涓夌鍒囧潡绮掑害锛? * <ul>
 *   <li><b>file</b>锛氭暣涓枃浠讹紙闈?Java 鏂囦欢鎴栧ぇ鏂囨湰鍒嗘鍚庣殑閫昏緫娈碉級</li>
 *   <li><b>class</b>锛氱被/鎺ュ彛澹版槑锛堝惈绫荤鍚嶅拰鍓嶅嚑琛屾垚鍛橈級</li>
 *   <li><b>method</b>锛氭柟娉曞０鏄庯紙鍚柟娉曠鍚嶅拰瀹屾暣鏂规硶浣擄級</li>
 * </ul>
 *
 * @param filePath  婧愭枃浠剁浉瀵硅矾寰? * @param chunkType 鍧楃被鍨嬶紙"file" / "class" / "method"锛? * @param name      鍧楀悕绉帮紙绫诲悕銆佹柟娉曠鍚嶆垨鏂囦欢鍚嶏級
 * @param content   鍧楁枃鏈唴瀹? * @param startLine 璧峰琛屽彿锛?-based锛? * @param endLine   缁撴潫琛屽彿锛?-based锛? * @since s15
 */
public record CodeChunk(String filePath, String chunkType, String name,
                        String content, int startLine, int endLine) {

    /**
     * 鏋勯€犳枃浠剁骇鍒殑浠ｇ爜鍧椻€斺€旂敤浜庨潪 Java 鏂囦欢鎴栨寜澶у皬鍒囧垎鍚庣殑鏂囨湰娈点€?     */
    public static CodeChunk fileChunk(String filePath, String content) {
        return new CodeChunk(filePath, "file", filePath, content, 0, 0);
    }

    /**
     * 鏋勯€犵被绾у埆鐨勪唬鐮佸潡鈥斺€斿寘鍚被绛惧悕鍜屽紑澶村嚑琛岀殑鎴愬憳/瀛楁淇℃伅銆?     */
    public static CodeChunk classChunk(String filePath, String className,
                                       String content, int startLine, int endLine) {
        return new CodeChunk(filePath, "class", className, content, startLine, endLine);
    }

    /**
     * 鏋勯€犳柟娉曠骇鍒殑浠ｇ爜鍧椻€斺€斿寘鍚畬鏁存柟娉曠鍚嶅拰鏂规硶浣撱€?     */
    public static CodeChunk methodChunk(String filePath, String methodName,
                                        String content, int startLine, int endLine) {
        return new CodeChunk(filePath, "method", methodName, content, startLine, endLine);
    }

    /**
     * 鐢熸垚鐢ㄤ簬 Embedding 鐨勬枃鏈〃绀猴細{@code [chunkType:name] content}銆?     * 鍚庣画绔犺妭鐨?EmbeddingClient 灏嗚鍙栨鏂囨湰鐢熸垚鍚戦噺銆?     */
    public String toEmbeddingText() {
        return String.format("[%s:%s] %s", chunkType, name, content);
    }
}
