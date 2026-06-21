package ouccs.smy.paiclilearn.hitl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** [s05 新增] 验证 ApprovalResult 的各种决策状态。 */
class ApprovalResultTest {
    @Test void approve() { assertTrue(ApprovalResult.approve().isApproved()); }
    @Test void approveAll() { assertTrue(ApprovalResult.approveAll().isApproved()); }
    @Test void reject() { assertTrue(ApprovalResult.reject("危险").isRejected()); }
    @Test void modify() { assertTrue(ApprovalResult.modify("{}").isApproved()); }
    @Test void skipIsNotApproved() { assertFalse(ApprovalResult.skip().isApproved()); }
    @Test void rejectHasReason() { assertEquals("测试拒绝", ApprovalResult.reject("测试拒绝").reason()); }
    @Test void modifyHasNewArgs() { assertEquals("{}", ApprovalResult.modify("{}").modifiedArguments()); }
}
