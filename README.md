# PaiCLI

一个终端优先（Terminal-First）的 Java Agent IDE，为开发者提供 AI 驱动的编程辅助体验。集成 ReAct 循环、Plan-and-Execute 任务编排、Multi-Agent 协作、代码库语义检索（RAG）、MCP 协议、多模型适配等核心能力，覆盖从日常编码到复杂开发的各类场景。

## 测试策略

日常开发不需要每次都跑全量测试。推荐按改动范围选择：

```bash
# 第 16 期终端 / TUI / inline renderer 冒烟
mvn test -Pphase16-smoke

# 常规快速回归，跳过外部进程 / 网络超时 / 命令超时类慢测试
mvn test -Pquick

# 发版或大范围重构前再跑全量
mvn test
```

## 项目介绍

PaiCLI 是一个终端优先（Terminal-First）的 Java Agent IDE，为开发者提供 AI 驱动的编程辅助体验。

### 核心能力

- **多 Agent 模式**：ReAct（默认）、Plan-and-Execute、Multi-Agent 三种执行模式，覆盖简单到复杂的各类开发场景
- **多模型适配**：内置 GLM-5.1/GLM-5V-Turbo、DeepSeek V4、StepFun、Kimi K2.6，支持运行时 `/model` 动态切换
- **代码语义检索**：Embedding 向量化 + SQLite 持久化 + 余弦相似度，支持自然语言搜代码与代码关系图谱
- **MCP 协议**：完整实现 stdio 与 Streamable HTTP，自动注册远程工具；内置 chrome-devtools 浏览器自动化
- **上下文工程**：长短记忆管理、上下文压缩、Token 预算控制、prompt cache 可见化与成本估算
- **HITL 安全机制**：危险操作三级审批、路径围栏、命令黑名单、结构化审计日志
- **图片输入**：粘贴板与 `@image:` 引用本地图片，自动压缩缩放后发送多模态模型
- **异步任务**：SQLite 持久化后台任务队列 + HTTP Runtime API
- **Skill 系统**：可插拔专家手册，将领域知识从 system prompt 抽离为可复用决策单元
- **LSP 诊断**：`write_file` 后自动语法诊断，支持 JavaParser 轻量分析

### 设计原则

- **终端优先**：所有交互在终端内完成，流式 TUI 默认提供状态栏、折叠块、git diff 等视觉反馈
- **安全默认**：HITL + 路径校验 + 命令黑名单 + 审计四位一体，不依赖伪沙箱
- **可扩展**：MCP 协议接入外部工具生态，Skill 系统注入领域知识，Prompt 分层架构支持全量覆盖
- **模型无关**：统一的 `LlmClient` 接口 + 模板基类，新增 provider 仅需约 20 行代码

## 启动界面

### 当前启动界面

当前启动输出以命令行实际产物为准：

```text
╔══════════════════════════════════════════════════════════╗
║                                                          ║
║   ██████╗  █████╗ ██╗ ██████╗██╗     ██╗                ║
║   ██╔══██╗██╔══██╗██║██╔════╝██║     ██║                ║
║   ██████╔╝███████║██║██║     ██║     ██║                ║
║   ██╔═══╝ ██╔══██║██║██║     ██║     ██║                ║
║   ██║     ██║  ██║██║╚██████╗███████╗██║                ║
║   ╚═╝     ╚═╝  ╚═╝╚═╝ ╚═════╝╚══════╝╚═╝                ║
║                                                          ║
║      Terminal-First Agent IDE v16.1.0                ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝

🔄 使用 ReAct 模式
```

## 功能

### 第一期

