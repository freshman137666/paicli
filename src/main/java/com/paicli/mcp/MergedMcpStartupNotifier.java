package com.paicli.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 缓冲 + 合并 MCP server 启动事件，在安全输出窗口生成一条摘要。
 * <p>
 * 永远不主动写终端——调用方在已知不会与 LineReader 输入区交错的时刻
 * （进入主循环前、下一次用户输入前）调用 {@link #drain()} 并自行
 * {@code println} 摘要。
 * <p>
 * 一次摘要只输出一次：{@link #drain()} 首次返回非空字符串后，
 * 后续调用返回 ""。如果启动未完成时 drain 过一次，后续
 * {@link #onStartupComplete()} 触发后下一次 drain 会再返回一次摘要。
 */
public class MergedMcpStartupNotifier implements McpStartupNotifier {

    private final List<McpEvent> events = new CopyOnWriteArrayList<>();
    private volatile boolean complete;
    private volatile boolean drained;

    /**
     * @param deferred true 表示缓冲事件，由调用方在安全窗口 drain 输出；
     *                 false 表示每收到事件立即输出到 System.out（主要用于向后兼容同步模式，
     *                 当前未实现，仅保留占位）
     */
    public MergedMcpStartupNotifier(boolean deferred) {
    }

    @Override
    public void onServerReady(String name, int toolCount) {
        events.add(new McpEvent(name, toolCount, null));
    }

    @Override
    public void onServerFailed(String name, String errorMessage) {
        events.add(new McpEvent(name, 0, errorMessage));
    }

    @Override
    public void onStartupComplete() {
        complete = true;
    }

    /**
     * 在安全输出窗口提取摘要。
     * <p>
     * 返回非空字符串意味着调用方应在当前窗口 {@code println} 它。
     * 返回 "" 表示目前没有新内容可输出（启动未完成或已提取过）。
     *
     * @return 摘要字符串（可能为空）
     */
    public synchronized String drain() {
        if (!complete || drained || events.isEmpty()) {
            return "";
        }
        drained = true;
        return buildSummary();
    }

    /** 用于测试：是否已有至少一条事件。 */
    boolean hasAnyEvent() {
        return !events.isEmpty();
    }

    private String buildSummary() {
        List<String> readyParts = new ArrayList<>();
        List<String> failedParts = new ArrayList<>();
        for (McpEvent e : events) {
            if (e.errorMessage != null) {
                failedParts.add(e.name + " 启动失败：" + e.errorMessage);
            } else {
                readyParts.add(e.name + " " + e.toolCount);
            }
        }
        StringBuilder sb = new StringBuilder("🔌 MCP 已就绪");
        if (!readyParts.isEmpty()) {
            sb.append("：");
            sb.append(String.join("、", readyParts));
        }
        if (!failedParts.isEmpty()) {
            sb.append("；");
            sb.append(String.join("；", failedParts));
        }
        return sb.toString();
    }

    private record McpEvent(String name, int toolCount, String errorMessage) {}
}
