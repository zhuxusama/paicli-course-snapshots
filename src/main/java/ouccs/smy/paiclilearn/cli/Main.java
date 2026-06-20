package ouccs.smy.paiclilearn.cli;

import ouccs.smy.paiclilearn.agent.Agent;
import ouccs.smy.paiclilearn.hitl.HitlToolRegistry;
import ouccs.smy.paiclilearn.hitl.TerminalHitlHandler;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmClientFactory;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import ouccs.smy.paiclilearn.llm.LlmTraceLogger;
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
        Agent agent = new Agent(llmClient, toolRegistry);
        agent.setHitlRegistry(hitlRegistry);  // [s05] HITL 审批链

        System.out.println("PaiCLI 教学版 v6 (Chapter 06 - Slash 命令与配置)");
        System.out.println("命令: /model [provider] 切换模型 | /clear 清空历史 | /exit 退出");

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
