package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.cli.PlanReviewInputParser.Decision;
import ouccs.smy.paiclilearn.llm.LlmClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** [s11 新增] 验证 PlanExecuteAgent 审阅循环、执行和异常处理。 */
class PlanExecuteAgentTest {

    @Test void runsSimpleGoalAndReturnsResult() {
        var a = new PlanExecuteAgent(new StubClient("done"));
        String r = a.run("列出文件");
        assertNotNull(r);
    }

    @Test void cancelDecisionStopsExecution() {
        var a = new PlanExecuteAgent(new StubClient("{}"),
                (g, p) -> Decision.cancel());
        assertTrue(a.run("x").contains("取消"));
    }

    @Test void supplementThenExecute() {
        var a = new PlanExecuteAgent(new StubClient("{}"),
                new PlanExecuteAgent.PlanReviewHandler() {
                    int c;
                    public Decision review(String g,
                            ouccs.smy.paiclilearn.plan.ExecutionPlan p) {
                        c++;
                        if (c == 1) return Decision.supplement("more");
                        return Decision.execute();
                    }
                });
        assertNotNull(a.run("先读取然后验证"));
    }

    @Test void exceedReplanLimitReturnsError() {
        var a = new PlanExecuteAgent(new StubClient("{}"),
                (g, p) -> Decision.supplement("always more"));
        String r = a.run("先读取然后验证");
        assertTrue(r.contains("重规划"), "should hit limit: " + r);
    }

    @Test void constructsWithDefaults() {
        assertNotNull(new PlanExecuteAgent(new StubClient("ok")));
    }

    static class StubClient implements LlmClient {
        private final String c;
        StubClient(String c) { this.c = c; }
        public ChatResponse chat(List<Message> m, List<Tool> t, StreamListener l) {
            return new ChatResponse("assistant", c, null, null, 0, 0, 0);
        }
        public String getModelName() { return "stub"; }
        public String getProviderName() { return "stub"; }
    }
}
