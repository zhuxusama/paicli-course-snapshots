package ouccs.smy.paiclilearn.hitl;

import ouccs.smy.paiclilearn.policy.*;
import ouccs.smy.paiclilearn.tool.ToolRegistry;
import ouccs.smy.paiclilearn.tool.ToolOutput;

/**
 * 带 HITL 审批链的工具注册中心包装。
 * <p>拦截顺序：HitlToolRegistry → ToolRegistry → PathGuard/CommandGuard。
 * 副作用工具在 Policy 层拒绝时直接返回错误（不可被用户越权批准）；
 * 通过 Policy 的工具进入 HITL 审批。</p>
 *
 * @since s05 [s05 新增]
 */
public class HitlToolRegistry {
    private final ToolRegistry delegate;
    private final HitlHandler hitlHandler;
    private final AuditLog auditLog;
    private final PathGuard pathGuard;

    /** 使用默认 HitlHandler 构造。 */
    public HitlToolRegistry(ToolRegistry delegate, HitlHandler hitlHandler) {
        this.delegate = delegate;
        this.hitlHandler = hitlHandler;
        this.auditLog = new AuditLog();
        this.pathGuard = new PathGuard(delegate.getProjectPath().toString());
    }

    public ToolRegistry delegate() { return delegate; }
    public HitlHandler hitlHandler() { return hitlHandler; }
    public AuditLog auditLog() { return auditLog; }

    /**
     * 带审批的工具执行入口。
     * <p>流程：
     * <ol>
     *   <li>检查是否需要审批（ApprovalPolicy.requiresApproval）</li>
     *   <li>不需要审批 → 直接委托 delegate.executeTool</li>
     *   <li>需要审批 → 先过 Policy 层（PathGuard/CommandGuard）</li>
     *   <li>Policy 通过 → 创建 ApprovalRequest → hitlHandler.requestApproval</li>
     *   <li>批准 → 委托 delegate.executeTool → 记录 allow 审计</li>
     *   <li>拒绝 → 记录 deny 审计 → 返回拒绝消息</li>
     * </ol>
     * </p>
     */
    public String executeTool(String name, String argumentsJson) {
        long start = System.currentTimeMillis();

        // 1. 不需要审批的工具直接放行
        if (!ApprovalPolicy.requiresApproval(name)) {
            return executeDelegate(name, argumentsJson).text();
        }

        // 2. 策略层检查（不可被 HITL 越权批准）
        String policyBlock = checkPolicy(name, argumentsJson);
        if (policyBlock != null) {
            auditLog.record(AuditLog.AuditEntry.denyByPolicy(name, argumentsJson,
                    policyBlock, System.currentTimeMillis() - start));
            return "❌ [策略拒绝] " + policyBlock;
        }

        // 3. HITL 审批
        ApprovalRequest req = ApprovalRequest.of(name, argumentsJson, null);
        ApprovalResult result = hitlHandler.requestApproval(req);

        long elapsed = System.currentTimeMillis() - start;
        String effectiveArgs = argumentsJson;

        if (result.isApproved()) {
            if (result.decision() == Decision.MODIFIED && result.modifiedArguments() != null) {
                effectiveArgs = result.modifiedArguments();
            }
            ToolOutput toolResult = executeDelegate(name, effectiveArgs);
            auditLog.record(AuditLog.AuditEntry.allow(name, effectiveArgs, elapsed));
            return toolResult.text();
        }

        if (result.isRejected()) {
            String reason = result.reason() != null ? result.reason() : "用户拒绝";
            auditLog.record(AuditLog.AuditEntry.denyByHitl(name, argumentsJson, reason, elapsed));
            return "⛔ [HITL 拒绝] " + reason;
        }

        if (result.isSkipped()) {
            auditLog.record(AuditLog.AuditEntry.denyByHitl(name, argumentsJson, "用户跳过", elapsed));
            return "⏭ [HITL 跳过] 已跳过 " + name;
        }

        return "❓ 未处理的审批结果";
    }

    /** 把底层文本结果提升为稳定值对象，后续章节可在此增加图片等内容部件。 */
    private ToolOutput executeDelegate(String name, String argumentsJson) {
        return ToolOutput.text(delegate.executeTool(name, argumentsJson));
    }

    /** 委托 getToolDefinitions。 */
    public java.util.List<ouccs.smy.paiclilearn.llm.LlmClient.Tool> getToolDefinitions() {
        return delegate.getToolDefinitions();
    }

    /** 委托 executeTools（批量执行，s13 改为并行）。 */
    public java.util.List<ToolRegistry.ToolExecutionResult> executeTools(
            java.util.List<ToolRegistry.ToolInvocation> invocations) {
        java.util.List<ToolRegistry.ToolExecutionResult> results = new java.util.ArrayList<>();
        for (ToolRegistry.ToolInvocation inv : invocations) {
            long start = System.currentTimeMillis();
            try {
                String result = executeTool(inv.name(), inv.argumentsJson());
                results.add(ToolRegistry.ToolExecutionResult.completed(inv, result,
                        System.currentTimeMillis() - start));
            } catch (Exception e) {
                results.add(ToolRegistry.ToolExecutionResult.failed(inv, e.getMessage(),
                        System.currentTimeMillis() - start));
            }
        }
        return results;
    }

    // ========== 策略层 ==========

    /** 在 HITL 之前执行策略检查。返回 null 表示通过，非 null 表示拒绝原因。 */
    private String checkPolicy(String toolName, String argumentsJson) {
        // 解析参数中的 path/command 字段进行策略检查
        try {
            var args = new com.fasterxml.jackson.databind.ObjectMapper().readValue(argumentsJson, java.util.Map.class);
            // 路径检查
            if (args.containsKey("path")) {
                try {
                    pathGuard.resolveSafe(args.get("path").toString());
                } catch (PolicyException e) {
                    return e.getMessage();
                }
            }
            // 命令检查
            if ("execute_command".equals(toolName) && args.containsKey("command")) {
                String cmdCheck = CommandGuard.check(args.get("command").toString());
                if (cmdCheck != null) return cmdCheck;
            }
        } catch (Exception ignored) {
            // 参数解析失败时放行给 HITL 决定
        }
        return null;
    }
}
