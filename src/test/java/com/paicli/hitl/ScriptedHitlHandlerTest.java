package com.paicli.hitl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptedHitlHandlerTest {

    private static final ApprovalRequest EXEC_CMD_REQUEST =
            ApprovalRequest.of("execute_command", "{\"command\":\"rm -rf /tmp/data\"}", null);
    private static final ApprovalRequest WRITE_FILE_REQUEST =
            ApprovalRequest.of("write_file", "{\"path\":\"/tmp/x.txt\",\"content\":\"hi\"}", null);

    @Test
    void rejectedDecisionReturnsRejected() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("execute_command", "REJECTED", "dangerous")
        ));
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.REJECTED, result.decision());
        assertEquals("dangerous", result.reason());
    }

    @Test
    void skippedDecisionReturnsSkipped() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("execute_command", "SKIPPED", "skipped dangerous command")
        ));
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.SKIPPED, result.decision());
    }

    @Test
    void approvedDecisionReturnsApproved() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("execute_command", "APPROVED", "ok")
        ));
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.APPROVED, result.decision());
    }

    @Test
    void approvedAllDecisionReturnsApprovedAllAndCaches() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("execute_command", "APPROVED_ALL", "trusted")
        ));
        ApprovalResult result1 = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.APPROVED_ALL, result1.decision());

        ApprovalResult result2 = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.APPROVED_ALL, result2.decision());
    }

    @Test
    void wildcardPatternMatchesAllTools() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("*", "REJECTED", "reject all")
        ));
        assertEquals(ApprovalResult.Decision.REJECTED,
                handler.requestApproval(EXEC_CMD_REQUEST).decision());
        assertEquals(ApprovalResult.Decision.REJECTED,
                handler.requestApproval(WRITE_FILE_REQUEST).decision());
    }

    @Test
    void unmatchedToolDefaultsToReject() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("execute_command", "APPROVED", "ok")
        ));
        ApprovalResult result = handler.requestApproval(WRITE_FILE_REQUEST);
        assertEquals(ApprovalResult.Decision.REJECTED, result.decision());
        assertTrue(result.reason().contains("默认拒绝"));
    }

    @Test
    void emptyDecisionsDefaultToReject() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of());
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.REJECTED, result.decision());
    }

    @Test
    void fromSystemPropertyParsesJson() {
        String json = "[{\"toolPattern\":\"execute_command\",\"decision\":\"REJECTED\",\"reason\":\"dangerous\"}]";
        ScriptedHitlHandler handler = ScriptedHitlHandler.fromSystemProperty(json);
        assertTrue(handler.isEnabled());
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.REJECTED, result.decision());
    }

    @Test
    void fromSystemPropertyHandlesEmptyJson() {
        ScriptedHitlHandler handler = ScriptedHitlHandler.fromSystemProperty("[]");
        assertTrue(handler.isEnabled());
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.REJECTED, result.decision());
    }

    @Test
    void fromSystemPropertyHandlesInvalidJson() {
        ScriptedHitlHandler handler = ScriptedHitlHandler.fromSystemProperty("not-json");
        assertTrue(handler.isEnabled());
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.REJECTED, result.decision());
    }

    @Test
    void clearApprovedAllResetsApproveAll() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("execute_command", "APPROVED_ALL", "trusted")
        ));
        handler.requestApproval(EXEC_CMD_REQUEST);
        handler.clearApprovedAll();
        ApprovalResult result = handler.requestApproval(EXEC_CMD_REQUEST);
        assertEquals(ApprovalResult.Decision.APPROVED_ALL, result.decision());
    }

    @Test
    void enableAndDisable() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of());
        assertTrue(handler.isEnabled());
        handler.setEnabled(false);
        assertFalse(handler.isEnabled());
        handler.setEnabled(true);
        assertTrue(handler.isEnabled());
    }

    @Test
    void matchesPatternWildcard() {
        assertTrue(ScriptedHitlHandler.matchesPattern("*", "any_tool"));
    }

    @Test
    void matchesPatternExactMatch() {
        assertTrue(ScriptedHitlHandler.matchesPattern("execute_command", "execute_command"));
    }

    @Test
    void matchesPatternNoMatch() {
        assertFalse(ScriptedHitlHandler.matchesPattern("execute_command", "write_file"));
    }

    @Test
    void matchesPatternNullPattern() {
        assertFalse(ScriptedHitlHandler.matchesPattern(null, "execute_command"));
    }

    @Test
    void matchesPatternBlankPattern() {
        assertFalse(ScriptedHitlHandler.matchesPattern("", "execute_command"));
    }

    @Test
    void modifiedDecisionReturnsModifiedArgs() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("write_file", "MODIFIED", "{\"path\":\"/safe.txt\",\"content\":\"x\"}")
        ));
        ApprovalResult result = handler.requestApproval(WRITE_FILE_REQUEST);
        assertEquals(ApprovalResult.Decision.MODIFIED, result.decision());
        assertEquals("{\"path\":\"/safe.txt\",\"content\":\"x\"}", result.effectiveArguments("{\"path\":\"/tmp/x.txt\",\"content\":\"hi\"}"));
    }

    @Test
    void multipleDecisionsMatchByOrder() {
        ScriptedHitlHandler handler = new ScriptedHitlHandler(true, List.of(
                new ScriptedHitlHandler.HitlDecision("execute_command", "REJECTED", "dangerous"),
                new ScriptedHitlHandler.HitlDecision("write_file", "APPROVED", "safe write")
        ));
        assertEquals(ApprovalResult.Decision.REJECTED,
                handler.requestApproval(EXEC_CMD_REQUEST).decision());
        assertEquals(ApprovalResult.Decision.APPROVED,
                handler.requestApproval(WRITE_FILE_REQUEST).decision());
    }
}