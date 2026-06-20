package ouccs.smy.paiclilearn.cli;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmClientFactory;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import ouccs.smy.paiclilearn.llm.LlmTraceLogger;
import ouccs.smy.paiclilearn.llm.LlmClient.Message;
import ouccs.smy.paiclilearn.llm.LlmClient.StreamListener;

import java.util.List;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第一章的最小命令行入口，负责读取用户消息并流式打印真实模型响应。
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

        System.out.println("PaiCLI 教学版 v2 (Chapter 02 - Multi-Provider Tool Calls)");
        System.out.println("Provider: " + llmClient.getProviderName());
        System.out.println("模型: " + llmClient.getModelName());
        System.out.println("输入 'exit' 退出\n");

        StreamListener streamListener = new StreamListener() {
            @Override
            public void onContentDelta(String delta) {
                System.out.print(delta);
                System.out.flush();
            }
        };

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

            try {
                LlmClient.ChatResponse response = llmClient.chat(
                        List.of(Message.user(input)), List.of(),
                        streamListener
                );
                if (!response.content().isEmpty()) {
                    System.out.println();
                }
                System.out.printf("[tokens: in=%d out=%d]%n%n",
                        response.inputTokens(), response.outputTokens());
                LlmTraceLogger.logReasoning(LOG, "cli", llmClient, response.reasoningContent());
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
            }
        }

        System.out.println("再见!");
        scanner.close();
    }
}