- 🤖 基于 GLM-5.1 的智能对话
- 🔄 ReAct Agent 循环（思考-行动-观察）
- 🛠️ 工具调用（文件操作、Shell命令、项目创建、代码语义检索、联网搜索、MCP 动态工具）
- 💬 交互式命令行界面
- 🧠 默认通过流式接口获取模型输出；ReAct 与用户可见的 Plan 阶段都会按流式展示思考过程与最终回复；ReAct 同一次用户输入只打印一次 `🧠 思考过程` 标题，工具调用前后的后续推理继续归在同一块下
- 🖥️ 终端会对常见 Markdown（标题、列表、表格、代码块）做渲染后再显示，避免直接暴露原始标记符号

### 第二期

- 📋 Plan-and-Execute + DAG 任务拆解与顺序执行
- ⌨️ `/plan` 一次性进入计划执行
- 🧭 更清晰的复杂任务执行顺序与依赖展示
- ⚖️ 简单任务会自动生成最小计划，不再为了凑步数扩展无关步骤

### 第三期

- 🧠 短期记忆、长期记忆与相关记忆检索
- 📦 长对话摘要压缩与 Token 预算管理
- 🧮 长上下文动态预算、prompt cache 可见化与成本估算
- 💾 `/memory` 与 `/save` 记忆管理入口

### 第四期

- 🔍 代码库语义检索（自然语言搜代码）
- 🕸️ 代码关系图谱（类继承、接口实现、方法调用）
- 📡 本地 Ollama Embedding + 远程 API 可配置
- 🗃️ SQLite 向量存储与持久化

### 第五期

- 👥 多 Agent 协作（规划者 + 执行者 + 检查者）
- 🎯 主从架构编排器自动分配任务
- 🔍 检查者审查质量，未通过自动重试
- 🛠️ 执行者共享工具集，支持文件操作与代码检索

### 第六期

- 🔒 危险操作静态规则识别（`write_file` / `execute_command` / `create_project` / `revert_turn`）
- ⚠️ 三级危险等级展示（高危 / 中危 / 安全）
- ✅ 审批决策：批准、全部放行、拒绝、跳过、修改参数后执行
- 🔓 HITL 默认关闭，`/hitl on` 启用、`/hitl off` 关闭

### 第七期

- ⚡ 同一轮多个工具调用会并行执行，适合同时读取多个文件、同时列目录、同时跑独立检查
- 🧵 ReAct、Plan-and-Execute、Multi-Agent Worker 共用同一套并行工具执行机制
- ⏱️ 工具批次有统一超时，超时工具会被取消并把超时结果回灌给模型
- 📋 Plan-and-Execute 与 Multi-Agent 会按 DAG 依赖批次并行推进独立任务

### 第八期

- 🔄 GLM-5.1、GLM-5V-Turbo、DeepSeek V4、阶跃星辰 StepFun 与 Kimi K2.6 多模型，`/model glm-5.1` / `/model glm-5v-turbo` 明确切 GLM 模型，`/model deepseek` / `/model step` / `/model kimi` 读取配置模型
- 🧱 `LlmClient` 接口 + 模板方法基类，新增 provider 只需 ~20 行
- 💾 默认模型持久化到 `~/.paicli/config.json`

### 第九期

- 🌐 `web_search` 工具支持三条路：智谱 Web Search（与 GLM 共用 Key 默认推荐）、SerpAPI（国际通用付费）、SearXNG（开源自托管免费）
- 📰 `web_fetch` 工具：抓 URL → readability 提取 → 返回 Markdown 正文
- 🛡️ 内置网络访问策略：屏蔽内网、loopback、`file://`；5MB 响应上限；每分钟 30 次限流
- 🚧 边界明确：SPA / 防爬墙返回空正文 + 已知边界提示，不重试

### 第六期 HITL 增强

