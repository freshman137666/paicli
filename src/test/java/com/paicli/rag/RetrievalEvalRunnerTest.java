package com.paicli.rag;

import com.paicli.rag.VectorStore.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetrievalEvalRunnerTest {

    private static SearchResult sr(String filePath, String name) {
        return new SearchResult(filePath, "method", name, "content", 0.5);
    }

    @Test
    void recallAtK_findsBothTargets() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/hitl/HitlToolRegistry.java", "executeTool"),
                sr("src/main/java/com/paicli/hitl/HitlHandler.java", "requestApproval"),
                sr("src/main/java/com/paicli/tool/ToolRegistry.java", "run")
        );
        double recall5 = RetrievalEvalRunner.recallAtK(results,
                List.of("HitlToolRegistry.java", "HitlHandler.java"), 5);
        assertEquals(1.0, recall5, 0.001);
    }

    @Test
    void recallAtK_findsOneOfTwoTargets() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/hitl/HitlToolRegistry.java", "executeTool"),
                sr("src/main/java/com/paicli/tool/ToolRegistry.java", "run")
        );
        double recall5 = RetrievalEvalRunner.recallAtK(results,
                List.of("HitlToolRegistry.java", "HitlHandler.java"), 5);
        assertEquals(0.5, recall5, 0.001);
    }

    @Test
    void recallAtK_noMatches() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/tool/ToolRegistry.java", "run"),
                sr("src/main/java/com/paicli/agent/Agent.java", "run")
        );
        double recall5 = RetrievalEvalRunner.recallAtK(results,
                List.of("HitlToolRegistry.java", "HitlHandler.java"), 5);
        assertEquals(0.0, recall5, 0.001);
    }

    @Test
    void recallAtK_cappedAtK() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/hitl/HitlToolRegistry.java", "executeTool"),
                sr("src/main/java/com/paicli/tool/ToolRegistry.java", "run"),
                sr("src/main/java/com/paicli/agent/Agent.java", "run"),
                sr("src/main/java/com/paicli/plan/Planner.java", "plan"),
                sr("src/main/java/com/paicli/hitl/HitlHandler.java", "requestApproval")
        );
        double recall3 = RetrievalEvalRunner.recallAtK(results,
                List.of("HitlToolRegistry.java", "HitlHandler.java"), 3);
        assertEquals(0.5, recall3, 0.001);
    }

    @Test
    void recallAtK_emptyTargets() {
        double recall = RetrievalEvalRunner.recallAtK(List.of(), List.of(), 5);
        assertEquals(1.0, recall, 0.001);
    }

    @Test
    void recallAtK_doesNotCountDuplicates() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/hitl/HitlToolRegistry.java", "executeTool"),
                sr("src/main/java/com/paicli/hitl/HitlToolRegistry.java", "anotherMethod"),
                sr("src/main/java/com/paicli/hitl/HitlHandler.java", "requestApproval")
        );
        double recall5 = RetrievalEvalRunner.recallAtK(results,
                List.of("HitlToolRegistry.java", "HitlHandler.java"), 5);
        assertEquals(1.0, recall5, 0.001);
    }

    @Test
    void mrr_firstPositionMatch() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/hitl/HitlToolRegistry.java", "executeTool"),
                sr("src/main/java/com/paicli/tool/ToolRegistry.java", "run")
        );
        double mrr = RetrievalEvalRunner.mrr(results,
                List.of("HitlToolRegistry.java"));
        assertEquals(1.0, mrr, 0.001);
    }

    @Test
    void mrr_secondPositionMatch() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/tool/ToolRegistry.java", "run"),
                sr("src/main/java/com/paicli/hitl/HitlToolRegistry.java", "executeTool")
        );
        double mrr = RetrievalEvalRunner.mrr(results,
                List.of("HitlToolRegistry.java"));
        assertEquals(0.5, mrr, 0.001);
    }

    @Test
    void mrr_noMatch() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/tool/ToolRegistry.java", "run")
        );
        double mrr = RetrievalEvalRunner.mrr(results,
                List.of("HitlToolRegistry.java"));
        assertEquals(0.0, mrr, 0.001);
    }

    @Test
    void recallAtK_matchesByName() {
        List<SearchResult> results = List.of(
                sr("src/main/java/com/paicli/hitl/Approval.java", "HitlToolRegistry")
        );
        double recall = RetrievalEvalRunner.recallAtK(results,
                List.of("HitlToolRegistry.java"), 5);
        assertEquals(1.0, recall, 0.001);
    }
}