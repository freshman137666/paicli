package com.paicli.hitl;

import com.paicli.eval.EvalRunRecorder;
import com.paicli.policy.AuditLog;
import com.paicli.tool.ToolRegistry;

import java.util.concurrent.TimeUnit;

/**
 * HITL 工具注册表 - 在危险工具调用前插入人工审批
 *
 * 继承自 ToolRegistry，覆写 executeTool 方法，在执行危险操作之前
 * 通过 HitlHandler 向用户请求审批。
 *
 * 如果 HITL 未启用，行为与父类完全相同，无额外开销。
 *
 * HITL 拒绝 / 跳过路径会写一行 audit（approver=hitl），HITL 通过后由父类 ToolRegistry 写
 * allow / policy-deny / error，HITL 审批与策略拦截共用同一份 ~/.paicli/audit/ 文件。
 *
 * 审批决策同时记录到 EvalRunRecorder.hitlDecisions（仅 eval 模式启用时生效），
 * 供 eval trace 对 grader 可见。
 */
public class HitlToolRegistry extends ToolRegistry {

    private final HitlHandler hitlHandler;

    public HitlToolRegistry(HitlHandler hitlHandler) {
        super();
        this.hitlHandler = hitlHandler;
    }

    @Override
    public String executeTool(String name, String argumentsJson) {
        // HITL 未启用或该工具不需要审批，直接执行
        if (!hitlHandler.isEnabled() || !ApprovalPolicy.requiresApproval(name)) {
            return super.executeTool(name, argumentsJson);
        }

        long start = System.nanoTime();
        ApprovalRequest request = ApprovalRequest.of(name, argumentsJson, null);
        ApprovalResult result = hitlHandler.requestApproval(request);

        // ScriptedHitlHandler 已自行写入 eval trace；TerminalHitlHandler 在此补写
        if (!(hitlHandler instanceof ScriptedHitlHandler)) {
            recordToEvalTrace(name, result.decision().name(),
                    result.reason() != null ? result.reason() : "", elapsedMillis(start));
        }

        if (result.isRejected()) {
            String reason = result.reason() != null && !result.reason().isBlank()
                    ? result.reason()
                    : "用户拒绝了此操作";
            getAuditLog().record(AuditLog.AuditEntry.denyByHitl(
                    name, argumentsJson, reason, elapsedMillis(start)));
            return "[HITL] 操作已被拒绝：" + reason;
        }

        if (result.isSkipped()) {
            getAuditLog().record(AuditLog.AuditEntry.denyByHitl(
                    name, argumentsJson, "用户跳过", elapsedMillis(start)));
            return "[HITL] 操作已被跳过";
        }

        // 批准（含修改参数）- 使用 effectiveArguments 获取最终参数；父类 executeTool 会负责 allow audit
        String effectiveArgs = result.effectiveArguments(argumentsJson);
        return super.executeTool(name, effectiveArgs);
    }

    private void recordToEvalTrace(String toolName, String decision, String reason, long durationMs) {
        EvalRunRecorder recorder = EvalRunRecorder.current();
        if (recorder != null && EvalRunRecorder.isEnabled()) {
            recorder.recordHitlDecision(toolName, decision, reason, durationMs);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    public HitlHandler getHitlHandler() {
        return hitlHandler;
    }
}
