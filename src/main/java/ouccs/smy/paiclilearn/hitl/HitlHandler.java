package ouccs.smy.paiclilearn.hitl;

/**
 * HITL 处理器边界：同步询问人工审批。
 *
 * @since s05 [s05 新增]
 */
public interface HitlHandler {
    ApprovalResult requestApproval(ApprovalRequest request);

    boolean isEnabled();
    void setEnabled(boolean enabled);

    /** 当前会话是否已对该工具选择"全部允许"。 */
    default boolean isApprovedAllForTool(String toolName) { return false; }

    /** 清空"全部允许"记录。 */
    default void clearApprovedAll() {}
}
