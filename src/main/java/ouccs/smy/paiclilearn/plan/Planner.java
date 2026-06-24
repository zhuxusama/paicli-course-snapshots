package ouccs.smy.paiclilearn.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ouccs.smy.paiclilearn.llm.LlmClient;

import java.io.IOException;
import java.util.*;

/**
 * 计划生成器——将用户目标转换为可执行的 DAG 计划。
 * <p>
 * [s10 新增] 核心流程：用户目标 → LLM 生成 JSON 计划 → parsePlan → ExecutionPlan。
 * {@link #createMinimalPlan(String)} 提供无 LLM 的快速计划，
 * 适合简单目标或在测试中直接验证 DAG 结构。</p>
 *
 * @since s10
 */
/** [s10 新增] */
public class Planner {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LlmClient llmClient;

    public Planner(LlmClient llmClient) { this.llmClient = llmClient; }

    /**
     * 创建纯本地最小计划——将目标直接拆分为固定模式的 DAG。
     * 适合简单目标和测试验证，不使用 LLM。
     */
    public ExecutionPlan createMinimalPlan(String goal) {
        String planId = "plan_" + UUID.randomUUID().toString().substring(0, 8);
        ExecutionPlan plan = new ExecutionPlan(planId, goal);

        Task t1 = new Task("task_1", "分析目标: " + goal, Task.TaskType.ANALYSIS);
        Task t2 = new Task("task_2", "实现 " + goal, Task.TaskType.FILE_WRITE, List.of("task_1"));
        Task t3 = new Task("task_3", "验证 " + goal, Task.TaskType.VERIFICATION, List.of("task_2"));

        plan.addTask(t1);
        plan.addTask(t2);
        plan.addTask(t3);
        plan.setSummary("最小计划: " + goal);
        return plan;
    }

    /**
     * 通过 LLM 创建完整的计划。
     * @param goal 用户目标
     * @return 解析后的 ExecutionPlan
     * @throws IOException  LLM 调用或 JSON 解析失败
     */
    public ExecutionPlan createPlan(String goal) throws IOException {
        String systemPrompt = """
                你是一个计划生成器。请将用户的目标拆分为可执行的任务列表（DAG）。
                返回 JSON 格式：
                {
                  "summary": "计划概要",
                  "tasks": [
                    {"id": "1", "description": "描述", "type": "FILE_READ|FILE_WRITE|COMMAND|ANALYSIS|VERIFICATION", "dependencies": []}
                  ]
                }
                每个任务只需描述做什么，不要实现细节。
                """;

        var response = llmClient.chat(
                List.of(LlmClient.Message.system(systemPrompt),
                        LlmClient.Message.user(goal)),
                List.of()
        );

        String content = response.content();
        if (content == null || content.isBlank()) {
            return createMinimalPlan(goal);
        }
        return parsePlan(goal, content);
    }

    /** 解析 LLM 返回的 JSON 计划字符串为 ExecutionPlan。 */
    public ExecutionPlan parsePlan(String goal, String planJson) throws IOException {
        // 去除 markdown 代码块标记
        String json = planJson
                .replaceAll("```(?:json)?", "")
                .trim();

        JsonNode root = MAPPER.readTree(json);
        String summary = root.path("summary").asText("");

        ExecutionPlan plan = new ExecutionPlan(
                "plan_" + UUID.randomUUID().toString().substring(0, 8), goal);
        plan.setSummary(summary);

        // 第一遍：创建所有 Task
        JsonNode tasks = root.path("tasks");
        if (tasks.isArray()) {
            Map<String, String> idMapping = new LinkedHashMap<>();
            int idx = 0;
            for (JsonNode taskNode : tasks) {
                idx++;
                String origId = taskNode.path("id").asText(String.valueOf(idx));
                String newId = "task_" + idx;
                idMapping.put(origId, newId);
            }

            idx = 0;
            for (JsonNode taskNode : tasks) {
                idx++;
                String desc = taskNode.path("description").asText("");
                String typeStr = taskNode.path("type").asText("COMMAND");
                Task.TaskType type = parseType(typeStr);
                List<String> deps = new ArrayList<>();
                JsonNode depsNode = taskNode.path("dependencies");
                if (depsNode.isArray()) {
                    for (JsonNode d : depsNode) {
                        String mapped = idMapping.get(d.asText());
                        if (mapped != null) deps.add(mapped);
                    }
                }
                Task task = new Task("task_" + idx, desc, type, deps);
                plan.addTask(task);
                plan.setSummary(summary);
            }
        } else {
            return createMinimalPlan(goal);
        }

        boolean acyclic = plan.computeExecutionOrder();
        if (!acyclic) {
            throw new IOException("计划包含循环依赖");
        }
        return plan;
    }

    private Task.TaskType parseType(String type) {
        return switch (type.toUpperCase()) {
            case "FILE_READ" -> Task.TaskType.FILE_READ;
            case "FILE_WRITE" -> Task.TaskType.FILE_WRITE;
            case "COMMAND" -> Task.TaskType.COMMAND;
            case "ANALYSIS" -> Task.TaskType.ANALYSIS;
            case "VERIFICATION" -> Task.TaskType.VERIFICATION;
            default -> Task.TaskType.PLANNING;
        };
    }
}
