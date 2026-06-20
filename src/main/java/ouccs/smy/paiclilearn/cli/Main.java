package ouccs.smy.paiclilearn.cli;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import ouccs.smy.paiclilearn.llm.OpenAiCompatibleClient;
import ouccs.smy.paiclilearn.llm.LlmClient.Message;
import ouccs.smy.paiclilearn.llm.LlmClient.StreamListener;

import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        LlmConfig config;
        try {
            config = LlmConfig.fromEnvironment();
        } catch (Exception e) {
            System.err.println("配置加载失败: " + e.getMessage());
            System.exit(1);
            return;
        }

        LlmClient llmClient = new OpenAiCompatibleClient(config);

        System.out.println("PaiCLI 教学版 v1 (Chapter 01)");
        System.out.println("协议: OpenAI-compatible");
        System.out.println("模型: " + config.model());
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
                        List.of(Message.user(input)),
                        streamListener
                );
                if (!response.content().isEmpty()) {
                    System.out.println();
                }
                System.out.printf("[tokens: in=%d out=%d]%n%n",
                        response.inputTokens(), response.outputTokens());
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
            }
        }

        System.out.println("再见!");
        scanner.close();
    }
}
