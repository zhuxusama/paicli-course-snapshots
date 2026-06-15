package ouccs.smy.paiclilearn.agent;

import ouccs.smy.paiclilearn.llm.LlmClient;
import ouccs.smy.paiclilearn.llm.LlmClient.Message;
import ouccs.smy.paiclilearn.llm.LlmClient.StreamListener;

import java.util.ArrayList;
import java.util.List;

public class MiniAgent {

    private final LlmClient llmClient;
    private final List<Message> history;

    public MiniAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.history = new ArrayList<>();
        this.history.add(Message.system(
                "你是 PaiCLI 教学版第一章的最小 Agent。\n" +
                "你现在只负责用简洁中文回答用户问题。\n" +
                "你还没有工具调用能力，不要声称已经读取或修改本地文件。"
        ));
    }

    public String run(String userInput, StreamListener listener) throws Exception {
        history.add(Message.user(userInput));

        var response = llmClient.chat(history, listener);

        history.add(Message.assistant(response.content()));
        return response.content();
    }

    public String run(String userInput) throws Exception {
        return run(userInput, StreamListener.NO_OP);
    }
}