- 🛡️ 路径围栏：文件类工具强制限定在项目根之内，绝对路径外逃 / `..` 穿越 / 符号链接逃逸全部拦截
- 🧯 命令快速拒绝：HITL 之前的 fast-fail 黑名单（`sudo` / `rm -rf 全盘` / `mkfs` / `dd of=/dev` / fork bomb / `curl|sh` / `find /` / `chmod 777 /` / `shutdown`），减少 HITL 弹窗骚扰
- 📦 资源上限：`write_file` 5MB；`execute_command` 60 秒超时 + 8KB 输出截断
- 📋 结构化审计：危险工具调用按天写一行 JSONL 到 `~/.paicli/audit/`，可通过 `/audit [N]` 查看
- 🧱 定位：HITL 之外的辅助层，不是沙箱、不提供进程隔离

## 快速开始

### 1. 配置 API Key

复制 `.env.example` 为 `.env`，并填入你的 GLM、DeepSeek、StepFun 或 Kimi API Key：

```bash
cp .env.example .env
# 编辑 .env 文件，填入你的 API Key
```

或者在环境变量中设置：

```bash
export GLM_API_KEY=your_api_key_here
# 或
export STEP_API_KEY=your_step_api_key_here
export STEP_MODEL=step-3.5-flash
# 或
export KIMI_API_KEY=your_kimi_api_key_here
export KIMI_MODEL=kimi-k2.6
```

长期记忆默认保存在用户目录下的 `~/.paicli/memory/long_term_memory.json`。
长期记忆只保存显式保存意图下的稳定事实：`/save <事实>`，或用户在自然语言里明确说“记一下 / 记住 / 以后记得”时由 Agent 调用 `save_memory`。它不应包含一次性任务请求或临时文件名/目录名。
代码索引默认保存在 `~/.paicli/rag/codebase.db`。
调试日志默认滚动写入 `~/.paicli/logs/paicli.log`，旧日志会按保留天数和总容量自动清理。
ReAct / Plan task / SubAgent / Planner 的模型 `reasoning_content` 会以 `LLM reasoning [...]` 形式写入该日志，便于排查模型为什么选择某个工具或路径。

如果你想为某次运行指定单独目录，可以额外传入：

```bash
# 指定记忆目录
java -Dpaicli.memory.dir=/tmp/paicli-memory -jar target/paicli-1.0-SNAPSHOT.jar

# 指定 RAG 索引目录
java -Dpaicli.rag.dir=/tmp/paicli-rag -jar target/paicli-1.0-SNAPSHOT.jar

# 指定日志目录与保留策略
java -Dpaicli.log.dir=/tmp/paicli-logs \
     -Dpaicli.log.level=DEBUG \
     -Dpaicli.log.maxHistory=3 \
     -Dpaicli.log.maxFileSize=5MB \
     -Dpaicli.log.totalSizeCap=20MB \
     -jar target/paicli-1.0-SNAPSHOT.jar
```

也可以放到 `.env` 或环境变量中：

```bash
PAICLI_LOG_LEVEL=DEBUG
PAICLI_LOG_DIR=/Users/yourname/.paicli/logs
PAICLI_LOG_MAX_HISTORY=7
PAICLI_LOG_MAX_FILE_SIZE=10MB
PAICLI_LOG_TOTAL_SIZE_CAP=100MB
```

### 2. 可选：配置 MCP server

MCP 子系统默认开启。`~/.paicli/mcp.json` 不存在时，PaiCLI 会自动创建默认 chrome-devtools 配置：

```json
{
  "mcpServers": {
    "chrome-devtools": {
      "command": "npx",
      "args": ["-y", "chrome-devtools-mcp@latest", "--isolated=true"]
    }
  }
}
```

需要继续接入其他 server 时，可编辑 `~/.paicli/mcp.json` 或项目内 `.paicli/mcp.json`：

```json
{
  "mcpServers": {
    "fetch": {
      "command": "uvx",
      "args": ["mcp-server-fetch"]
    },
    "git": {
      "command": "uvx",
      "args": ["mcp-server-git", "--repository", "${PROJECT_DIR}"]
    },
    "remote-demo": {
      "url": "https://mcp.example.com/v1",
      "headers": {"Authorization": "Bearer ${REMOTE_TOKEN}"}
    }
  }
}
```

