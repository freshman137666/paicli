# PaiCLI 架构拆解

> Skill usage marker: used the project-teardown skill

## 第一部分：全局概览

### 一句话判断

这是一个**教学导向的商业级 Java Agent CLI 产品**，从最原始的 ReAct 循环出发，按工程化需求逐层叠加 Plan-and-Execute、Memory、RAG、Multi-Agent、HITL、并发工具调用、多模型适配、联网搜索、MCP 协议、resources 双轨等能力，最终形成了一套可交付的 Agent 终端产品。它不是在追技术热点，而是在回答一个核心问题：**一个 Agent CLI 要真的能商用，到底需要哪些工程基础设施。**

### 项目本质

PaiCLI 的本质不是"一个调 LLM API 的壳"，也不是"一个玩具项目"。它本质上是一个**围绕 LLM 推理循环逐步构建的工程防护体系**。

如果你把 Agent 拆成两层来看：

- **上层（推理层）**：LLM 负责思考、决策、调用工具 —— 这部分 PaiCLI 只管"把工具描述和对话喂给模型，把结果收回来"。
- **下层（防护层）**：所有支撑 LLM 推理稳定运行的基础设施 —— Memory 防上下文溢出、TokenBudget 防死循环、HITL 防危险操作、PathGuard/CommandGuard 策略防恶意命令、AuditLog 可追溯、MCP 工具默认审批 —— 这些才是 PaiCLI 真正花大力气建的东西。

所以这个项目的设计灵魂不在于"怎么调 GLM/DeepSeek"，而在于：**当 LLM 是一个不可靠的、会幻觉的、会做出危险决策的"智能体"时，你需要在它周围建多少层护栏，才能让它安全地在你电脑上跑。**

### 核心业务痛点

PaiCLI 要解决的痛点不是技术炫技，而是非常现实的工程问题：

1. **LLM 不可靠性 vs 文件系统安全**：LLM 可能生成 `rm -rf /`、写入 `~/.bashrc`、路径穿越到项目之外。你不能信任 LLM 的自我约束——必须有一层"不相信任何 LLM 输出"的硬策略。
2. **上下文爆炸 vs 对话连续性**：多轮工具调用后，`tool result` 不断累积，token 预算逼近上限，模型开始遗忘早期信息。单纯截断会丢失关键上下文，AI 摘要可能扭曲事实。
3. **单 Agent 能力上限 vs 复杂任务**：一个 LLM 面对"拆解任务、执行步骤、验证结果"这三件事时，往往会顾此失彼。需要分工——有人规划、有人执行、有人检查。
4. **工具调用串行 vs 实际需求**：LLM 可以同一轮返回 5 个 `read_file` 调用，如果串行执行，等待时间累加，用户体验不可接受。
5. **外部工具（MCP server）安全性**：MCP 协议让第三方提供的工具进入你的 Agent 系统——这些工具可能读写你的文件系统、执行你的命令——不能像内置工具一样信任它们。

### 一条核心请求的完整旅行

让我们追踪用户在 PaiCLI 中输入 `帮我创建一个 Java 项目叫 demo，读取 pom.xml，然后跑一下 mvn test` 后发生了什么：

```
用户输入
  │
  ▼
Main.java ──→ CliCommandParser.parse() 判断：普通自然语言，非 slash 命令
  │
  ▼
AtMentionExpander 展开 @server:uri 引用（本次无 @ 引用，原样返回）
  │
  ▼
Agent.run(userInput)
  │
  ├─→ 1. MemoryManager.addUserMessage()    // 写入短期记忆
  ├─→ 2. MemoryRetriever.buildContext()    // 从长期记忆检索 500 字相关事实
  ├─→ 3. updateSystemPromptWithMemory()    // 注入到 system prompt
  ├─→ 4. conversationHistory.add(user)     // 追加 user message
  │
  ▼
┌─ ReAct 主循环 ──────────────────────────────────────────┐
│                                                         │
│  while(true):                                           │
│    │                                                    │
│    ├─→ AgentBudget.check()   // 三保险：token/死循环/轮次 │
│    ├─→ CancellationContext   // 用户按了 /cancel 吗？     │
│    ├─→ llmClient.chat()      // 调 GLM/DeepSeek API     │
│    │     │                                              │
│    │     ├─ reasoning_content → 流式渲染 "🧠 思考过程"     │
│    │     └─ content → 流式渲染 "🤖 回复"                  │
│    │                                                    │
│    ├─ 有 tool_calls?                                    │
│    │   ├─ YES →                                          │
│    │   │   ├─ ToolRegistry.executeTools(){              │
│    │   │   │   并行执行 N 个工具（ThreadPool, max=4）      │
│    │   │   │   │                                        │
│    │   │   │   ├─ 工具1: create_project → PathGuard → OK │
│    │   │   │   ├─ 工具2: read_file    → PathGuard → OK  │
│    │   │   │   └─ 工具3: execute_command                │
│    │   │   │        ├─ CommandGuard.check() → 不是黑名单 │
│    │   │   │        ├─ HITL 拦截（如启用）→ 用户确认      │
│    │   │   │        └─ ProcessBuilder 执行               │
│    │   │   │   }                                        │
│    │   │   ├─ 结果灌回 conversationHistory 为 tool 消息   │
│    │   │   └─ continue（让 LLM 继续思考）                │
│    │   │                                                │
│    │   └─ NO →                                          │
│    │       ├─ MemoryManager.addAssistantMessage()        │
│    │       ├─ return 格式化响应                           │
│    │       └─ 循环结束                                   │
└─────────────────────────────────────────────────────────┘
```

