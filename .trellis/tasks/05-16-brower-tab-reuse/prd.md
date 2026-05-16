# 同域标签复用

## 问题
Agent 调用 `navigate_page` 时每次都新建标签页，导致同一个域（如 linux.do）在 Chrome 中积累多个标签页。

## 目标
在 `navigate_page` 执行前检查 Chrome 是否已有同域标签页，有则 `switch_page` 切换过去再导航，无则正常新建。

## 方案

### 新增文件
- `src/main/java/com/paicli/browser/BrowserTabManager.java`

### 修改文件
- `src/main/java/com/paicli/mcp/McpServerManager.java` — 在 `replaceTools` 中包装 `navigate_page` 的 invoker
- `src/main/java/com/paicli/cli/Main.java` — 实例化 `BrowserTabManager` 并注入到 `McpServerManager`

### 流程
```
Agent → navigate_page("https://linux.do/c/news/34")
  │
  ▼
McpServerManager 包装后的 invoker
  ├─ BrowserTabManager.findExistingTab(client, targetUrl)
  │    ├─ callToolOutput("list_pages", "{}")
  │    ├─ JSON 解析 → TabInfo list
  │    ├─ 提取目标域: linux.do
  │    └─ 匹配已有 tab → pageId="page_abc"
  │
  ├─ 匹配成功 → switch_page(pageId) → navigate_page(url)
  │
  └─ 无匹配 → 原样 navigate_page(args)
```

### 边界情况
- `list_pages` 失败 → 静默降级到原始 `navigate_page`
- `switch_page` 失败 → 降级到原始 `navigate_page`
- 域名提取失败 → 降级
- JSON 解析失败 → 尝试 regex fallback
- 域名匹配粒度：首版用 `URI.getHost()` 精确 host 匹配
