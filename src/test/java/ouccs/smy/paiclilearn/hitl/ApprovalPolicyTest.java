package ouccs.smy.paiclilearn.hitl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** [s05 新增] 验证 ApprovalPolicy 的审批判断逻辑。 */
class ApprovalPolicyTest {
    @Test void dangerousToolsIncludeSideEffects() {
        var set = ApprovalPolicy.dangerousTools();
        assertTrue(set.contains("write_file"));
        assertTrue(set.contains("execute_command"));
        assertTrue(set.contains("create_project"));
    }
    @Test void readOnlyToolsDontRequireApproval() {
        assertFalse(ApprovalPolicy.requiresApproval("read_file"));
        assertFalse(ApprovalPolicy.requiresApproval("grep_code"));
    }
    @Test void sideEffectToolsRequireApproval() {
        assertTrue(ApprovalPolicy.requiresApproval("write_file"));
        assertTrue(ApprovalPolicy.requiresApproval("execute_command"));
    }
    @Test void dangerLevelsAreDistinct() {
        assertTrue(ApprovalPolicy.dangerLevel("execute_command").contains("高危"));
        assertTrue(ApprovalPolicy.dangerLevel("write_file").contains("中危"));
        assertTrue(ApprovalPolicy.dangerLevel("read_file").contains("安全"));
    }
}
