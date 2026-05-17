package com.paicli.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MergedMcpStartupNotifierTest {

    @Test
    void emptyWhenNoEvents() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        assertFalse(n.hasAnyEvent());
        assertEquals("", n.drain(), "无事件且未 complete 时 drain 应返回空");
    }

    @Test
    void emptyWhenNotComplete() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        n.onServerReady("demo", 3);
        assertTrue(n.hasAnyEvent());
        assertEquals("", n.drain(), "未 complete 时即使有事件也应返回空");
    }

    @Test
    void singleReadyServer() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        n.onServerReady("github", 26);
        n.onStartupComplete();
        assertEquals("🔌 MCP 已就绪：github 26", n.drain());
    }

    @Test
    void multipleReadyServers() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        n.onServerReady("github", 26);
        n.onServerReady("context7", 2);
        n.onServerReady("chrome-devtools", 29);
        n.onStartupComplete();
        assertEquals("🔌 MCP 已就绪：github 26、context7 2、chrome-devtools 29", n.drain());
    }

    @Test
    void mixedReadyAndFailed() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        n.onServerReady("github", 26);
        n.onServerFailed("chrome-devtools", "npx 安装失败");
        n.onStartupComplete();
        String summary = n.drain();
        assertTrue(summary.contains("github 26"));
        assertTrue(summary.contains("chrome-devtools 启动失败：npx 安装失败"));
    }

    @Test
    void singleDrainNoDuplicate() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        n.onServerReady("demo", 3);
        n.onStartupComplete();
        String first = n.drain();
        assertFalse(first.isEmpty(), "第一次 drain 应有内容");
        assertEquals("", n.drain(), "第二次 drain 应返回空");
        assertEquals("", n.drain(), "第三次 drain 也应返回空");
    }

    @Test
    void drainWorksAfterLateCompletion() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        n.onServerReady("github", 26);
        // complete 尚未触发
        assertEquals("", n.drain(), "complete 前 drain 应为空");
        // 稍后 complete 到达
        n.onStartupComplete();
        assertEquals("🔌 MCP 已就绪：github 26", n.drain(), "complete 后 drain 应有摘要");
    }

    @Test
    void drainAfterLateCompletionThenNoDuplicate() {
        MergedMcpStartupNotifier n = new MergedMcpStartupNotifier(true);
        n.onServerReady("demo", 3);
        assertEquals("", n.drain(), "第一次（complete 前）应为空");
        n.onStartupComplete();
        assertEquals("🔌 MCP 已就绪：demo 3", n.drain(), "complete 后应有摘要");
        assertEquals("", n.drain(), "第三次应为空");
    }

    @Test
    void noopNotifierDoesNothing() {
        McpStartupNotifier noop = McpStartupNotifier.noop();
        noop.onServerReady("demo", 3);
        noop.onServerFailed("bad", "err");
        noop.onStartupComplete();
        // noop 不实现 drain，仅验证无异常
    }
}
