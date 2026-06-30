# ch02

First runnable agent loop, ported directly from Go `ch02/go-tiny-claw`.

## 复刻对照

| Go 文件 | Java 对应 | 复刻内容 |
| --- | --- | --- |
| `cmd/claw/main.go` | `cmd/ClawApplication.java` | 内置 `MockProvider` 和 `MockRegistry`，启动 `AgentEngine`，执行 `帮我检查当前目录的文件` |
| `internal/engine/loop.go` | `engine/AgentEngine.java` | 初始化 system/user 消息、循环调用模型、执行工具、追加 observation、无工具调用时退出 |
| `internal/provider/interface.go` | `provider/LlmProvider.java` | 定义统一模型接口 `generate(messages, availableTools)` |
| `internal/schema/message.go` | `schema/*.java` | 拆分为 `Role`、`Message`、`ToolCall`、`ToolResult`、`ToolDefinition` |
| `internal/tools/registry.go` | `tools/Registry.java` | 定义 `getAvailableTools()` 和 `execute(call)` 两个方法 |

Main package: `com.bugwiki.harness`.

Run from IDEA by opening the module and running
`com.bugwiki.harness.cmd.ClawApplication`.
