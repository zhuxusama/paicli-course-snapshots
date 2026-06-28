package ouccs.smy.paiclilearn.runtime;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 取消上下文——管理当前运行任务的 CancellationToken。
 * <p>
 * [s13 新增] 提供全局 + ThreadLocal 两层存储，确保：
 * <ul>
 *   <li>主线程通过 {@link #startRun()} 创建 token</li>
 *   <li>子线程通过 {@link #current()} 可获取到 token（InheritableThreadLocal）</li>
 *   <li>{@link #isCancelled()} 提供便捷的取消状态查询</li>
 * </ul>
 * </p>
 *
 * @since s13
 */
public final class CancellationContext {
    private static final AtomicReference<CancellationToken> CURRENT = new AtomicReference<>();
    private static final InheritableThreadLocal<CancellationToken> LOCAL = new InheritableThreadLocal<>();

    private CancellationContext() {}

    /** 开始一次运行，创建并注册新的 CancellationToken。 */
    public static CancellationToken startRun() {
        CancellationToken token = new CancellationToken();
        CURRENT.set(token);
        LOCAL.set(token);
        return token;
    }

    /** 获取当前线程关联的 CancellationToken。 */
    public static CancellationToken current() {
        CancellationToken token = LOCAL.get();
        return token == null ? CURRENT.get() : token;
    }

    /** 便捷方法：检查当前运行是否已被取消。 */
    public static boolean isCancelled() {
        CancellationToken token = current();
        return token != null && token.isCancelled();
    }

    /** 清理指定 token 的注册。 */
    public static void clear(CancellationToken token) {
        if (LOCAL.get() == token) {
            LOCAL.remove();
        }
        CURRENT.compareAndSet(token, null);
    }
}
