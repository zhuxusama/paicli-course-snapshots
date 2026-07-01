package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.cli.PlanReviewInputParser;
import ouccs.smy.paiclilearn.cli.PlanReviewInputParser.Decision;
import ouccs.smy.paiclilearn.cli.PlanReviewInputParser.DecisionType;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.plan.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * s11 的控制台教程测试：学习 Plan 审阅、执行、补充重规划和取消闭环。
 * <p>
 * 本章的新能力不是“再多一个数据结构”，而是把 s10 的 DAG 变成可审阅、可执行的流程。
 * 因此 DemoTest 重点展示：用户输入如何变成审阅决策，PlanExecuteAgent 如何按计划推进任务。
 */
class PlanDemoTest {

    @Test
    void demoReviewInputParserDecisions() {
        System.out.println("【场景】计划生成后，用户需要在执行前做选择：执行、补充要求、或取消。");

        System.out.println("【输入】模拟审阅界面的几种输入。");
        List<String> inputs = List.of("", "run", "请先补充测试步骤", "cancel", "\u001B");
        inputs.forEach(input -> System.out.println("  input = " + printable(input)));

        System.out.println("【执行】调用 PlanReviewInputParser.parse(input)。");
        List<Decision> decisions = inputs.stream()
                .map(PlanReviewInputParser::parse)
                .toList();

        System.out.println("【输出】空输入/run -> EXECUTE；普通文本 -> SUPPLEMENT；cancel/ESC -> CANCEL。");
        for (int i = 0; i < inputs.size(); i++) {
            Decision decision = decisions.get(i);
            System.out.println("  " + printable(inputs.get(i)) + " => "
                    + decision.type()
                    + (decision.feedback() == null ? "" : " feedback=" + decision.feedback()));
        }

        assertEquals(DecisionType.EXECUTE, decisions.get(0).type());
        assertEquals(DecisionType.EXECUTE, decisions.get(1).type());
        assertEquals(DecisionType.SUPPLEMENT, decisions.get(2).type());
        assertEquals("请先补充测试步骤", decisions.get(2).feedback());
        assertEquals(DecisionType.CANCEL, decisions.get(3).type());
        assertEquals(DecisionType.CANCEL, decisions.get(4).type());
    }

    @Test
    void demoPlanExecuteHappyPath() {
        System.out.println("【场景】s10 只能生成 DAG；s11 要在审阅通过后真正执行每个 task。");

        System.out.println("【输入】给 PlanExecuteAgent 一个复杂目标，并准备 LLM 的确定性响应。");
        String goal = "请先读取 pom.xml，然后根据内容验证项目结构并给出总结";
        QueueLlmClient llm = new QueueLlmClient(
                planJson("""
                        {
                          "summary": "读取项目配置并验证结构",
                          "tasks": [
                            {"id": "read", "description": "读取 pom.xml", "type": "FILE_READ", "dependencies": []},
                            {"id": "verify", "description": "验证项目结构", "type": "VERIFICATION", "dependencies": ["read"]}
                          ]
                        }
                        """),
                "pom.xml 显示这是一个 Maven 项目",
                "项目结构验证通过"
        );

        PlanExecuteAgent.PlanReviewHandler reviewHandler = (reviewGoal, plan) -> {
            System.out.println("【执行】审阅回调收到计划：");
            System.out.println("  goal = " + reviewGoal);
            System.out.println("  summary = " + plan.getSummary());
            System.out.println("  order = " + plan.getExecutionOrder());
            System.out.println("  decision = EXECUTE");
            return Decision.execute();
        };

        System.out.println("【执行】调用 PlanExecuteAgent.run(goal)：Planner 生成计划 -> 审阅 -> 按拓扑序执行任务。");
        PlanExecuteAgent agent = new PlanExecuteAgent(llm, reviewHandler);
        String result = agent.run(goal);

        System.out.println("【输出】最终返回叶子任务结果；依赖任务结果会作为后续任务上下文。");
        System.out.println("  result = " + result);

        assertTrue(result.contains("项目结构验证通过"));
        assertEquals(3, llm.callCount());
        assertFalse(result.contains("取消"));
    }

