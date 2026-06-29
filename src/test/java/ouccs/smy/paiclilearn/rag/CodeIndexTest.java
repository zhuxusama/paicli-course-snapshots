package ouccs.smy.paiclilearn.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeIndex 教学测试 —— 展示代码索引的扫描、分块、分析全流程。
 *
 * <p>本章（s14）的 CodeIndex 尚未集成 EmbeddingClient 和 VectorStore
 * （它们将在 s16 加入），因此测试只验证扫描+分块+关系提取+进度回调。
 */
class CodeIndexTest {

    @Test
    void demoIndexNonExistentPath() {
        System.out.println("【输入】不存在的路径");
        CodeIndex indexer = new CodeIndex();
        CodeIndex.IndexResult result = indexer.index("/non/existent/path");

        System.out.println("【输出】chunkCount=" + result.chunkCount() + ", message=" + result.message());
        assertEquals(0, result.chunkCount());
        assertTrue(result.message().contains("路径不存在"));
    }

    @Test
    void demoIndexTestResources() {
        System.out.println("【输入】src/test/resources/rag 目录（含 SampleService.java）");

        CodeIndex indexer = new CodeIndex();
        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        System.out.println("【输出】chunks=" + result.chunkCount() + ", relations=" + result.relationCount());
        assertTrue(result.chunkCount() > 0,
                "应该至少索引一个代码块（类级+方法级）");
        assertTrue(result.message().contains("索引完成"),
                "成功消息应包含 '索引完成'");
    }

    @Test
    void demoProgressListener() {
        System.out.println("【输入】带进度监听器的 CodeIndex");

        List<String> messages = new ArrayList<>();
        CodeIndex indexer = new CodeIndex(messages::add);

        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        System.out.println("【转换】索引过程中产生了 " + messages.size() + " 条进度消息：");
        messages.forEach(msg -> System.out.println("  " + msg));

        System.out.println("【输出】验证进度回调覆盖了开始、发现、完成三个阶段");
        assertTrue(result.chunkCount() > 0);
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("🔍 开始索引")),
                "第一条进度消息应为 '开始索引'");
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("📁 发现")),
                "应包含 '发现文件' 进度消息");
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("✅ 索引完成")),
                "最后一条进度消息应为 '索引完成'");
    }
}
