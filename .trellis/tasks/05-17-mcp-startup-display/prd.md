# MCP 启动终端展示优化

## 目标
消除 async MCP startup 的 per-server 裸写 System.out 导致的终端展示交错，改用抽象接口 + 合并摘要模式。

## 需求
1. 启动时只打印一行：`🔌 MCP server 后台启动中，可用 /mcp 查看状态`
2. 切到 `McpStartupNotifier` 接口抽象，不再直接写 System.out
3. `MergedMcpStartupNotifier` 缓冲所有就绪/失败事件，`onStartupComplete()` 后通过 `drain()` 输出合并摘要
4. `drain()` 只在安全窗口（进入主循环前、每次用户输入前）被调用，避免与 JLine 输入区交错
5. `startAll()` 同步兼容：创建 notifier → 等 futures → drain → println 到 PrintStream
6. `startAllAsync()` 接受 McpStartupNotifier，不在 async 线程写终端
7. 异步语义不变：`awaitServer` 行为不变

## 验收条件
- [ ] 启动后裸写 `⏳ xxx 启动中` / `✓ xxx 就绪` 消失
- [ ] 启动完成后仅一条 `🔌 MCP 已就绪：name N、name M`（如果完成早于进入主循环）
- [ ] 如果启动尚未完成时已进入交互，摘要不出现在 `👤 你:` 行，也不交错
- [ ] `McpServerManagerTest` 和 `MergedMcpStartupNotifierTest` 通过
- [ ] 测试覆盖：单 server、多 server、混合就绪/失败、drain 不重复、late completion
- [ ] 向后兼容：`startAll(PrintStream)` 仍能输出启动摘要

## 涉及文件
- 新增：`src/main/java/com/paicli/mcp/McpStartupNotifier.java`
- 新增：`src/main/java/com/paicli/mcp/MergedMcpStartupNotifier.java`
- 新增：`src/test/java/com/paicli/mcp/MergedMcpStartupNotifierTest.java`
- 修改：`src/main/java/com/paicli/mcp/McpServerManager.java`
- 修改：`src/main/java/com/paicli/cli/Main.java`

## Out of Scope
- Renderer 集成（当前为纯 CLI 路径）
- npx 缓存优化
- MCP transport/jsonrpc 改动