    @Test
    void demoSupplementTriggersReplanBeforeExecution() {
        System.out.println("【场景】用户看到计划后发现少了测试步骤，可以补充要求，让 Planner 重新生成 DAG。");

        System.out.println("【输入】第一次计划缺少测试；审阅回调第一次返回 SUPPLEMENT，第二次才 EXECUTE。");
        QueueLlmClient llm = new QueueLlmClient(
                planJson("""
                        {
                          "summary": "只实现功能",
                          "tasks": [
                            {"id": "impl", "description": "实现功能", "type": "FILE_WRITE", "dependencies": []}
                          ]
                        }
                        """),
                planJson("""
                        {
                          "summary": "实现功能并补充测试",
                          "tasks": [
                            {"id": "impl", "description": "实现功能", "type": "FILE_WRITE", "dependencies": []},
                            {"id": "test", "description": "补充测试", "type": "VERIFICATION", "dependencies": ["impl"]}
                          ]
                        }
                        """),
                "功能代码已写入",
                "测试已补齐并通过"
        );

        PlanExecuteAgent.PlanReviewHandler reviewHandler = new PlanExecuteAgent.PlanReviewHandler() {
            private int reviewCount;

            @Override
            public Decision review(String reviewGoal, ExecutionPlan plan) {
                reviewCount++;
                System.out.println("【执行】第 " + reviewCount + " 次审阅：summary = " + plan.getSummary());
                if (reviewCount == 1) {
                    System.out.println("  decision = SUPPLEMENT feedback=请补充测试步骤");
                    return Decision.supplement("请补充测试步骤");
                }
                System.out.println("  decision = EXECUTE");
                return Decision.execute();
            }
        };

        System.out.println("【执行】调用 PlanExecuteAgent.run(goal)，观察补充要求如何触发重新规划。");
        PlanExecuteAgent agent = new PlanExecuteAgent(llm, reviewHandler);
        String result = agent.run("请实现一个功能，并确保它有验证步骤");

        System.out.println("【输出】最终执行的是第二版计划，结果来自补充后的测试任务。");
        System.out.println("  result = " + result);

        assertTrue(result.contains("测试已补齐并通过"));
        assertEquals(4, llm.callCount());
    }

    @Test
    void demoCancelStopsBeforeTaskExecution() {
        System.out.println("【场景】计划生成后，用户可以取消，任务不应进入执行阶段。");

        System.out.println("【输入】准备一个会生成计划的目标，但审阅回调返回 CANCEL。");
        QueueLlmClient llm = new QueueLlmClient(planJson("""
                {
                  "summary": "准备取消的计划",
                  "tasks": [
                    {"id": "only", "description": "不会执行的任务", "type": "COMMAND", "dependencies": []}
                  ]
                }
                """));

        System.out.println("【执行】调用 PlanExecuteAgent.run(goal)，审阅阶段返回取消。");
        PlanExecuteAgent agent = new PlanExecuteAgent(llm, (goal, plan) -> {
            System.out.println("  review summary = " + plan.getSummary());
            System.out.println("  decision = CANCEL");
            return Decision.cancel();
        });
        String result = agent.run("请创建一个会被取消的复杂计划");

        System.out.println("【输出】返回取消信息，并且 LLM 只被调用一次用于生成计划，没有进入 task 执行。");
        System.out.println("  result = " + result);

        assertTrue(result.contains("取消"));
        assertEquals(1, llm.callCount());
    }

    private static String planJson(String json) {
        return json.strip();
    }

    private static String printable(String input) {
        if (input == null || input.isEmpty()) {
            return "<Enter>";
        }
        if ("\u001B".equals(input)) {
            return "<ESC>";
        }
        return input;
    }

    static class QueueLlmClient implements LlmClient {
        private final Queue<String> responses = new ArrayDeque<>();
        private int calls;

        QueueLlmClient(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            calls++;
            String content = responses.isEmpty() ? "默认任务结果" : responses.remove();
            return new ChatResponse("assistant", content, null, null, 0, 0, 0);
        }

        int callCount() {
            return calls;
        }

        @Override
        public String getModelName() {
            return "stub-plan-model";
        }

        @Override
        public String getProviderName() {
            return "stub";
        }
    }
}
