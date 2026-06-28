package ouccs.smy.paiclilearn.cli;

import ouccs.smy.paiclilearn.agent.Agent;
import ouccs.smy.paiclilearn.hitl.HitlToolRegistry;
import ouccs.smy.paiclilearn.hitl.TerminalHitlHandler;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmClientFactory;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import ouccs.smy.paiclilearn.llm.LlmTraceLogger;
import ouccs.smy.paiclilearn.memory.MemoryManager;
            // [s08 新增]
import ouccs.smy.paiclilearn.tool.ToolRegistry;

import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * s06 CLI 入口：Slash 命令解析 + 模型切换 + 配置优先级。
 * <p>
 * [s06 修改] 与 s05 的区别：
 * <ul>
 *   <li>引入 CliCommandParser 解析 /xxx 命令</li>
 *   <li>未知命令在 CLI 层直接拒绝（不再进入 Agent）</li>
 *   <li>/model 查询当前 provider，并解析需要在重启时生效的目标 provider</li>
 *   <li>/clear 通过 parser 统一路由</li>
 * </ul>
 * </p>
 */
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        LlmConfig config;
        try {
            config = LlmConfig.fromEnvironment();
        } catch (Exception e) {
            System.err.println("配置加载失败: " + e.getMessage());
            System.exit(1);
            return;
        }

        LlmClient llmClient = LlmClientFactory.create(config);
        ToolRegistry toolRegistry = new ToolRegistry();
        HitlToolRegistry hitlRegistry = new HitlToolRegistry(
                toolRegistry, new TerminalHitlHandler(true));
        // [s08 新增] 记忆管理器初始化
        MemoryManager memoryManager = MemoryManager.createDefault(llmClient, hitlRegistry.delegate().getProjectPath().toString());
            // [s08 新增]
        Agent agent = new Agent(llmClient, toolRegistry, memoryManager);
        agent.setHitlRegistry(hitlRegistry);  // [s05] HITL 审批链

        System.out.println("PaiCLI 教学版 v8 (Chapter 08 - 短期与长期记忆)");
        System.out.println("命令: /save <内容> | /memory list|search|delete|clear | /clear | /exit");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            // [s06 新增] 命令解析分流
            var parsed = CliCommandParser.parse(input);

            switch (parsed.type()) {
                // ---- [s06 新增] 退出 ----
                case EXIT -> {
                    System.out.println("再见!");
                    scanner.close();
                    return;
                }
                // ---- [s06 新增] 清空对话 ----
                case CLEAR -> {
                    agent.clearHistory();
                    hitlRegistry.hitlHandler().clearApprovedAll();
                    System.out.println("对话历史已清空。\n");
                    continue;
                }
                // ---- [s06 新增] 切换模型 ----
                case SWITCH_MODEL -> {
                    String provider = parsed.payload();
                    if (provider != null && !provider.isBlank()) {
                        System.out.println("目标模型: " + provider
                                + "。本章尚未实现热切换，请修改环境配置后重启。");
                    } else {
                        System.out.println("当前模型: " + llmClient.getProviderName()
                                + " / " + llmClient.getModelName());
                    }
                    System.out.println();
                    continue;
                }
                // [s08 新增] /save 命令处理
                case SAVE_MEMORY -> {
                    String payload = parsed.payload();
                    if (payload == null || payload.isBlank()) {
                        System.out.println("用法: /save [global] <要记住的稳定事实>\n");
                        continue;
                    }
                    String scope = "project";
                    String fact = payload;
                    if (payload.regionMatches(true, 0, "global ", 0, 7)) {
                        scope = "global";
                        fact = payload.substring(7).trim();
                    }
                    if (fact.isBlank()) {
                        System.out.println("记忆内容不可为空。\n");
                        continue;
                    }
                    var saved = memoryManager.saveFact(fact, scope);
                    System.out.println("已保存长期记忆: " + saved.id() + " [" + saved.scope() + "]\n");
                    continue;
                }
                // [s08 新增] /memory 命令处理
                case MEMORY -> {
                    String payload = parsed.payload() == null ? "" : parsed.payload().trim();
                    String[] parts = payload.split("\\s+", 2);
                    String action = parts.length == 0 ? "" : parts[0].toLowerCase();
                    String argument = parts.length > 1 ? parts[1].trim() : "";
                    switch (action) {
                        case "list" -> memoryManager.listLongTerm().forEach(entry ->
                                System.out.println(entry.id() + " [" + entry.scope() + "] " + entry.content()));
                        case "search" -> {
                            if (argument.isBlank()) System.out.println("用法: /memory search <关键词>");
                            else memoryManager.searchLongTerm(argument, 20).forEach(entry ->
                                    System.out.println(entry.id() + " [" + entry.scope() + "] " + entry.content()));
                        }
                        case "delete" -> System.out.println(argument.isBlank()
                                ? "用法: /memory delete <id>"
                                : (memoryManager.deleteLongTerm(argument) ? "已删除。" : "未找到该记忆。"));
                        case "clear" -> System.out.println("已清空 "
                                + memoryManager.clearProjectLongTerm() + " 条当前项目记忆；global 保留。");
                        default -> System.out.println("用法: /memory list|search <关键词>|delete <id>|clear");
                    }
                    System.out.println();
                    continue;
                }
                // ---- [s06 新增] 未知 /xxx → CLI 层直接拒绝 ----
                case UNKNOWN_COMMAND -> {
                    System.out.println("❌ 未知命令: " + parsed.payload() + "\n");
                    continue;
                }
                // ---- 普通文本 → 进入 Agent ----
                case NONE -> { /* fall through to Agent */ }
            }

            if (input.isEmpty()) continue;

            try {
                agent.setStreamListener(new LlmClient.StreamListener() {
                    @Override
                    public void onContentDelta(String delta) {
                        System.out.print(delta);
                        System.out.flush();
                    }
                });

                String result = agent.run(input);
                if (!result.isEmpty()) System.out.println(result);
                else System.out.println();

                System.out.println("[" + agent.statusSummary() + "]\n");
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
                LOG.error("Agent 执行异常", e);
            }
        }
    }
}
