package ouccs.smy.paiclilearn.prompt;

/**
 * 当前课程快照支持的 prompt 运行模式。
 * <p>s07 只有真实可运行的 ReAct Agent；规划和团队模式在对应执行路径出现时再加入。</p>
 *
 * @since s07
 */
public enum PromptMode {
    AGENT("modes/agent.md");

    private final String resourcePath;

    PromptMode(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /** 返回该模式对应的 classpath 相对资源路径。 */
    public String resourcePath() {
        return resourcePath;
    }
}
