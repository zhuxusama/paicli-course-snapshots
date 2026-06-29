package ouccs.smy.paiclilearn.memory.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用完整真实 rollout 验证 s09 压缩率、结构完整性和事实召回。 */
class RolloutCompressionBenchmarkTest {

    @Test
    void compressesRealRolloutWithoutLosingCriticalContext() throws Exception {
        var rollout = new CodexRolloutLoader().load(CodexRolloutLoader.locateFixtureOrSkip());
        var result = CompressionBenchmark.run(
                rollout, new DeterministicSummaryLlmClient(), "offline");

        Path reportDir = Path.of("target", "compression-benchmark");
        CompressionBenchmarkReport.write(reportDir, result.report());

        String failures = result.failedGateSummary();
        assertTrue(result.allGatesPassed(), failures + "；完整报告: "
                + reportDir.resolve("report.json").toAbsolutePath());
    }
}
