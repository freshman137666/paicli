# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

<!--
Document your project's quality standards here.

Questions to answer:
- What patterns are forbidden?
- What linting rules do you enforce?
- What are your testing requirements?
- What code review standards apply?
-->

(To be filled by the team)

---

## Forbidden Patterns

<!-- Patterns that should never be used and why -->

(To be filled by the team)

---

## Required Patterns

<!-- Patterns that must always be used -->

### Chrome DevTools MCP tab reuse

When wrapping `chrome-devtools` MCP browser tools, keep tab reuse conservative and
observable:

- Reuse an existing tab only for `navigate_page` when the target URL and an
  existing page have the same normalized host.
- Normalize bare domains before navigation so values like `linux.do` become
  `https://linux.do`; otherwise chrome-devtools can hang or create surprising
  pages.
- Treat `list_pages`, JSON parsing, and `switch_page` failures as soft failures:
  fall back to the original `navigate_page` call instead of failing the user task.
- Do not close or replace tabs as part of same-host reuse. Sensitive pages and
  logged-in shared-browser state must remain under the browser safety policy.

### MCP 异步启动（startAllAsync + awaitServer）

MCP server（尤其 chrome-devtools）通过 npx 启动子进程，冷启动耗时数秒到十几秒。
CLI 启动时不应同步等待所有 server 就绪，而应采用异步启动 + 按需等待模式。

#### Scope / Trigger
- `startAll(PrintStream)` 会 `.join()` 阻塞当前线程，导致 CLI 在 MCP server 就绪前无法进入交互循环
- 异步启动解决启动感知延迟，不改变 MCP 协议层、transport 层或工具定义

#### 模式

```java
// McpServerManager 提供异步与同步两个入口
public void startAllAsync(PrintStream progressOut) { ... }  // 不阻塞，后台启动
public void startAll(PrintStream out) {
    startAllAsync(out);
    CompletableFuture.allOf(startupFutures.values().toArray(new CompletableFuture[0])).join();
}
```

- `startAllAsync` 启动后立即返回，每个 server 就绪/失败时通过 `whenComplete` 打印单行状态
- 同步 `startAll` 复用同一套 async futures 并 `.join()`，不做两套启动逻辑

#### Contracts

**`awaitServer(String name, Duration timeout)`**
- 返回值：boolean — true 表示 server 在 timeout 内变为 READY
- 调用方依赖 `server.status()` 判断后续行为，而非依赖返回值做唯一依据
- server 已 READY → 立即返回 true
- server 仍 STARTING → 等待对应 future，超时返回 false
- server ERROR/DISABLED → 返回 false

**MCP invoker status guard（`wrapInvoker` 内）**
- READY → 正常调用 `client.callToolOutput()`
- STARTING → `awaitServer(name, 5s)`，超时返回 `"⌛ MCP server {name} 仍在启动中，请稍后重试"`
- ERROR/DISABLED → 返回 `"MCP server {name} 不可用: {errorMessage}"`

**`Main.java` 启动顺序（稳定契约）**
```
loadConfiguredServers()        // 同步，必顺
startAllAsync(System.out)      // 异步，不阻塞
→ 继续初始化 LineReader / Agent / Renderer / 主循环
```

#### Validation & Error Matrix
| 条件 | 行为 |
|------|------|
| server 在 5s 内就绪 | 工具正常调用 |
| server 超过 5s 仍未就绪 | 返回"仍在启动中"提示，不阻塞 |
| server 启动失败 | 返回"不可用"提示 |
| `list_pages` 等 /browser 命令 | `awaitServer("chrome-devtools", 5s)` 后决定就绪与否 |

#### Good / Base / Bad Cases

**Good**: CLI 启动 → 立即出现 `🔌 MCP server 后台启动中...` → 用户开始输入 → 后台打印 `✓ chrome-devtools 就绪（4 个工具）`
**Base**: chrome-devtools 配置错误（npx 安装失败）→ 启动阶段不阻塞 CLI → error 行打印到 stdout → 用户按需排查
**Bad**: `startAllAsync` 内部异常未被捕获 → 后台线程静默失败 → server 永远 STALLED → `awaitServer` 也等不到结束

#### 禁止模式
- 不要在 `startAllAsync` 外部的 try-catch 包裹启动逻辑（async 失败不应影响 CLI 主线程）
- 不要在 `wrapInvoker` 之外手动检查 server status（应统一走 status guard 避免遗漏）
- 不要新增独立的同步启动路径（复用 async futures）

---

## Testing Requirements

<!-- What level of testing is expected -->

(To be filled by the team)

---

## Code Review Checklist

<!-- What reviewers should check -->

(To be filled by the team)
