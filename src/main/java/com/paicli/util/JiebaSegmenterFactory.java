package com.paicli.util;

import com.huaban.analysis.jieba.JiebaSegmenter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * s16: 静默创建 Jieba 分词器，避免词典首次加载日志污染 CLI 或 DemoTest 输出。
 * <p>
 * 原项目把这层隔离成工具类，是因为 jieba-analysis 初始化时会直接写 stdout。
 */
public final class JiebaSegmenterFactory {
    private JiebaSegmenterFactory() {
    }

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
