package ouccs.smy.paiclilearn.hitl;

/**
 * 审批决策类型枚举。
 *
 * @since s05 [s05 新增]
 */
public enum Decision {
    APPROVED,              // 单次通过
    APPROVED_ALL,          // 本次会话同工具全部通过
    REJECTED,              // 拒绝
    MODIFIED,              // 以修改后的参数允许
    SKIPPED                // 跳过此步，继续执行
}
