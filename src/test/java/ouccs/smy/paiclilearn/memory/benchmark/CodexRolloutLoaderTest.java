package ouccs.smy.paiclilearn.memory.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 Codex rollout 验证 JSONL 映射和工具协议配对。 */
class CodexRolloutLoaderTest {

    @Test
    void loadsBaselineFixtureAndPairsEveryFunctionCall() throws Exception {
        var fixture = CodexRolloutLoader.locateFixtureOrSkip();
        var rollout = new CodexRolloutLoader().load(fixture);

        assertTrue(rollout.baselineFixture());
        assertEquals(780, rollout.stats().totalEvents());
        assertEquals(532, rollout.stats().responseItems());
        assertEquals(11, rollout.stats().userMessages());
        assertEquals(78, rollout.stats().assistantMessages());
        assertEquals(175, rollout.stats().functionCalls());
        assertEquals(175, rollout.stats().functionCallOutputs());
        assertEquals(0, rollout.stats().parseErrors());
        assertTrue(rollout.pairingIssues().isEmpty(), rollout.pairingIssues().toString());

        assertTrue(rollout.messages().stream().anyMatch(item -> "system".equals(item.message().role())));
        assertTrue(rollout.messages().stream().anyMatch(item -> "user".equals(item.message().role())));
        assertTrue(rollout.messages().stream().anyMatch(item -> "assistant".equals(item.message().role())));
        assertTrue(rollout.messages().stream().anyMatch(item -> "tool".equals(item.message().role())));
        assertTrue(rollout.messages().stream().anyMatch(item -> item.message().toolCalls() != null
                && !item.message().toolCalls().isEmpty()));

        var first = rollout.messages().get(0);
        assertTrue(first.sourceLine() > 0);
        assertFalse(first.sourceType().isBlank());
        assertEquals(64, first.sha256().length());
        assertTrue(first.preview().length() <= 81);
    }
}