`command` 表示 stdio server，`url` 表示 Streamable HTTP server。`${PROJECT_DIR}` / `${HOME}` 是内置变量，其他 `${VAR}` 从环境变量读取；缺失会在启动时直接提示。

需要复用当前登录态时，Chrome 144+ 推荐打开 `chrome://inspect/#remote-debugging` 并勾选 `Allow remote debugging for this browser instance`。旧版本或需要显式 CDP 端口时，可以启动带远程调试端口和独立 user-data-dir 的 Chrome，并在这个调试 Chrome 中完成登录：

```bash
# macOS
open -na "Google Chrome" --args --remote-debugging-port=9222 --user-data-dir=/tmp/paicli-chrome-profile

# Windows
start chrome.exe --remote-debugging-port=9222 --user-data-dir=%TEMP%\paicli-chrome-profile

# Linux
google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/paicli-chrome-profile
```

通常不需要用户预先切换；Agent 如果遇到登录页会自己调用 `browser_connect`。手工调试时也可以在 PaiCLI 内执行：

```text
/browser status
/browser connect
/browser tabs
/browser disconnect
```

`/browser connect` 只在当前进程内把 `chrome-devtools` 切到 shared 模式，不会改写 `~/.paicli/mcp.json`。如果希望启动后默认 shared，可手动把 args 改为：

```json
["-y", "chrome-devtools-mcp@latest", "--autoConnect"]
```

旧式 CDP HTTP JSON 端口也可使用：

```json
["-y", "chrome-devtools-mcp@latest", "--browser-url=http://127.0.0.1:9222"]
```

浏览器测试可直接让 Agent 读取动态页面，例如：

```text
帮我看下 https://mp.weixin.qq.com/s/RB7kF_BbsJZ5_Hmu9PxWdg 这篇文章讲了什么
```

期望路径是 `web_fetch` 尝试失败后，fallback 到 `mcp__chrome-devtools__navigate_page` 与 `take_snapshot`。

如果 server 支持 resources，可以直接查看或引用：

```text
/mcp resources filesystem
/mcp prompts filesystem
帮我看下 @filesystem:file://README.md 这份文档
```

OAuth 和 `sampling/createMessage` 当前未实现；远程 server 需要鉴权时仍使用 `headers` + 环境变量注入 Bearer token。

### 3. 安装到 PATH（可选，推荐）

将 `bin/` 目录加入系统 PATH，即可在任意目录下直接运行 `paicli` 命令（类似 `claude`）：

**Windows（CMD）：**
```batch
:: 临时添加
set PATH=%PATH%;D:\Code\MyProject\paicli\bin

:: 永久添加
:: setx PATH "%PATH%;D:\Code\MyProject\paicli\bin"
```

**Windows（PowerShell）：**
```powershell
# 临时添加（当前会话）
$env:PATH += ";D:\Code\MyProject\paicli\bin"

# 永久添加（推荐：注入到 PowerShell profile）
Add-Content -Path $PROFILE -Value "`n. D:\Code\MyProject\paicli\bin\paicli.ps1"
```

**Linux / macOS / Git Bash：**
```bash
# 临时添加
export PATH="$PATH:/path/to/paicli/bin"

# 永久添加（追加到 ~/.bashrc 或 ~/.zshrc）
echo 'export PATH="$PATH:/path/to/paicli/bin"' >> ~/.bashrc
```

添加后即可在任何目录运行：

```bash
cd /some/project
paicli          # 以当前目录为项目根运行
```

脚本会自动定位 jar 包，无需关心 `java -jar target/...` 的路径问题。

### 4. 编译运行

```bash
# 编译
mvn clean package

