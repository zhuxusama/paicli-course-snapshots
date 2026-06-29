package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClientFactory;
import ouccs.smy.paiclilearn.llm.LlmConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 显式开启后，用当前真实 provider 评价同一份 rollout 的压缩质量。 */
class RealLlmRolloutCompressionBenchmarkTest {

    @Test
    void evaluatesCompressionWithConfiguredRealProvider() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("paicli.compression.real"),
                "设置 -Dpaicli.compression.real=true 后才调用真实 LLM");
        Path fixture = CodexRolloutLoader.locateFixtureOrSkip();
        Path output = Path.of("target", "compression-benchmark", "real");
        try {
            var rollout = new CodexRolloutLoader().load(fixture);
            var client = LlmClientFactory.create(LlmConfig.fromEnvironment());
            var result = CompressionBenchmark.run(rollout, client, "real");
            CompressionBenchmarkReport.write(output, result.report());
            assertTrue(result.allGatesPassed(), result.failedGateSummary()
                    + "；完整报告: " + output.resolve("report.json").toAbsolutePath());
        } catch (Throwable failure) {
            CompressionBenchmarkReport.writeFailure(output, fixture, "real", failure);
            throw failure;
        }
    }
}
