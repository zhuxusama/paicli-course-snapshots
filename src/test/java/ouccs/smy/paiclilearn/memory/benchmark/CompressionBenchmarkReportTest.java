package ouccs.smy.paiclilearn.memory.benchmark;

import ouccs.smy.paiclilearn.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 验证基准报告完整记录指标，但不会复制真实会话敏感正文。 */
class CompressionBenchmarkReportTest {
    @TempDir Path tempDir;

    @Test
    void writesAuditableReportsWithoutLeakingFullMessageBodies() throws Exception {
        String secretTail = "Bearer sk-test-secret-must-not-appear";
        String body = "用户要求先备份。" + "x".repeat(120) + secretTail;
        var message = new LlmClient.Message("user", body);
        var traced = new TracedMessage(12, "response_item/message/user", message,
                body.length(), CodexRolloutLoader.sha256(body), CodexRolloutLoader.preview(body));
        var stats = new RolloutStats(780, 532, 11, 78, 175, 175, 0, 248,
                Map.of("response_item", 532), Map.of("message", 90));
        var snapshot = new BenchmarkReportSnapshot(
                Path.of("real-rollout.jsonl"), true, stats, List.of(traced), List.of(),
                1, 0, Map.of("user", 0.55), 10_000, 3_000, 0.70, 0.55, 123,
                "用户要求先备份并冲击 35 万分。",
                List.of(new FactEvaluation("backup", "必须备份", true, "summary")),
                List.of(new GateEvaluation("token_reduction", true, ">= 60%", "70%")),
                "offline", "deterministic", "extractive", 0, 0, 0);

        CompressionBenchmarkReport.write(tempDir, snapshot);

        String log = Files.readString(tempDir.resolve("benchmark.log"));
        String json = Files.readString(tempDir.resolve("report.json"));
        assertTrue(log.contains("tokenReductionPercent=70.00"));
        assertTrue(log.contains(traced.sha256()));
        assertTrue(json.contains("\"facts\""));
        assertTrue(json.contains("\"gates\""));
        assertTrue(log.contains(snapshot.summary()));
        assertTrue(json.contains("用户要求先备份并冲击 35 万分"));
        assertFalse(log.contains(secretTail));
        assertFalse(json.contains(secretTail));
        assertFalse(log.contains(body));
        assertFalse(json.contains(body));
    }

    @Test
    void writesSafeFailureReportBeforeRealModeFails() throws Exception {
        String secret = "Bearer sk-real-secret-12345678";
        CompressionBenchmarkReport.writeFailure(tempDir, Path.of("fixture.jsonl"),
                "real", new IllegalStateException("provider failed " + secret));

        String log = Files.readString(tempDir.resolve("benchmark.log"));
        String json = Files.readString(tempDir.resolve("report.json"));
        assertTrue(log.contains("IllegalStateException"));
        assertTrue(json.contains("provider failed"));
        assertFalse(log.contains(secret));
        assertFalse(json.contains(secret));
    }
}
