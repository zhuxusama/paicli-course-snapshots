package ouccs.smy.paiclilearn.prompt;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 教学演示：PromptContext 输入经过分层组装后形成完整 system prompt。 */
class PromptAssemblerDemoTest {

    @Test
    void demoLayeredPromptAssembly() {
        System.out.println("========== s07 Prompt 分层演示 ==========");

        PromptContext input = PromptContext.builder()
                .approvalMode("suggest")
                .personality("calm")
                .currentDate(LocalDate.of(2026, 6, 21))
                .zoneId(ZoneId.of("Asia/Shanghai"))
                .build();
        System.out.println("【输入】" + input);

        String output = PromptAssembler.createDefault().assemble(PromptMode.AGENT, input);
        System.out.println("【转换】base -> personality -> agent -> approval -> runtime");
        System.out.println("【输出】\n" + output);

        assertTrue(output.contains("## Language"));
        assertTrue(output.contains("## Mode: ReAct Agent"));
        assertTrue(output.contains("2026-06-21"));
        System.out.println("========== 演示结束 ==========");
    }
}
