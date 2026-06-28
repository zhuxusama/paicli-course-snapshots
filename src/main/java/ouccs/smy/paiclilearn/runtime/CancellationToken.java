package ouccs.smy.paiclilearn.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可传播的取消令牌——线程安全的取消标志。
 * <p>
 * [s13 新增] 用于让 /cancel 命令通知正在执行的 Agent 循环停止。
 * 每个 Agent 运行对应一个 CancellationToken 实例。
 * </p>
 *
 * @since s13
 */
public class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** 发出取消信号。幂等——多次调用效果相同。 */
    public void cancel() {
        cancelled.set(true);
    }

    /** 检查是否已被取消。也响应线程中断。 */
    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }
}
