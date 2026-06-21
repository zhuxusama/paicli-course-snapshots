package ouccs.smy.paiclilearn.policy;

/**
 * 策略层通用异常。
 * 当 PathGuard 检测到路径逃逸、CommandGuard 阻断危险命令时抛出。
 *
 * @since s05 [s05 新增]
 */
public class PolicyException extends RuntimeException {
    public PolicyException(String message) { super(message); }
}
