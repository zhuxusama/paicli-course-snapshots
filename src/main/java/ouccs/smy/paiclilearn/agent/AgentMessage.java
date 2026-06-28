package ouccs.smy.paiclilearn.agent;

/**
 * Agent 间通信消息——Multi-Agent 协作的基本通信单元。
 * <p>
 * [s12 新增] 消息类型说明：
 * <ul>
 *   <li>TASK:      主控分配给子代理的任务</li>
 *   <li>RESULT:    子代理返回的执行结果</li>
 *   <li>FEEDBACK:  检查者对结果的反馈（可能包含改进建议）</li>
 *   <li>APPROVAL:  检查者认可结果</li>
 *   <li>REJECTION: 检查者拒绝结果，需要重新执行</li>
 *   <li>ERROR:     子代理在执行过程中遭遇系统级错误</li>
 * </ul>
 * </p>
 *
 * @since s12
 */
public record AgentMessage(
        String fromAgent,
        AgentRole fromRole,
        String content,
        Type type
) {
    /** 消息类型枚举。 @since s12 */
    public enum Type {
        TASK,
        RESULT,
        FEEDBACK,
        APPROVAL,
        REJECTION,
        ERROR
    }

    /**
     * 创建任务消息（主控 → 子代理）。
     * @param fromAgent 发送方标识
     * @param content   任务内容
     * @return TASK 类型消息
     */
    public static AgentMessage task(String fromAgent, String content) {
        return new AgentMessage(fromAgent, null, content, Type.TASK);
    }

    /**
     * 创建结果消息（子代理 → 主控）。
     * @param fromAgent 发送方标识
     * @param role      发送方角色
     * @param content   执行结果
     * @return RESULT 类型消息
     */
    public static AgentMessage result(String fromAgent, AgentRole role, String content) {
        return new AgentMessage(fromAgent, role, content, Type.RESULT);
    }

    /**
     * 创建反馈消息（检查者 → 主控）。
     * @param fromAgent 发送方标识
     * @param content   反馈内容
     * @return FEEDBACK 类型消息
     */
    public static AgentMessage feedback(String fromAgent, String content) {
        return new AgentMessage(fromAgent, AgentRole.REVIEWER, content, Type.FEEDBACK);
    }

    /**
     * 创建审批通过消息。
     * @param fromAgent 发送方标识
     * @param content   审批说明
     * @return APPROVAL 类型消息
     */
    public static AgentMessage approval(String fromAgent, String content) {
        return new AgentMessage(fromAgent, AgentRole.REVIEWER, content, Type.APPROVAL);
    }

    /**
     * 创建拒绝消息（检查者认为结果不合格）。
     * @param fromAgent 发送方标识
     * @param content   拒绝原因
     * @return REJECTION 类型消息
     */
    public static AgentMessage rejection(String fromAgent, String content) {
        return new AgentMessage(fromAgent, AgentRole.REVIEWER, content, Type.REJECTION);
    }

    /**
     * 创建错误消息（子代理执行过程中遇到系统级错误）。
     * @param fromAgent 发送方标识
     * @param role      发送方角色
     * @param content   错误描述
     * @return ERROR 类型消息
     */
    public static AgentMessage error(String fromAgent, AgentRole role, String content) {
        return new AgentMessage(fromAgent, role, content, Type.ERROR);
    }
}
