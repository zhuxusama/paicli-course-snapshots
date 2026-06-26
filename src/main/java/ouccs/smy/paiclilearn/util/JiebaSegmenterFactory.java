package ouccs.smy.paiclilearn.util;

import com.huaban.analysis.jieba.JiebaSegmenter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * jieba 分词器工厂——在首次加载词典时静默 stdout，避免污染 CLI 界面。
 *
 * <p>jieba-analysis 初始化时会打印词典加载信息到 System.out，
 * 在交互式 CLI 环境中这会破坏终端布局。本工厂在构造分词器时
 * 临时重定向 stdout，构造完成后恢复。</p>
 *
 * @since s09
 */
public final class JiebaSegmenterFactory {
    private JiebaSegmenterFactory() {}

    /** 创建 jieba 分词器，期间静默 stdout。 */
    public static JiebaSegmenter createSilently() {
        synchronized (JiebaSegmenterFactory.class) {
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
                return new JiebaSegmenter();
            } finally {
                System.setOut(originalOut);
            }
        }
    }
}
