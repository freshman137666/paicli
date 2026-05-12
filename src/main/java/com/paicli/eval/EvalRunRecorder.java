package com.paicli.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Eval-only recorder. It is enabled only when PAICLI_EVAL_TRACE_DIR or
 * -Dpaicli.eval.trace.dir is set, so normal CLI runs keep their current output.
 */
public final class EvalRunRecorder implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ThreadLocal<EvalRunRecorder> CURRENT = new ThreadLocal<>();
    private static final int PREVIEW_LIMIT = 2_000;

    private final boolean enabled;
    private final Path traceDir;
    private final ObjectNode trace;
    private final ObjectNode usage;
    private final ArrayNode toolCalls;
    private final ArrayNode hitlDecisions;
    private final ArrayNode policyDenials;
    private final ArrayNode planTasks;
    private final ArrayNode teamSteps;
    private final ArrayNode llmCalls;

    private int totalInputTokens;
    private int totalOutputTokens;
    private long totalLatencyMs;

    private EvalRunRecorder(boolean enabled, Path traceDir, String runMode,
                            String userInput, String provider, String model) {
        this.enabled = enabled;
        this.traceDir = traceDir;
        this.trace = MAPPER.createObjectNode();
        this.usage = MAPPER.createObjectNode();
        this.toolCalls = MAPPER.createArrayNode();
        this.hitlDecisions = MAPPER.createArrayNode();
        this.policyDenials = MAPPER.createArrayNode();
        this.planTasks = MAPPER.createArrayNode();
        this.teamSteps = MAPPER.createArrayNode();
        this.llmCalls = MAPPER.createArrayNode();

        trace.put("runMode", runMode);
        trace.put("userInput", userInput == null ? "" : userInput);
        trace.put("finalAnswer", "");
        trace.set("toolCalls", toolCalls);
        trace.set("hitlDecisions", hitlDecisions);
        trace.set("policyDenials", policyDenials);
        trace.set("planTasks", planTasks);
        trace.set("teamSteps", teamSteps);

        usage.put("provider", provider == null ? "" : provider);
        usage.put("model", model == null ? "" : model);
        usage.set("calls", llmCalls);
        usage.put("totalInputTokens", 0);
        usage.put("totalOutputTokens", 0);
        usage.put("totalTokens", 0);
        usage.put("totalLatencyMs", 0);
    }

    public static EvalRunRecorder startIfEnabled(String runMode, String userInput,
                                                 String provider, String model) {
        String configured = System.getProperty("paicli.eval.trace.dir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("PAICLI_EVAL_TRACE_DIR");
        }
        if (configured == null || configured.isBlank()) {
            EvalRunRecorder recorder = new EvalRunRecorder(false, null, runMode, userInput, provider, model);
            CURRENT.set(recorder);
            return recorder;
        }

        EvalRunRecorder recorder = new EvalRunRecorder(
                true,
                Path.of(configured).toAbsolutePath().normalize(),
                runMode,
                userInput,
                provider,
                model
        );
        CURRENT.set(recorder);
        recorder.flushQuietly();
        return recorder;
    }

    public static EvalRunRecorder current() {
        return CURRENT.get();
    }

    public static void propagateFrom(EvalRunRecorder recorder) {
        if (recorder != null) {
            CURRENT.set(recorder);
        } else {
            CURRENT.remove();
        }
    }

    public static void clearThread() {
        CURRENT.remove();
    }

    public static boolean isEnabled() {
        EvalRunRecorder recorder = CURRENT.get();
        return recorder != null && recorder.enabled;
    }

    public void recordLlmCall(int inputTokens, int outputTokens, long latencyMs) {
        if (!enabled) {
            return;
        }
        ObjectNode call = MAPPER.createObjectNode();
        call.put("inputTokens", Math.max(inputTokens, 0));
        call.put("outputTokens", Math.max(outputTokens, 0));
        call.put("totalTokens", Math.max(inputTokens, 0) + Math.max(outputTokens, 0));
        call.put("latencyMs", Math.max(latencyMs, 0));
        llmCalls.add(call);

        totalInputTokens += Math.max(inputTokens, 0);
        totalOutputTokens += Math.max(outputTokens, 0);
        totalLatencyMs += Math.max(latencyMs, 0);
        usage.put("totalInputTokens", totalInputTokens);
        usage.put("totalOutputTokens", totalOutputTokens);
        usage.put("totalTokens", totalInputTokens + totalOutputTokens);
        usage.put("totalLatencyMs", totalLatencyMs);
        flushQuietly();
    }

    public void recordToolCall(String id, String name, String argumentsJson,
                               String result, long durationMs, boolean timedOut) {
        if (!enabled) {
            return;
        }
        ObjectNode call = MAPPER.createObjectNode();
        call.put("id", id == null ? "" : id);
        call.put("name", name == null ? "" : name);
        call.put("arguments", argumentsJson == null ? "" : argumentsJson);
        call.put("resultPreview", preview(result));
        call.put("durationMs", Math.max(durationMs, 0));
        call.put("timedOut", timedOut);
        toolCalls.add(call);
        flushQuietly();
    }

    public void recordHitlDecision(String toolName, String decision, String reason,
                                   long durationMs) {
        if (!enabled) {
            return;
        }
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("tool", toolName == null ? "" : toolName);
        entry.put("decision", decision == null ? "" : decision);
        entry.put("reason", reason == null ? "" : reason);
        entry.put("durationMs", Math.max(durationMs, 0));
        hitlDecisions.add(entry);
        flushQuietly();
    }

    public void recordPolicyDenial(String toolName, String reason, long durationMs) {
        if (!enabled) {
            return;
        }
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("tool", toolName == null ? "" : toolName);
        entry.put("reason", reason == null ? "" : reason);
        entry.put("durationMs", Math.max(durationMs, 0));
        policyDenials.add(entry);
        flushQuietly();
    }

    public void recordPlanTask(String id, String status, String dependencies, String resultSummary) {
        if (!enabled) {
            return;
        }
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("id", id == null ? "" : id);
        entry.put("status", status == null ? "" : status);
        entry.put("dependencies", dependencies == null ? "" : dependencies);
        entry.put("resultSummary", preview(resultSummary));
        planTasks.add(entry);
        flushQuietly();
    }

    public void recordTeamStep(String id, String status, String workerId,
                               boolean reviewApproved, int retryCount, String resultSummary) {
        if (!enabled) {
            return;
        }
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("id", id == null ? "" : id);
        entry.put("status", status == null ? "" : status);
        entry.put("workerId", workerId == null ? "" : workerId);
        entry.put("reviewApproved", reviewApproved);
        entry.put("retryCount", Math.max(retryCount, 0));
        entry.put("resultSummary", preview(resultSummary));
        teamSteps.add(entry);
        flushQuietly();
    }

    public void finish(String finalAnswer) {
        if (!enabled) {
            return;
        }
        trace.put("finalAnswer", finalAnswer == null ? "" : finalAnswer);
        flushQuietly();
    }

    public static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    @Override
    public void close() {
        CURRENT.remove();
    }

    private void flushQuietly() {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(traceDir);
            MAPPER.writeValue(traceDir.resolve("trace.json").toFile(), trace);
            MAPPER.writeValue(traceDir.resolve("usage.json").toFile(), usage);
        } catch (IOException ignored) {
            // Eval output must never break a normal Agent run.
        }
    }

    private static String preview(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() <= PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LIMIT) + "...";
    }
}