这条路径看起来很直观（ReAct 标准循环），但真正关键的是中间每个环节的**防护逻辑**：PathGuard 拦截越界路径、CommandGuard 拦截破坏性命令、AgentBudget 兜底死循环、HITL 让用户在危险操作前确认——这些才是一个"能用的"Agent CLI 和"玩具 Demo"之间的区别。

### 最核心的架构思想

用一句话概括：**"信任 LLM 的智慧，但永远不信任 LLM 的决策"**。

PaiCLI 的整体架构不是围绕"更好的 AI"设计的，而是围绕"AI 的不可靠性的工程化解"设计的。你可以在每个模块中看到这种思想的影子：

- LLM 决定调用什么工具 → 但工具执行前要经过策略层（PathGuard/CommandGuard/HITL）
- LLM 可以自由对话 → 但 Memory 系统会默默的压缩/管理上下文，确保 LLM 不会因为它自己的输出太长而崩掉
- LLM 可以跑无限轮 → 但 AgentBudget 有硬兜底，超了就截断
- LLM 可以调用外部 MCP 工具 → 但所有 MCP 工具默认走 HITL + 审计

这不是对 AI 的不信任，而是工程上的**纵深防御（defense-in-depth）思维**：每一层都不相信上一层是完美无缺的。

### 最值得先理解的 4 个核心机制

如果只能选 4 个来理解这个项目，它们是：

1. **ReAct 循环 + AgentBudget 三保险**：这是整个项目的"心脏"。理解 LLM 怎么和工具交互、预算怎么兜底——其他所有模块（Plan/Team/Memory）本质上都是在这个循环的变体上叠加额外逻辑。

2. **工具执行的三层防护**：`HitlToolRegistry → ToolRegistry → 策略层（PathGuard/CommandGuard）` 的调用链，以及策略层怎么在你不知道的时候拦截危险操作。这是这个项目和 GitHub 上 90% 的玩具 Agent 项目最本质的区别。

3. **上下文工程（Memory + TokenBudget + 摘要压缩）**：LLM 有 context window 上限，工具调用会不断往里塞内容——怎么在"不丢掉关键信息"和"不超预算"之间拉扯，是 Agent 产品化的核心难题。

4. **MCP 协议集成（工具 + resources 双轨）**：MCP 让 PaiCLI 的工具集可以无限扩展，但引入外部工具也带来了安全风险——它是怎么把 MCP 工具无缝注册到内置工具体系、同时强制所有 MCP 工具走 HITL 审批的。

### 当前全景总结

PaiCLI 当前是一个 **v11.0.0 的 Java Agent CLI**，已完成 11 期演进：

| 期数 | 能力 | 核心价值 |
|------|------|---------|
| 1 | ReAct 循环 | 让 LLM 能调用工具 |
| 2 | Plan-and-Execute + DAG | 让 LLM 能处理复杂多步任务 |
| 3 | Memory + 上下文工程 | 让对话不爆 context window |
| 4 | RAG + 代码库理解 | 让 LLM 能理解你的项目代码 |
| 5 | Multi-Agent 协作 | 让多个 LLM 分工合作 |
| 6 | HITL + 审批流 | 让用户能在危险操作前拦截 |
| 7 | 异步 + 并行工具调用 | 让多个工具可以同时跑 |
| 8 | 多模型适配 | 让你可以在 GLM/DeepSeek 间切换 |
| 9 | 联网搜索 + 网页抓取 | 让 LLM 能获取实时信息 |
| 10 | MCP 协议核心 | 让你能接入第三方 MCP server |
| 11 | MCP 高级能力 | resources 双轨 / 被动通知 / 取消 |

技术栈：Java 17 + Maven + OkHttp + Jackson + JLine3 + SQLite + JavaParser + Ollama

### 建议下一步深入拆解的方向

**优先拆解「工具执行的三层防护体系」**——这是 PaiCLI 最区别于其他 Agent CLI 的地方。具体来说：

- HITL 是怎么做到不修改 Agent 原有逻辑、透明插入审批的（继承 ToolRegistry 覆写 executeTool）
- PathGuard 的符号链接逃逸是怎么检测的（`Files.toRealPath` + 剩余段拼接）
- CommandGuard 为什么是"辅助"而非"主防线"（黑名单永远列不全，安全责任在 HITL）
- 策略层和 HITL 的协同顺序为什么是 `HitlToolRegistry → ToolRegistry → 策略层`（用户批准后策略层仍会校验，用户无法批准策略拒绝的请求）

理解了这个防护体系，你就理解了 PaiCLI 的工程骨架。之后再深入拆解「上下文工程」或「MCP 集成」会更有层次感。

---

## 第二部分：后续专题拆解（预留）

以下专题将在后续对话中逐步深入填充。

### 工具执行的三层防护

> 待深入拆解。

### 上下文工程：Memory + TokenBudget + 压缩

> 待深入拆解。

### MCP 协议集成与安全

> 待深入拆解。

### Multi-Agent 编排与协作

> 待深入拆解。
