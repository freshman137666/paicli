package com.paicli.mcp;

import java.util.List;

/**
 * MCP server 启动事件通知接口。
 * <p>
 * startAllAsync 把启动过程中的 per-server 就绪/失败事件交给此接口，
 * 调用方可以按需缓冲、合并或转发到不同的终端输出路径（renderer、status bar 等）。
 * 单元测试可通过 mock 实现验证"事件记录正确"而非"System.out 输出格式"。
 */
public interface McpStartupNotifier {

    /** 单台 server 启动成功。toolCount 表示已注册的工具数量。 */
    void onServerReady(String name, int toolCount);

    /** 单台 server 启动失败。 */
    void onServerFailed(String name, String errorMessage);

    /** 全部 server（含 disabled）启动流程结束，无论成功或失败。 */
    void onStartupComplete();

    /** 无操作的 notifier，调用方不需要展示任何启动信息时使用。 */
    static McpStartupNotifier noop() {
        return new McpStartupNotifier() {
            @Override public void onServerReady(String name, int toolCount) {}
            @Override public void onServerFailed(String name, String errorMessage) {}
            @Override public void onStartupComplete() {}
        };
    }
}
