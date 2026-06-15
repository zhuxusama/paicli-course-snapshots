package ouccs.smy.paiclilearn.cli;

import ouccs.smy.paiclilearn.agent.MiniAgent;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import ouccs.smy.paiclilearn.llm.OpenAiCompatibleClient;
import ouccs.smy.paiclilearn.llm.LlmClient.StreamListener;

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
        MiniAgent agent = new MiniAgent(llmClient);

        System.out.println("PaiCLI 教学版 v1 (Chapter 01)");
        System.out.println("模型: " + llmClient.getProviderName() + " / " + llmClient.getModelName());
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
                String response = agent.run(input, streamListener);
                if (!response.isEmpty()) {
                    System.out.println();
                }
                System.out.println();
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
            }
        }

        System.out.println("再见!");
        scanner.close();
    }
}
