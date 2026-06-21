package ouccs.smy.paiclilearn.policy;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSONL 审计日志——每次副作用操作写入一条审计记录。
 *
 * @since s05 [s05 新增]
 */
public class AuditLog {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认审计目录。 */
    public static final Path DEFAULT_DIR = Path.of(System.getProperty("user.home"), ".paicli", "audit");

    private final Path auditDir;

    public AuditLog() { this(DEFAULT_DIR); }
    public AuditLog(Path auditDir) { this.auditDir = auditDir; }

    public Path getAuditDir() { return auditDir; }

    // ========== AuditEntry ==========

    /**
     * 一次副作用工具决策的持久化审计记录。
     *
     * @param timestamp ISO-8601 时间戳
     * @param tool 工具名称
     * @param args 脱敏后的参数 JSON
     * @param outcome allow 或 deny
     * @param reason 拒绝原因；允许时为空
     * @param approver 决策来源
     * @param durationMs 审批与执行耗时
     */
    public record AuditEntry(
            String timestamp, String tool, String args,
            String outcome, String reason, String approver, long durationMs
    ) {
        public static AuditEntry allow(String tool, String args, long ms) {
            return new AuditEntry(Instant.now().toString(), tool, sanitize(args), "allow", "", "hitl", ms);
        }
        public static AuditEntry denyByPolicy(String tool, String args, String reason, long ms) {
            return new AuditEntry(Instant.now().toString(), tool, sanitize(args), "deny", reason, "policy", ms);
        }
        public static AuditEntry denyByHitl(String tool, String args, String reason, long ms) {
            return new AuditEntry(Instant.now().toString(), tool, sanitize(args), "deny", reason, "hitl", ms);
        }
        public static AuditEntry error(String tool, String args, String reason, long ms) {
            return new AuditEntry(Instant.now().toString(), tool, sanitize(args), "error", reason, "none", ms);
        }
    }

    // ========== 写入 ==========

    /** 追加一条审计记录到当天的 JSONL 文件。 */
    public synchronized void record(AuditEntry entry) {
        try {
            Files.createDirectories(auditDir);
            String filename = "audit-" + java.time.LocalDate.now() + ".jsonl";
            Path file = auditDir.resolve(filename);
            String json = MAPPER.writeValueAsString(entry) + "\n";
            Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 审计写入失败不中断主流程
        }
    }

    // ========== 读取 ==========

    /** 读取当天最近 n 条审计记录。 */
    public synchronized List<AuditEntry> readRecent(int n) {
        try {
            String filename = "audit-" + java.time.LocalDate.now() + ".jsonl";
            Path file = auditDir.resolve(filename);
            if (!Files.exists(file)) return Collections.emptyList();
            List<String> lines = Files.readAllLines(file);
            List<AuditEntry> entries = new ArrayList<>();
            int start = Math.max(0, lines.size() - n);
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    entries.add(MAPPER.readValue(line, AuditEntry.class));
                }
            }
            return entries;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /** 脱敏：移除参数中的 Bearer token 和密钥。 */
    private static String sanitize(String args) {
        if (args == null) return null;
        return args.replaceAll("(Bearer\\s+)[\\w\\-\\.]+", "$1***")
                   .replaceAll("\"(api[Kk]ey|key|password|secret|authorization)\"\\s*:\\s*\"[^\"]+\"", "\"$1\":\"***\"");
    }
}
