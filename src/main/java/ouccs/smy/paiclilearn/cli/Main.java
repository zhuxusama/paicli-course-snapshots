package ouccs.smy.paiclilearn.cli;

import ouccs.smy.paiclilearn.agent.Agent;
import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmClientFactory;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import ouccs.smy.paiclilearn.llm.LlmTraceLogger;

import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * s03 CLI 入口：使用 Agent 执行 ReAct 循环。
 * <p>
 * 与 s02 的区别：
 * <ul>
 *   <li>不再直接调用 llmClient.chat()，而是通过 Agent.run() 进入 ReAct 循环</li>
 *   <li>支持多轮推理：Agent 自动管理 conversationHistory</li>
 *   <li>支持 /clear 命令清空对话历史</li>
 *   <li>提示 /model 切换后需重启（s06 加入运行时切换）</li>
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
        Agent agent = new Agent(llmClient);

        System.out.println("PaiCLI 教学版 v4 (Chapter 04 - 只读文件工具)");
        System.out.println("Provider: " + llmClient.getProviderName());
        System.out.println("模型: " + llmClient.getModelName());
        System.out.println("工具: read_file, list_dir, glob_files, grep_code");
        System.out.println("输入 'exit' 退出；输入 '/clear' 清空对话历史\n");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(input)) {
                break;
            }
            if (input.isEmpty()) {
                continue;
            }
            if ("/clear".equalsIgnoreCase(input)) {
                agent.clearHistory();
                System.out.println("对话历史已清空。\n");
                continue;
            }

            try {
                // 设置流式监听器——实时打印模型输出
                agent.setStreamListener(new LlmClient.StreamListener() {
                    @Override
                    public void onContentDelta(String delta) {
                        System.out.print(delta);
                        System.out.flush();
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        // 本章暂不把推理过程写入正文区
                    }
                });

                String result = agent.run(input);
                if (!result.isEmpty()) {
                    System.out.println(result);
                } else {
                    System.out.println();
                }

                System.out.println("[" + agent.statusSummary() + "]\n");
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
                LOG.error("Agent 执行异常", e);
            }
        }

        System.out.println("再见!");
        scanner.close();
    }
}