# 运行（需要本地 Ollama 已启动且拉取了 nomic-embed-text）
java -jar target/paicli-1.0-SNAPSHOT.jar
```

或者直接运行：

```bash
mvn clean compile exec:java -Dexec.mainClass="com.paicli.cli.Main"
```

### 5. 如何进入 Plan 模式

当前默认模式是 `ReAct`。进入 `Plan-and-Execute` 的方式只有 `/plan`：

1. 输入 `/plan`
2. 下一条任务会用计划模式执行
3. 执行完成后自动回到默认 `ReAct`

如果想一条命令切模式并执行任务，可以直接输入：

```text
/plan 创建一个 demo 项目，然后读取 pom.xml，最后验证项目结构
```

这条命令执行完成后，会自动回到默认的 `ReAct` 模式。

计划生成后，CLI 会先停下来等待确认：

- 按 `Enter`：按当前计划执行
- 按 `Ctrl+O`：展开完整计划
- 按 `ESC`：折叠完整计划或取消本次计划
- 按 `I`：输入补充要求并重新规划
- 按方向键不会触发取消；只有单独按下 `ESC` 才会取消待执行 plan

## 使用示例

### 第一期：ReAct 示例

```text
👤 你: 创建一个Java项目叫myapp

🧠 思考过程:
用户要创建一个 Java 项目。我先调用 create_project 工具生成基础结构，再根据工具返回结果确认是否创建成功。

🤖 最终结果:
已成功创建 Java 项目 "myapp"，包含基本的 Maven 结构。
```

### 第二期：Plan-and-Execute 示例

```text
💡 提示:
   - 输入你的问题或任务
   - 输入 '/' 查看命令
   - 输入 '@server:protocol://path' 可显式引用 MCP resource
   - 任务运行中按 ESC 取消当前任务
   - 默认模式是 ReAct
   - 未识别的 `/xxx` 命令会直接提示“未知命令”，不会再交给 Agent 当普通对话处理

👤 你: /plan 创建一个名为 demoapp 的 java 项目，然后读取 pom.xml，最后验证项目结构

📋 使用 Plan-and-Execute 模式

📋 正在规划任务: 创建一个名为 demoapp 的 java 项目，然后读取 pom.xml，最后验证项目结构

╔══════════════════════════════════════════════════════════╗
║  执行计划: 创建一个名为 demoapp 的 java 项目，然后读取... ║
╠══════════════════════════════════════════════════════════╣
║  1. ⏳ task_1               [COMMAND   ] 依赖: 无        ║
║     创建 demoapp 项目结构                              ║
║  2. ⏳ task_2               [FILE_READ ] 依赖: task_1    ║
║     读取 demoapp/pom.xml 内容                          ║
║  3. ⏳ task_3               [VERIFICATION] 依赖: task_2  ║
║     验证项目结构与 Maven 配置                          ║
╚══════════════════════════════════════════════════════════╝

📝 计划已生成。
   - 回车：按当前计划执行
   - ESC：取消本次计划
   - I：输入补充要求后重新规划

I
补充> 请在执行前先检查 README

📝 已收到补充要求，正在重新规划...

