package ouccs.smy.paiclilearn.agent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Agent 循环的退出预算——三种"保险阀"防止死循环。
 * <p>
 * [s13 新增] LLM 主导循环退出（只要返回 content 不再调用工具即结束），
 * AgentBudget 只在异常情况下兜底：
 * <ol>
 *   <li>Token 预算：累计 input+output token 超过阈值（默认 Integer.MAX_VALUE，实质不限）</li>
 *   <li>停滞检测：连续 N 轮工具调用完全相同（默认 3 轮），判定死循环</li>
 *   <li>硬轮数上限：累计迭代轮数超过上限（默认 50 轮）</li>
 * </ol>
 * 三个条件"先到先触发"。
 * </p>
 *
 * @since s13
 */
public class AgentBudget {

    /** 退出原因枚举。 @since s13 */
    public enum ExitReason {
        WITHIN_BUDGET,          // 预算内，可继续
        TOKEN_BUDGET_EXCEEDED,  // Token 超限
        STAGNATION_DETECTED,    // 停滞（死循环）
        HARD_ITERATION_LIMIT    // 达到硬轮数上限
    }

    private static final int DEFAULT_STAGNATION_WINDOW = 3;
    private static final int DEFAULT_HARD_MAX_ITERATIONS = 50;
    private static final int DEFAULT_TOKEN_BUDGET = Integer.MAX_VALUE;

    private final int tokenBudget;
    private final int stagnationWindow;
    private final int hardMaxIterations;

    private final Deque<String> recentToolSignatures = new ArrayDeque<>();
    private int iteration;
    private int totalInputTokens;
    private int totalOutputTokens;
    private int totalCachedInputTokens;
    private boolean stagnant;

    /** 使用默认值创建预算。 */
    public AgentBudget() {
        this(DEFAULT_TOKEN_BUDGET, DEFAULT_STAGNATION_WINDOW, DEFAULT_HARD_MAX_ITERATIONS);
    }

    /**
     * @param tokenBudget       累计 token 上限
     * @param stagnationWindow  停滞检测窗口（连续相同工具调用轮数）
     * @param hardMaxIterations  硬轮数上限
     */
    public AgentBudget(int tokenBudget, int stagnationWindow, int hardMaxIterations) {
        if (tokenBudget <= 0) throw new IllegalArgumentException("tokenBudget must be positive");
        if (stagnationWindow < 2) throw new IllegalArgumentException("stagnationWindow must be >= 2");
        if (hardMaxIterations <= 0) throw new IllegalArgumentException("hardMaxIterations must be positive");
        this.tokenBudget = tokenBudget;
        this.stagnationWindow = stagnationWindow;
        this.hardMaxIterations = hardMaxIterations;
    }

    /** 进入新一轮迭代，返回当前轮次（从 1 开始）。 */
    public int beginIteration() {
        return ++iteration;
    }

    /** 记录本轮 token 消耗。 */
    public void recordTokens(int inputTokens, int outputTokens) {
        recordTokens(inputTokens, outputTokens, 0);
    }

    public void recordTokens(int inputTokens, int outputTokens, int cachedInputTokens) {
        this.totalInputTokens += Math.max(0, inputTokens);
        this.totalOutputTokens += Math.max(0, outputTokens);
        this.totalCachedInputTokens += Math.max(0, cachedInputTokens);
    }

    /**
     * 记录本轮工具调用签名并判断是否进入停滞。
     * <p>
     * 停滞条件：最近 stagnationWindow 轮的"工具名+参数"完全相同。
     * 一旦判定为停滞，状态保持，后续 {@link #check()} 返回 STAGNATION_DETECTED。
     * </p>
     */
    public void recordToolCalls(List<ouccs.smy.paiclilearn.llm.LlmClient.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            recentToolSignatures.clear();
            return;
        }
        String signature = signatureOf(toolCalls);
        recentToolSignatures.addLast(signature);
        while (recentToolSignatures.size() > stagnationWindow) {
            recentToolSignatures.removeFirst();
        }
        if (recentToolSignatures.size() == stagnationWindow) {
            String first = recentToolSignatures.peekFirst();
            stagnant = recentToolSignatures.stream().allMatch(sig -> sig.equals(first));
        }
    }

    /** 检查当前是否超出预算。返回 WITHIN_BUDGET 则可以继续。 */
    public ExitReason check() {
        if (stagnant) return ExitReason.STAGNATION_DETECTED;
        if (totalInputTokens + totalOutputTokens >= tokenBudget) return ExitReason.TOKEN_BUDGET_EXCEEDED;
        if (iteration >= hardMaxIterations) return ExitReason.HARD_ITERATION_LIMIT;
        return ExitReason.WITHIN_BUDGET;
    }

    // ---- getters ----

    public int iteration() { return iteration; }
    public int totalInputTokens() { return totalInputTokens; }
    public int totalOutputTokens() { return totalOutputTokens; }
    public int totalCachedInputTokens() { return totalCachedInputTokens; }
    public int tokenBudget() { return tokenBudget; }
    public int hardMaxIterations() { return hardMaxIterations; }
    public int stagnationWindow() { return stagnationWindow; }

    /** 人类可读的退出原因描述。 */
    public String describeExit(ExitReason reason) {
        return switch (reason) {
            case WITHIN_BUDGET -> "未触发兜底条件";
            case TOKEN_BUDGET_EXCEEDED -> String.format(Locale.ROOT,
                    "Token 预算已用尽（%d / %d），任务被强制收尾",
                    totalInputTokens + totalOutputTokens, tokenBudget);
            case STAGNATION_DETECTED -> String.format(Locale.ROOT,
                    "检测到连续 %d 轮重复的工具调用，疑似死循环，已强制收尾",
                    stagnationWindow);
            case HARD_ITERATION_LIMIT -> String.format(Locale.ROOT,
                    "达到硬轮数上限（%d），已强制收尾", hardMaxIterations);
        };
    }

    // ---- internal ----

    private static String signatureOf(List<ouccs.smy.paiclilearn.llm.LlmClient.ToolCall> toolCalls) {
        StringBuilder sb = new StringBuilder();
        for (var tc : toolCalls) {
            sb.append(tc.function().name()).append('|').append(tc.function().arguments()).append(';');
        }
        return sb.toString();
    }
}
