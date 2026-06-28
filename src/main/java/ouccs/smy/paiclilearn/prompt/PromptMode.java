package ouccs.smy.paiclilearn.prompt;

/**
 * 当前课程快照支持的 prompt 运行模式。
 * <p>s07 只有真实可运行的 ReAct Agent；规划和团队模式在对应执行路径出现时再加入。</p>
 *
 * @since s07
 */
public enum PromptMode {
    AGENT("modes/agent.md"),
    /** [s10 新增] 计划生成模式——LLM 根据用户目标拆分为 DAG 任务序列。 */
    PLANNER("modes/planner.md"),
    /** [s11 新增] 计划执行模式——按 DAG 逐个执行任务，带工具调用和审阅闭环。 */
    PLAN("modes/plan.md");

    private final String resourcePath;

    PromptMode(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /** 返回该模式对应的 classpath 相对资源路径。 */
    public String resourcePath() {
        return resourcePath;
    }
}
