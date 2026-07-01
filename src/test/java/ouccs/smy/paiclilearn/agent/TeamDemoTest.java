package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * s12 教学演示：把 s11 的“单 Agent 执行计划”升级成 Planner / Worker / Reviewer 团队协作。
 *
 * <p>这个测试不是只看绿灯，而是故意把输入、调度、审核和输出打印出来。运行后可以从控制台看到：</p>
 * <ol>
 *   <li>Planner 如何把用户目标拆成 JSON 步骤；</li>
 *   <li>AgentOrchestrator 如何把依赖 ID 规范化成 step_1 / step_2；</li>
 *   <li>Worker 如何执行 ready 步骤，Reviewer 如何审核；</li>
 *   <li>Reviewer 驳回时，Orchestrator 如何给 Worker 一次修正机会。</li>
 * </ol>
 */
class TeamDemoTest {

    @Test
    void demoPlannerWorkerReviewerPipeline() {
        String goal = "创建登录页面并补充测试";
        String planJson = """
                {
                  "tasks": [
                    {"id": "analysis", "description": "拆解登录页需求", "type": "ANALYSIS", "dependencies": []},
                    {"id": "view", "description": "实现登录页 HTML", "type": "FILE_WRITE", "dependencies": ["analysis"]},
                    {"id": "test", "description": "补充表单验证测试", "type": "VERIFICATION", "dependencies": ["analysis"]}
                  ]
                }
                """;

        var llm = ScriptedTeamClient.approving(planJson);
        var orchestrator = new AgentOrchestrator(llm, new ToolRegistry());

        System.out.println("【场景】s12 把 s11 的顺序执行升级为 Multi-Agent 团队协作。");
        System.out.println("       Planner 只负责拆计划；Worker 负责执行；Reviewer 负责验收。");
        System.out.println("【输入】用户目标 = " + goal);
        System.out.println("       Planner 将返回 3 个步骤，其中 step_2 / step_3 都依赖 step_1：");
        System.out.println(planJson);

        System.out.println("【执行】AgentOrchestrator.run(goal)");
        System.out.println("       1. Planner 生成 JSON 计划");
        System.out.println("       2. Orchestrator 解析依赖，把 analysis/view/test 规范化为 step_1/step_2/step_3");
        System.out.println("       3. ready 步骤交给 Worker，执行结果再交给 Reviewer 审核");

        String result = orchestrator.run(goal);

        System.out.println("【输出】最终汇总：");
        System.out.println(result);
        System.out.println("       LLM 调用轨迹：");
        llm.calls().forEach(call -> System.out.println("       - " + call));

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("[step_1]"));
        assertTrue(result.contains("[step_2]"));
        assertTrue(result.contains("[step_3]"));
        assertEquals(1, llm.countCalls("PLANNER"));
        assertEquals(3, llm.countCalls("WORKER"));
        assertEquals(3, llm.countCalls("REVIEWER"));
    }

    @Test
    void demoParsePlanAndDependencyBatching() {
        String rawPlan = """
                {
                  "steps": [
                    {"id": "1", "description": "分析需求", "type": "ANALYSIS", "dependencies": []},
                    {"id": "2", "description": "实现代码", "type": "FILE_WRITE", "dependencies": ["1"]},
                    {"id": "3", "description": "编写测试", "type": "VERIFICATION", "dependencies": ["1"]}
                  ]
                }
                """;
        var orchestrator = new AgentOrchestrator(ScriptedTeamClient.approving(rawPlan), new ToolRegistry());

        System.out.println("【场景】观察 Planner JSON 被解析成可调度步骤。");
        System.out.println("【输入】Planner 原始 JSON：");
        System.out.println(rawPlan);

        List<AgentOrchestrator.ExecutionStep> steps = orchestrator.parseSteps(rawPlan);
        List<String> firstBatch = steps.stream()
                .filter(step -> step.dependencies.isEmpty())
                .map(step -> step.id)
                .toList();
        List<String> secondBatch = steps.stream()
                .filter(step -> step.dependencies.contains("step_1"))
                .map(step -> step.id)
                .toList();

        System.out.println("【执行】parseSteps 会把数字 ID 统一改写为 step_N，并同步改写 dependencies。");
        steps.forEach(step -> System.out.println("       " + step.id
                + " | type=" + step.type
                + " | dependencies=" + step.dependencies
                + " | description=" + step.description));

        System.out.println("【输出】调度含义：");
        System.out.println("       第一批 ready = " + firstBatch);
        System.out.println("       step_1 完成后，第二批 ready = " + secondBatch);

        assertEquals(List.of("step_1"), firstBatch);
        assertEquals(List.of("step_2", "step_3"), secondBatch);
        assertEquals(List.of("step_1"), steps.get(1).dependencies);
        assertEquals(List.of("step_1"), steps.get(2).dependencies);
    }

    @Test
    void demoReviewerRejectionTriggersOneRetry() {
        String planJson = """
                {
                  "tasks": [
                    {"id": "1", "description": "生成 README 使用说明", "type": "FILE_WRITE", "dependencies": []}
                  ]
                }
                """;
        var llm = ScriptedTeamClient.rejectFirstReviewThenApprove(planJson);
        var orchestrator = new AgentOrchestrator(llm, new ToolRegistry());

        System.out.println("【场景】Reviewer 第一次驳回，Worker 获得一次修正机会。");
        System.out.println("【输入】单步骤计划：");
        System.out.println(planJson);

        System.out.println("【执行】第一次 Worker 输出故意缺少测试说明；Reviewer 返回 approved=false。");
        String result = orchestrator.run("生成 README 使用说明");

        System.out.println("【输出】修正后的结果：");
        System.out.println(result);
        System.out.println("       LLM 调用轨迹：");
        llm.calls().forEach(call -> System.out.println("       - " + call));

        assertTrue(result.contains("[step_1] COMPLETED"));
        assertTrue(result.contains("第二版"));
        assertEquals(1, llm.countCalls("PLANNER"));
        assertEquals(2, llm.countCalls("WORKER"));
        assertEquals(2, llm.countCalls("REVIEWER"));
    }

    static class ScriptedTeamClient implements LlmClient {
        private final String planJson;
        private final boolean rejectFirstReview;
        private final AtomicInteger totalCalls = new AtomicInteger();
        private final AtomicInteger workerCalls = new AtomicInteger();
        private final AtomicInteger reviewerCalls = new AtomicInteger();
        private final List<String> calls = new CopyOnWriteArrayList<>();

        static ScriptedTeamClient approving(String planJson) {
            return new ScriptedTeamClient(planJson, false);
        }

        static ScriptedTeamClient rejectFirstReviewThenApprove(String planJson) {
            return new ScriptedTeamClient(planJson, true);
        }

        private ScriptedTeamClient(String planJson, boolean rejectFirstReview) {
            this.planJson = planJson;
            this.rejectFirstReview = rejectFirstReview;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            totalCalls.incrementAndGet();
            String role = roleFromSystemPrompt(messages);
            String latestUserMessage = latestUserMessage(messages);

            if ("PLANNER".equals(role)) {
                calls.add("PLANNER 生成计划 JSON");
                return text(planJson);
            }

            if ("REVIEWER".equals(role)) {
                int reviewNo = reviewerCalls.incrementAndGet();
                if (rejectFirstReview && reviewNo == 1) {
                    calls.add("REVIEWER 第 1 次审核：驳回，要求补充测试说明");
                    return text("{\"approved\": false, \"summary\": \"缺少测试说明，请补充后重试\"}");
                }
                calls.add("REVIEWER 第 " + reviewNo + " 次审核：通过");
                return text("{\"approved\": true, \"summary\": \"通过\"}");
            }

            int workerNo = workerCalls.incrementAndGet();
            String version = workerNo == 1 ? "第一版" : "第二版";
            calls.add("WORKER 第 " + workerNo + " 次执行：" + summarize(latestUserMessage));
            return text(version + " Worker 输出：已完成任务；"
                    + (workerNo == 1 ? "待 Reviewer 检查。" : "已补充测试说明。"));
        }

        int countCalls(String prefix) {
            return (int) calls.stream().filter(call -> call.startsWith(prefix)).count();
        }

        List<String> calls() {
            return new ArrayList<>(calls);
        }

        @Override
        public String getModelName() {
            return "scripted-team";
        }

        @Override
        public String getProviderName() {
            return "demo";
        }

        private static ChatResponse text(String content) {
            return new ChatResponse("assistant", content, null, null, 0, 0, 0);
        }

        private static String roleFromSystemPrompt(List<Message> messages) {
            if (messages.isEmpty()) {
                return "UNKNOWN";
            }
            String systemPrompt = messages.get(0).content();
            if (systemPrompt.contains("PLANNER")) {
                return "PLANNER";
            }
            if (systemPrompt.contains("REVIEWER")) {
                return "REVIEWER";
            }
            if (systemPrompt.contains("WORKER")) {
                return "WORKER";
            }
            return "UNKNOWN";
        }

        private static String latestUserMessage(List<Message> messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                if ("user".equals(message.role())) {
                    return message.content();
                }
            }
            return "";
        }

        private static String summarize(String message) {
            String compact = message.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            return compact.length() <= 42 ? compact : compact.substring(0, 42) + "...";
        }
    }
}
