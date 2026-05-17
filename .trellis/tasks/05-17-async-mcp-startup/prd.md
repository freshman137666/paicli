# MCP 启动非阻塞化

## 目标
消除 `McpServerManager.startAll()` 同步阻塞对 CLI 启动速度的影响，让用户在 MCP server 后台初始化期间即可开始交互。

## 需求
1. `McpServerManager.startAllAsync()` 后台启动所有 server，不阻塞调用线程
2. 保留同步 `startAll()` 包装，内部复用 async futures 并 `.join()`，避免两套启动逻辑
3. Manager 保存每个 server 的 `CompletableFuture<Void>`，新增 `awaitServer(name, timeout)` 供按需等待
4. `McpServerManager.wrapInvoker()` 在调用 MCP 工具前检查 server 状态：
   - READY → 正常调用
   - STARTING → `awaitServer(name, 5s)`，超时返回"工具暂不可用"
   - ERROR/DISABLED → 直接返回失败提示
5. `/browser connect/tabs` 等显式命令也走 `awaitServer("chrome-devtools", 5s)`
6. 单 server 就绪/失败时打印一行：`✓ chrome-devtools 就绪（4 个工具）` 或 `✗ chrome-devtools 启动失败：<原因>`
7. `Main.java` 启动流程：`loadConfiguredServers()` → `startAllAsync(System.out)`（不阻塞） → 继续初始化 LineReader / Agent / Renderer / 主循环

## 验收条件
- [ ] 启动后在 MCP server 尚未就绪时即可输入第一条消息
- [ ] `startAll()` 同步兜底与 async 复用同一套 future，行为一致
- [ ] MCP 工具调用时若 server 仍 STARTING，等待不超过 5s 后返回友好提示
- [ ] `/browser status` 等命令在 chrome-devtools 未就绪时提示等待或不可用
- [ ] 单 server 就绪/失败信息正确打印到 stdout
- [ ] 启动超时/异常不影响 CLI 进入交互循环

## 涉及文件
- `src/main/java/com/paicli/mcp/McpServerManager.java` — 新增 startAllAsync / awaitServer，改造 start / wrapInvoker
- `src/main/java/com/paicli/cli/Main.java` — startAll 改为 startAllAsync，调整启动顺序

## Out of Scope
- npx 缓存/冷启动优化
- JsonRpcClient / transport 层超时改动
- MCP 协议层改动
- ToolRegistry 结构性重构

## Decision (ADR-lite)
- **Context**：`startAll().join()` 导致 CLI 启动必须等所有 MCP server 就绪，chrome-devtools（npx + Chrome）是主要瓶颈
- **Decision**：后台异步启动 + MCP invoker 入口按 server 粒度按需等待
- **Consequences**：启动流程更复杂（需要处理 server 状态竞争）；jsonschema 未注册期间 MCP 工具不可调用，Agent 会拿到友好提示而非工具定义
