package ouccs.smy.paiclilearn.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** [s11 新增] 验证 PlanReviewInputParser 输入→决策映射。 */
class PlanReviewInputParserTest {

    @Test void emptyIsExecute() {
        assertEquals(PlanReviewInputParser.DecisionType.EXECUTE,
                PlanReviewInputParser.parse("").type());
    }

    @Test void nullIsExecute() {
        assertEquals(PlanReviewInputParser.DecisionType.EXECUTE,
                PlanReviewInputParser.parse(null).type());
    }

    @Test void yesIsExecute() {
        assertEquals(PlanReviewInputParser.DecisionType.EXECUTE,
                PlanReviewInputParser.parse("yes").type());
        assertEquals(PlanReviewInputParser.DecisionType.EXECUTE,
                PlanReviewInputParser.parse("y").type());
    }

    @Test void runIsExecute() {
        assertEquals(PlanReviewInputParser.DecisionType.EXECUTE,
                PlanReviewInputParser.parse("run").type());
    }

    @Test void escIsCancel() {
        assertEquals(PlanReviewInputParser.DecisionType.CANCEL,
                PlanReviewInputParser.parse("").type());
    }

    @Test void cancelWordIsCancel() {
        assertEquals(PlanReviewInputParser.DecisionType.CANCEL,
                PlanReviewInputParser.parse("cancel").type());
    }

    @Test void arbitraryTextIsSupplement() {
        var d = PlanReviewInputParser.parse("需要添加错误处理");
        assertEquals(PlanReviewInputParser.DecisionType.SUPPLEMENT, d.type());
        assertEquals("需要添加错误处理", d.feedback());
    }

    @Test void factoryMethods() {
        assertEquals(PlanReviewInputParser.DecisionType.EXECUTE,
                PlanReviewInputParser.Decision.execute().type());
        assertEquals(PlanReviewInputParser.DecisionType.CANCEL,
                PlanReviewInputParser.Decision.cancel().type());
        var s = PlanReviewInputParser.Decision.supplement("msg");
        assertEquals(PlanReviewInputParser.DecisionType.SUPPLEMENT, s.type());
        assertEquals("msg", s.feedback());
    }
}
