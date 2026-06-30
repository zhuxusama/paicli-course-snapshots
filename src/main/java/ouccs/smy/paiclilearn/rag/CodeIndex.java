package ouccs.smy.paiclilearn.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * 浠ｇ爜绱㈠紩绠＄悊鍣?鈥斺€?鎵弿椤圭洰鐩綍銆佸浠ｇ爜鏂囦欢鍒嗗潡銆佹彁鍙栫粨鏋勫叧绯汇€? *
 * <p>鏈珷锛坰14锛夌殑鏍稿績鑱岃矗锛氭妸"椤圭洰鎵弿 鈫?鏂囦欢鏀堕泦 鈫?AST 鍒嗗潡 鈫?鍏崇郴鎻愬彇"
 * 涓茶仈鎴愪竴鏉″彲瑙傛祴鐨勭閬撱€傝繘搴﹂€氳繃 {@link ProgressListener} 鍥炰紶锛? * 缁撴灉姹囧叆 {@link IndexResult}銆? *
 * <p><b>涓庢簮椤圭洰鐨勫樊寮傦細</b>婧愰」鐩殑 {@code CodeIndex} 鍦ㄦ瀯閫犲嚱鏁颁腑鎸佹湁
 * {@code EmbeddingClient} 鍜?{@code VectorStore}锛屾湰鐗堟湰鏆傛椂涓嶅紩鍏ヨ繖涓や釜渚濊禆
 * 锛堝畠浠皢鍦?s16 鐨?Embedding / SQLite 鍚戦噺瀛樺偍绔犺妭鍔犲叆锛夈€? * 褰撳墠鐗堟湰浠呮敹闆?chunk 涓?relation 鐨勮鏁颁俊鎭紝涓嶅仛鍚戦噺鍖栦笌鎸佷箙鍖栥€? *
 * <p>闆嗘垚鐐癸細鍚庣画绔犺妭鐨?{@code /index} 鍛戒护浼氭瀯閫?{@code CodeIndex}
 * 骞舵敞鍐?{@code ProgressListener}锛屾妸绱㈠紩杩涘害鎺ㄩ€佸埌缁堢杈撳嚭娴併€? *
 * @since s15
 */
public class CodeIndex {

    private static final Logger log = LoggerFactory.getLogger(CodeIndex.class);

    private final CodeChunker chunker;
    private final CodeAnalyzer analyzer;
    private final ProgressListener progressListener;

    /**
     * 绱㈠紩杩涘害鐩戝惉鍣?鈥斺€?鐢辫皟鐢ㄦ柟娉ㄥ叆锛岀敤浜庢妸杩涘害娑堟伅鎺ㄩ€佸埌缁堢鎴栨棩蹇椼€?     */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(String message);

        static ProgressListener noop() {
            return message -> {};
        }
    }

    /** 鏃犺繘搴︾洃鍚櫒鐨勯粯璁ゆ瀯閫?*/
    public CodeIndex() {
        this(ProgressListener.noop());
    }

    /** 甯﹁繘搴︾洃鍚櫒鐨勬瀯閫?*/
    public CodeIndex(ProgressListener progressListener) {
        this.chunker = new CodeChunker();
        this.analyzer = new CodeAnalyzer();
        this.progressListener = progressListener == null ? ProgressListener.noop() : progressListener;
    }

    /**
     * 绱㈠紩鎸囧畾璺緞鐨勪唬鐮佸簱锛氭敹闆嗘枃浠?鈫?鍒嗗潡 鈫?鍒嗘瀽鍏崇郴 鈫?杩斿洖缁熻銆?     *
     * @param projectPath 椤圭洰鏍圭洰褰曡矾寰?     * @return 绱㈠紩缁熻缁撴灉锛坈hunk 鏁般€佸叧绯绘暟銆佺姸鎬佹秷鎭級
     */
    public IndexResult index(String projectPath) {
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            String message = "璺緞涓嶅瓨鍦? " + projectPath;
            emit("鉂?" + message);
            return new IndexResult(0, 0, message);
        }

        emit("馃攳 寮€濮嬬储寮? " + root);

        // 姝ラ 1锛氭敹闆嗗緟绱㈠紩鏂囦欢
        List<Path> filesToIndex = new ArrayList<>();
        collectFiles(root, filesToIndex);
        emit("馃搧 鍙戠幇 " + filesToIndex.size() + " 涓枃浠跺緟绱㈠紩");

        int totalChunks = 0;
        int totalRelations = 0;

        int processed = 0;
        int total = filesToIndex.size();

        // 姝ラ 2 & 3锛氶€愭枃浠跺垎鍧?+ 鍒嗘瀽鍏崇郴
        for (Path file : filesToIndex) {
            processed++;
            if (processed % 10 == 0 || processed == total) {
                emit(String.format("   杩涘害: %d/%d (%s)",
                        processed, total, file.getFileName()));
            }

            try {
                List<CodeChunk> chunks = chunker.chunkFile(file);
                totalChunks += chunks.size();

                if (file.toString().endsWith(".java")) {
                    List<CodeRelation> relations = analyzer.analyzeFile(file);
                    totalRelations += relations.size();
                }
            } catch (Exception e) {
                String message = "   鈿狅笍 绱㈠紩澶辫触: " + file + " - " + e.getMessage();
                emit(message);
                log.warn("code index failed for file {}", file, e);
            }
        }

        String msg = String.format("绱㈠紩瀹屾垚锛?d 涓唬鐮佸潡锛?d 鏉″叧绯?,
                totalChunks, totalRelations);
        emit("鉁?" + msg);
        return new IndexResult(totalChunks, totalRelations, msg);
    }

    private void emit(String message) {
        progressListener.onProgress(message);
    }

    /**
     * 閫掑綊鏀堕泦椤圭洰鐩綍涓渶瑕佺储寮曠殑浠ｇ爜鏂囦欢銆?     * 璺宠繃甯歌鐨勯潪浠ｇ爜鐩綍鍜岄殣钘忕洰褰曘€?     */
    private void collectFiles(Path root, List<Path> files) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (dirName.equals("node_modules") || dirName.equals("target")
                            || dirName.equals("build") || dirName.equals(".git")
                            || dirName.equals(".idea") || dirName.equals(".vscode")
                            || dirName.equals("dist") || dirName.equals("out")
                            || dirName.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".java") || name.endsWith(".py")
                            || name.endsWith(".js") || name.endsWith(".ts")
                            || name.endsWith(".go") || name.endsWith(".rs")
                            || name.endsWith(".c") || name.endsWith(".cpp")
                            || name.endsWith(".h") || name.endsWith(".md")
                            || name.endsWith(".xml") || name.endsWith(".properties")
                            || name.endsWith(".yaml") || name.endsWith(".yml")
                            || name.endsWith(".json") || name.endsWith(".sh")
                            || name.endsWith(".gradle") || name.endsWith(".kt")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            String message = "閬嶅巻鏂囦欢澶辫触: " + e.getMessage();
            emit("鉂?" + message);
            log.warn("code index file traversal failed for root {}", root, e);
        }
    }

    /**
     * 绱㈠紩缁撴灉 鈥斺€?鍖呭惈 chunk 鏁般€佸叧绯绘暟鍜岀姸鎬佹弿杩版秷鎭€?     */
    public record IndexResult(int chunkCount, int relationCount, String message) {}
}
