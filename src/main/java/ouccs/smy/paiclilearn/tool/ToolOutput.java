package ouccs.smy.paiclilearn.tool;

/**
 * 工具执行结果的值对象。
 * <p>
 * text 为工具返回的文本描述。
 * s25 起将增加 imageParts 字段支持图片输出。</p>
 *
 * @param text 工具输出的文本内容
 * @since s04
 */
public record ToolOutput(String text) {
    public ToolOutput {
        text = text == null ? "" : text;
    }

    /** 创建纯文本输出。 */
    public static ToolOutput text(String text) {
        return new ToolOutput(text);
    }
}
