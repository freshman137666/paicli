package com.paicli.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standalone retrieval evaluation: measures recall@K and MRR for
 * semantic, keyword, and hybrid search modes against ground-truth targets.
 *
 * Usage:
 *   java -cp ... com.paicli.rag.RetrievalEvalRunner <project-path> <output-json>
 *
 * Environment:
 *   EMBEDDING_PROVIDER, EMBEDDING_MODEL, EMBEDDING_BASE_URL, EMBEDDING_API_KEY
 *   (as configured for normal PaiCLI RAG)
 */
public class RetrievalEvalRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    record QuerySpec(String id, String query, List<String> groundTruthTargets) {}

    static List<QuerySpec> QUERIES = List.of(
            new QuerySpec("q01", "How does HITL approval block dangerous tool execution?",
                    List.of("HitlToolRegistry.java", "HitlHandler.java")),
            new QuerySpec("q02", "Where is CommandGuard's blacklist of dangerous commands?",
                    List.of("CommandGuard.java")),
            new QuerySpec("q03", "How does PathGuard prevent directory traversal attacks?",
                    List.of("PathGuard.java")),
            new QuerySpec("q04", "How are tool calls executed in parallel?",
                    List.of("ToolRegistry.java")),
            new QuerySpec("q05", "How does the AuditLog record dangerous tool calls?",
                    List.of("AuditLog.java")),
            new QuerySpec("q06", "How does ScriptedHitlHandler return pre-configured decisions?",
                    List.of("ScriptedHitlHandler.java")),
            new QuerySpec("q07", "How does the planner generate execution plans?",
                    List.of("Planner.java")),
            new QuerySpec("q08", "How does MCP tool bridge route calls to external servers?",
                    List.of("McpServerManager.java")),
            new QuerySpec("q09", "How does the network policy block localhost and private IPs?",
                    List.of("NetworkPolicy.java")),
            new QuerySpec("q10", "How does the VectorStore perform similarity search?",
                    List.of("VectorStore.java"))
    );

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: RetrievalEvalRunner <project-path> <output-json>");
            System.exit(1);
        }

        String projectPath = args[0];
        Path outputPath = Path.of(args[1]);

        System.out.println("=== PaiCLI RAG Retrieval Eval ===");
        System.out.println("Project: " + projectPath);
        System.out.println();

        EmbeddingClient embeddingClient = new EmbeddingClient();
        CodeIndex index = new CodeIndex(embeddingClient);
        CodeIndex.IndexResult indexResult = index.index(projectPath);
        System.out.println("Index: " + indexResult.chunkCount() + " chunks, " + indexResult.relationCount() + " relations");
        System.out.println();

        CodeRetriever retriever = new CodeRetriever(projectPath, embeddingClient);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("evalType", "rag-retrieval-vs-lexical");
        root.put("projectPath", projectPath);
        root.put("chunkCount", indexResult.chunkCount());
        root.put("relationCount", indexResult.relationCount());

        ArrayNode resultsArray = root.putArray("results");
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        summaryMap.put("lexical", new double[3]);
        summaryMap.put("semantic", new double[3]);
        summaryMap.put("hybrid", new double[3]);

        int queryCount = QUERIES.size();

        for (QuerySpec spec : QUERIES) {
            System.out.println("Query: " + spec.id() + " - " + spec.query());
            ObjectNode queryResult = MAPPER.createObjectNode();
            queryResult.put("id", spec.id());
            queryResult.put("query", spec.query());
            ArrayNode gtArray = queryResult.putArray("groundTruthTargets");
            spec.groundTruthTargets().forEach(gtArray::add);

            for (String mode : List.of("keyword", "semantic", "hybrid")) {
                List<VectorStore.SearchResult> results;
                try {
                    results = switch (mode) {
                        case "keyword" -> retriever.keywordSearch(spec.query());
                        case "semantic" -> retriever.semanticSearch(spec.query(), 10);
                        case "hybrid" -> retriever.hybridSearch(spec.query(), 10);
                        default -> List.of();
                    };
                } catch (Exception e) {
                    System.err.println("  " + mode + " mode failed: " + e.getMessage());
                    results = List.of();
                }

                double recallAt5 = recallAtK(results, spec.groundTruthTargets(), 5);
                double recallAt10 = recallAtK(results, spec.groundTruthTargets(), 10);
                double mrr = mrr(results, spec.groundTruthTargets());

                ObjectNode modeResult = queryResult.putObject(mode);
                modeResult.put("recallAt5", recallAt5);
                modeResult.put("recallAt10", recallAt10);
                modeResult.put("mrr", mrr);

                ArrayNode filesArray = modeResult.putArray("topFiles");
                results.stream()
                        .limit(10)
                        .map(r -> r.filePath() + "#" + r.name())
                        .forEach(filesArray::add);

                System.out.printf("  %s: R@5=%.2f R@10=%.2f MRR=%.3f%n", mode, recallAt5, recallAt10, mrr);

                String summaryKey = mode.equals("keyword") ? "lexical" : mode;
                double[] sums = (double[]) summaryMap.get(summaryKey);
                sums[0] += recallAt5;
                sums[1] += recallAt10;
                sums[2] += mrr;
            }

            resultsArray.add(queryResult);
        }

        ObjectNode summaryNode = root.putObject("summary");
        for (String mode : List.of("keyword", "semantic", "hybrid")) {
            String summaryKey = mode.equals("keyword") ? "lexical" : mode;
            double[] sums = (double[]) summaryMap.get(summaryKey);
            ObjectNode modeSummary = summaryNode.putObject(mode);
            modeSummary.put("avgRecallAt5", sums[0] / queryCount);
            modeSummary.put("avgRecallAt10", sums[1] / queryCount);
            modeSummary.put("avgMrr", sums[2] / queryCount);
        }

        Files.createDirectories(outputPath.getParent());
        MAPPER.writeValue(outputPath.toFile(), root);
        System.out.println();
        System.out.println("Results written to: " + outputPath);

        retriever.close();
    }

    static double recallAtK(List<VectorStore.SearchResult> results, List<String> targets, int k) {
        if (targets.isEmpty()) return 1.0;
        Set<String> found = new HashSet<>();
        int limit = Math.min(k, results.size());
        for (int i = 0; i < limit; i++) {
            for (String target : targets) {
                if (results.get(i).filePath().contains(target)
                        || results.get(i).name().contains(target.replace(".java", ""))) {
                    found.add(target);
                    break;
                }
            }
        }
        return (double) found.size() / targets.size();
    }

    static double mrr(List<VectorStore.SearchResult> results, List<String> targets) {
        for (int i = 0; i < results.size(); i++) {
            for (String target : targets) {
                if (results.get(i).filePath().contains(target)
                        || results.get(i).name().contains(target.replace(".java", ""))) {
                    return 1.0 / (i + 1);
                }
            }
        }
        return 0.0;
    }
}