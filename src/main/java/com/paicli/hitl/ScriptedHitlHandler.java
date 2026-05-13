package com.paicli.hitl;

import com.paicli.eval.EvalRunRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eval-mode HITL handler that returns pre-configured decisions
 * without interactive terminal prompts.
 *
 * Decisions are configured via constructor or system property
 * {@code paicli.eval.hitl.decisions} (JSON array format).
 * When no decision matches a tool, defaults to REJECT (fail-safe).
 */
public class ScriptedHitlHandler implements HitlHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<HitlDecision> decisions;
    private final Set<String> approvedAllTools = ConcurrentHashMap.newKeySet();
    private final Set<String> approvedAllServers = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled;

    public record HitlDecision(String toolPattern, String decision, String reason) {
        public ApprovalResult.Decision asDecision() {
            return switch (decision.toUpperCase()) {
                case "APPROVED" -> ApprovalResult.Decision.APPROVED;
                case "APPROVED_ALL" -> ApprovalResult.Decision.APPROVED_ALL;
                case "APPROVED_ALL_BY_SERVER" -> ApprovalResult.Decision.APPROVED_ALL_BY_SERVER;
                case "REJECTED" -> ApprovalResult.Decision.REJECTED;
                case "SKIPPED" -> ApprovalResult.Decision.SKIPPED;
                case "MODIFIED" -> ApprovalResult.Decision.MODIFIED;
                default -> ApprovalResult.Decision.REJECTED;
            };
        }
    }

    public ScriptedHitlHandler(boolean enabled, List<HitlDecision> decisions) {
        this.enabled = enabled;
        this.decisions = decisions != null ? decisions : List.of();
    }

    /**
     * Parse decisions from a JSON file.
     * Used when the JSON string is too complex or contains special characters
     * that make it hard to pass as a system property.
     */
    public static ScriptedHitlHandler fromFile(String filePath) {
        try {
            String json = java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
            return fromSystemProperty(json);
        } catch (Exception e) {
            System.err.println("⚠️ 读取 HITL 决策文件失败: " + e.getMessage());
            return new ScriptedHitlHandler(true, List.of());
        }
    }

    /**
     * Parse decisions from system property JSON string.
     * Format: [{"toolPattern":"execute_command","decision":"REJECTED","reason":"dangerous"}]
     */
    public static ScriptedHitlHandler fromSystemProperty(String json) {
        List<HitlDecision> decisions = new ArrayList<>();
        if (json != null && !json.isBlank()) {
            try {
                var tree = MAPPER.readTree(json);
                if (tree.isArray()) {
                    for (var node : tree) {
                        String pattern = node.has("toolPattern") ? node.get("toolPattern").asText("") : "*";
                        String decision = node.has("decision") ? node.get("decision").asText("REJECTED") : "REJECTED";
                        String reason = node.has("reason") ? node.get("reason").asText("") : "";
                        decisions.add(new HitlDecision(pattern, decision, reason));
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ 解析 HITL 决策配置失败: " + e.getMessage());
            }
        }
        return new ScriptedHitlHandler(true, decisions);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void clearApprovedAll() {
        approvedAllTools.clear();
        approvedAllServers.clear();
    }

    @Override
    public ApprovalResult requestApproval(ApprovalRequest request) {
        String mcpServer = ApprovalPolicy.mcpServerName(request.toolName());
        if (approvedAllTools.contains(request.toolName())) {
            return ApprovalResult.approveAll();
        }
        if (mcpServer != null && approvedAllServers.contains(mcpServer)) {
            return ApprovalResult.approveAllByServer();
        }

        for (HitlDecision d : decisions) {
            if (matchesPattern(d.toolPattern(), request.toolName())) {
                ApprovalResult result = toResult(d);
                if (result.isApprovedAll()) {
                    approvedAllTools.add(request.toolName());
                } else if (result.isApprovedAllForServer() && mcpServer != null) {
                    approvedAllServers.add(mcpServer);
                }
                recordToEvalTrace(request.toolName(), d);
                return result;
            }
        }

        ApprovalResult rejectResult = ApprovalResult.reject("eval 模式默认拒绝（未匹配预设决策）");
        recordToEvalTrace(request.toolName(), new HitlDecision("*", "REJECTED", "eval 模式默认拒绝"));
        return rejectResult;
    }

    private ApprovalResult toResult(HitlDecision d) {
        return switch (d.asDecision()) {
            case APPROVED -> ApprovalResult.approve();
            case APPROVED_ALL -> ApprovalResult.approveAll();
            case APPROVED_ALL_BY_SERVER -> ApprovalResult.approveAllByServer();
            case REJECTED -> ApprovalResult.reject(d.reason());
            case SKIPPED -> ApprovalResult.skip();
            case MODIFIED -> ApprovalResult.modify(d.reason());
        };
    }

    private void recordToEvalTrace(String toolName, HitlDecision d) {
        EvalRunRecorder recorder = EvalRunRecorder.current();
        if (recorder != null && EvalRunRecorder.isEnabled()) {
            recorder.recordHitlDecision(toolName, d.decision(), d.reason(), 0);
        }
    }

    static boolean matchesPattern(String pattern, String toolName) {
        if (pattern == null || pattern.isBlank()) return false;
        if ("*".equals(pattern)) return true;
        return pattern.equals(toolName);
    }
}
