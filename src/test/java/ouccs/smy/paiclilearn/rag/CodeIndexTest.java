package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeIndex 鏁欏娴嬭瘯 鈥斺€?灞曠ず浠ｇ爜绱㈠紩鐨勬壂鎻忋€佸垎鍧椼€佸垎鏋愬叏娴佺▼銆? *
 * <p>鏈珷锛坰14锛夌殑 CodeIndex 灏氭湭闆嗘垚 EmbeddingClient 鍜?VectorStore
 * 锛堝畠浠皢鍦?s16 鍔犲叆锛夛紝鍥犳娴嬭瘯鍙獙璇佹壂鎻?鍒嗗潡+鍏崇郴鎻愬彇+杩涘害鍥炶皟銆? */
class CodeIndexTest {

    @Test
    void demoIndexNonExistentPath() {
        System.out.println("銆愯緭鍏ャ€戜笉瀛樺湪鐨勮矾寰?);
        CodeIndex indexer = new CodeIndex();
        CodeIndex.IndexResult result = indexer.index("/non/existent/path");

        System.out.println("銆愯緭鍑恒€慶hunkCount=" + result.chunkCount() + ", message=" + result.message());
        assertEquals(0, result.chunkCount());
        assertTrue(result.message().contains("璺緞涓嶅瓨鍦?));
    }

    @Test
    void demoIndexTestResources() {
        System.out.println("銆愯緭鍏ャ€憇rc/test/resources/rag 鐩綍锛堝惈 SampleService.java锛?);

        CodeIndex indexer = new CodeIndex();
        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        System.out.println("銆愯緭鍑恒€慶hunks=" + result.chunkCount() + ", relations=" + result.relationCount());
        assertTrue(result.chunkCount() > 0,
                "搴旇鑷冲皯绱㈠紩涓€涓唬鐮佸潡锛堢被绾?鏂规硶绾э級");
        assertTrue(result.message().contains("绱㈠紩瀹屾垚"),
                "鎴愬姛娑堟伅搴斿寘鍚?'绱㈠紩瀹屾垚'");
    }

    @Test
    void demoProgressListener() {
        System.out.println("銆愯緭鍏ャ€戝甫杩涘害鐩戝惉鍣ㄧ殑 CodeIndex");

        List<String> messages = new ArrayList<>();
        CodeIndex indexer = new CodeIndex(messages::add);

        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        System.out.println("銆愯浆鎹€戠储寮曡繃绋嬩腑浜х敓浜?" + messages.size() + " 鏉¤繘搴︽秷鎭細");
        messages.forEach(msg -> System.out.println("  " + msg));

        System.out.println("銆愯緭鍑恒€戦獙璇佽繘搴﹀洖璋冭鐩栦簡寮€濮嬨€佸彂鐜般€佸畬鎴愪笁涓樁娈?);
        assertTrue(result.chunkCount() > 0);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("馃攳 寮€濮嬬储寮?)),
                "绗竴鏉¤繘搴︽秷鎭簲涓?'寮€濮嬬储寮?");
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("馃搧 鍙戠幇")),
                "搴斿寘鍚?'鍙戠幇鏂囦欢' 杩涘害娑堟伅");
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("鉁?绱㈠紩瀹屾垚")),
                "鏈€鍚庝竴鏉¤繘搴︽秷鎭簲涓?'绱㈠紩瀹屾垚'");
    }
}