🚀 开始执行计划...
```

## 可用工具

- `read_file` - 读取文件内容
- `write_file` - 写入文件内容
- `list_dir` - 列出目录内容
- `execute_command` - 在当前项目目录执行短时 Shell 命令（默认 60 秒超时，黑名单拦截破坏性命令）
- `create_project` - 创建项目结构（java/python/node）
- `search_code` - 语义检索代码库（自然语言查询）
- `web_search` - 搜索互联网获取实时信息
- `web_fetch` - 抓取已知 URL 并提取正文 Markdown
- `revert_turn` - 恢复到最近第 N 个 pre-turn 快照（走 HITL 与审计）
- `mcp__{server}__{tool}` - MCP server 动态提供的外部工具
- `mcp__{server}__list_resources` / `mcp__{server}__read_resource` - 支持 resources 的 MCP server 自动注册的虚拟工具

同一轮模型返回多个工具调用时，PaiCLI 会并行执行这些工具；如果工具之间有依赖关系，模型应分多轮调用。

文件类工具（`read_file` / `write_file` / `list_dir` / `create_project`）路径强制限定在项目根之内，越界请求会被策略层拒绝；`execute_command` 通过命令黑名单拦截 `sudo` / `rm -rf 全盘` / `mkfs` / `dd of=/dev` / fork bomb / `curl|sh` 等。`revert_turn` 会批量回写工作区，默认触发 HITL 和审计。所有 `mcp__` 前缀工具默认触发 HITL 和审计。详见 `/policy`。

## 命令

- `/plan` - 下一条任务使用 Plan-and-Execute 模式
- `/plan <任务>` - 直接用 Plan-and-Execute 模式执行这条任务
- `/team` - 下一条任务使用 Multi-Agent 协作模式
- `/team <任务>` - 直接用 Multi-Agent 协作模式执行这条任务
- `/cancel` - 运行中请求取消当前任务；空闲时会提示当前没有正在运行的任务
- `/hitl on` - 启用危险操作人工审批（HITL）
- `/hitl off` - 关闭 HITL 审批
- `/hitl` - 查看 HITL 当前状态
- `/mcp` - 查看所有 MCP server 状态
- `/mcp restart <name>` - 重启单个 MCP server
- `/mcp logs <name>` - 查看 MCP server 最近 200 行 stderr 日志
- `/mcp disable <name>` - 运行时禁用 MCP server 并移除其工具
- `/mcp enable <name>` - 运行时启用 MCP server
- `/mcp resources <name>` - 查看 MCP server 暴露的 resources
- `/mcp prompts <name>` - 查看 MCP server 暴露的 prompts（只查看，不注入对话）
- `/policy` - 查看安全策略状态（路径围栏 / 命令黑名单 / 资源上限 / 审计目录）
- `/audit [N]` - 查看今日最近 N 条危险工具审计记录（默认 10）
- `/snapshot` - 查看最近 Side-Git 快照
- `/snapshot status` - 查看 Side-Git 快照状态
- `/snapshot clean` - 清理当前项目 Side-Git 快照目录
- `/restore <N>` - 恢复到最近第 N 个 pre-turn 快照
- `/memory` / `/mem` - 查看记忆系统状态
- `/memory clear` - 清空长期记忆
- `/save <事实>` - 手动保存关键事实到长期记忆
- `save_memory` - Agent 内置工具，仅在用户明确要求保存长期偏好或稳定事实时调用；“复用已登录 Chrome，记一下”这类浏览器登录态偏好会写入长期记忆，供新会话检索
- `/index [路径]` - 索引代码库（默认当前目录）
- `/search <查询>` - 语义检索代码
- `/graph <类名>` - 查看代码关系图谱
- `/clear` - 清空对话历史
- `/exit` / `/quit` - 退出程序

## 运行效果

### 第一期：旧版启动效果

```text
╔══════════════════════════════════════════════════════════╗
║                                                          ║
║   ██████╗  █████╗ ██╗      ██████╗██╗     ██╗            ║
║   ██╔══██╗██╔══██╗██║     ██╔════╝██║     ██║            ║
║   ██████╔╝███████║██║     ██║     ██║     ██║            ║
║   ██╔═══╝ ██╔══██║██║     ██║     ██║     ██║            ║
║   ██║     ██║  ██║███████╗╚██████╗███████╗██║            ║
║   ╚═╝     ╚═╝  ╚═╝╚══════╝ ╚═════╝╚══════╝╚═╝            ║
║                                                          ║
║              简单的 Java Agent CLI v1.0.0                ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
```

### 第三期：当前运行效果

```text
╔══════════════════════════════════════════════════════════╗
║                                                          ║
║   ██████╗  █████╗ ██╗ ██████╗██╗     ██╗                ║
║   ██╔══██╗██╔══██╗██║██╔════╝██║     ██║                ║
║   ██████╔╝███████║██║██║     ██║     ██║                ║
║   ██╔═══╝ ██╔══██║██║██║     ██║     ██║                ║
║   ██║     ██║  ██║██║╚██████╗███████╗██║                ║
║   ╚═╝     ╚═╝  ╚═╝╚═╝ ╚═════╝╚══════╝╚═╝                ║
║                                                          ║
║      Memory-Enhanced Agent CLI v3.0.0                 ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝

