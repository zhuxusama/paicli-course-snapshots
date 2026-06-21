package ouccs.smy.paiclilearn.hitl;

/**
 * 单次审批结果。
 *
 * @param decision          审批决策
 * @param modifiedArguments MODIFIED 时的修改后参数，否则为 null
 * @param reason            REJECTED 时的拒绝原因，否则为 null
 * @since s05 [s05 新增]
 */
public record ApprovalResult(Decision decision, String modifiedArguments, String reason) {
    public static ApprovalResult approve()      { return new ApprovalResult(Decision.APPROVED, null, null); }
    public static ApprovalResult approveAll()   { return new ApprovalResult(Decision.APPROVED_ALL, null, null); }
    public static ApprovalResult reject(String r){ return new ApprovalResult(Decision.REJECTED, null, r); }
    public static ApprovalResult modify(String m){ return new ApprovalResult(Decision.MODIFIED, m, null); }
    public static ApprovalResult skip()         { return new ApprovalResult(Decision.SKIPPED, null, null); }

    public boolean isApproved() { return decision == Decision.APPROVED || decision == Decision.APPROVED_ALL || decision == Decision.MODIFIED; }
    public boolean isRejected() { return decision == Decision.REJECTED; }
    public boolean isSkipped()  { return decision == Decision.SKIPPED; }
}