✅ API Key 已加载

🔄 使用 ReAct 模式

💡 提示:
   - 输入你的问题或任务
   - 输入 '/' 查看命令
   - 输入 '@server:protocol://path' 可显式引用 MCP resource
   - 任务运行中按 ESC 取消当前任务
   - 默认模式是 ReAct

👤 你: 你好，请列出当前目录的文件

🧠 思考过程:
用户想了解当前目录结构。我先读取目录，再基于结果做归类说明，而不是只回原始文件列表。

🤖 最终结果:
当前目录包含 `src`、`target`、`pom.xml`、`README.md` 等文件，
这是一个标准的 Java Maven 项目。

👤 你: /exit

👋 再见!
```

## 技术栈

- Java 17
- Maven
- GLM-5.1 API
- OkHttp
- Jackson
- JLine3（终端交互）
- SQLite（向量与图谱持久化）
- JavaParser（AST 分析）
- Ollama（本地 Embedding）

## 项目结构

```
src/main/java/com/paicli
├── agent/
│   ├── Agent.java              # ReAct Agent
│   ├── PlanExecuteAgent.java   # Plan-and-Execute Agent
│   ├── AgentRole.java          # Agent 角色枚举
│   ├── AgentMessage.java       # Agent 间通信消息
│   ├── SubAgent.java           # 可配置子代理
│   └── AgentOrchestrator.java  # Multi-Agent 编排器
├── cli/
│   ├── Main.java               # CLI 入口
│   ├── CliCommandParser.java   # 命令解析
│   └── PlanReviewInputParser.java  # 计划审核输入
├── llm/
│   ├── GLMClient.java          # GLM API 客户端；glm-5.1 走 Coding endpoint，glm-5v-turbo 走多模态 endpoint
│   ├── DeepSeekClient.java     # DeepSeek API 客户端
│   ├── StepClient.java         # 阶跃星辰 StepFun API 客户端
│   └── KimiClient.java         # Kimi / Moonshot API 客户端
├── context/
│   ├── ContextMode.java        # short / balanced / long 模式
│   ├── ContextProfile.java     # 模型窗口与上下文策略
│   └── TokenUsageFormatter.java # Token / cache / 成本展示
├── memory/
│   ├── MemoryEntry.java        # 记忆条目
│   ├── ConversationMemory.java # 短期记忆
│   ├── LongTermMemory.java     # 长期记忆
│   ├── ContextCompressor.java  # 上下文压缩
│   ├── TokenBudget.java        # Token 预算管理
│   ├── MemoryRetriever.java    # 记忆检索
│   └── MemoryManager.java      # 记忆门面类
├── plan/
│   ├── Task.java               # 任务定义
│   ├── ExecutionPlan.java      # 执行计划
│   └── Planner.java            # 规划器
├── rag/
│   ├── EmbeddingClient.java    # Embedding API 客户端
│   ├── VectorStore.java        # SQLite 向量存储
│   ├── CodeChunk.java          # 代码块模型
│   ├── CodeChunker.java        # 代码分块器
│   ├── CodeAnalyzer.java       # AST 关系分析
│   ├── CodeRelation.java       # 代码关系模型
│   ├── CodeIndex.java          # 索引管理器
│   └── CodeRetriever.java      # 检索入口
└── tool/
    └── ToolRegistry.java       # 工具注册表
```
