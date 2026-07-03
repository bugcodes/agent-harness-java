# agent-harness-java

![Java](https://img.shields.io/badge/Java-17-007396)
![Maven](https://img.shields.io/badge/Maven-multi--module-C71A36)
![Modules](https://img.shields.io/badge/chapters-22-blue)
![Provider](https://img.shields.io/badge/LLM-MiniMax%20%2F%20OpenAI--compatible-brightgreen)

用 Java 17 手写一套 Agent Harness，从最小 CLI 骨架开始，逐章实现模型调用、工具系统、上下文管理、审批中间件、子 Agent、评测、追踪和 AgentOps 入口。

这个项目是 `build-agent-harness-from-scratch` 教程的 Java 学习版。每个 `chXX` 都是独立 Maven module，适合在 IDEA 中按章节打开、阅读和运行。

## Features

- **Chapter-by-chapter learning**: `ch01` 到 `ch22` 保持渐进式结构，每章只引入少量新能力。
- **Runnable Java modules**: 每章都有自己的 `pom.xml` 和入口类，可以单独运行。
- **MiniMax by default**: 使用 MiniMax / OpenAI-compatible provider，支持根目录 `application-local.yml` 本地配置。
- **Agent core from scratch**: 覆盖 message schema、provider、tool registry、agent loop、reporter、memory、compactor、plan mode、approval middleware、subagent、benchmark 和 trace。
- **IDEA friendly**: 包根路径为 `com.bugwiki.harness`，右键运行 `ClawApplication.main()` 即可跟进章节实验。

## Requirements

- JDK 17
- Maven 3.8+
- IntelliJ IDEA, optional but recommended
- MiniMax API key, required from chapters that call the real model

## Quick Start

Clone and build:

```bash
git clone https://github.com/bugcodes/agent-harness-java.git
cd agent-harness-java

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean test
```

Run the first chapter from the command line:

```bash
mvn -q -pl ch01 compile exec:java \
  -Dexec.mainClass=com.bugwiki.harness.cmd.ClawApplication
```

Or open the project in IDEA, import it as a Maven project, then run:

```text
ch01/src/main/java/com/bugwiki/harness/cmd/ClawApplication.java
```

## MiniMax Configuration

Copy the example config:

```bash
cp application-local.yml.example application-local.yml
```

Fill in your MiniMax key:

```yaml
minimax:
  api-key: "YOUR_MINIMAX_API_KEY"
  model: "MiniMax-M3"
```

`application-local.yml` is ignored by Git, so your real key will not be committed. You can also override it with environment variables:

```bash
export MINIMAX_API_KEY="your-real-key"
export MINIMAX_MODEL="MiniMax-M3"
```

## Run A Chapter

Every chapter can be run independently. Replace `ch04` with the chapter you want:

```bash
mvn -q -pl ch04 compile exec:java \
  -Dexec.mainClass=com.bugwiki.harness.cmd.ClawApplication
```

Later chapters expose extra entry points:

```bash
# ch20+ benchmark runner
mvn -q -pl ch20 compile exec:java \
  -Dexec.mainClass=com.bugwiki.harness.cmd.BenchApplication

# ch21 CLI with task, workspace and session options
mvn -q -pl ch21 compile exec:java \
  -Dexec.mainClass=com.bugwiki.harness.cmd.ClawApplication \
  -Dexec.args='-prompt "阅读当前目录并总结项目结构" -dir . -session local_demo'

# ch22 AgentOps HTTP entry
mvn -q -pl ch22 compile exec:java \
  -Dexec.mainClass=com.bugwiki.harness.cmd.AgentOpsApplication
```

## Chapter Roadmap

| Chapter | Focus |
| --- | --- |
| `ch01` | Minimal CLI scaffold |
| `ch02` | Message, provider, registry and first agent loop |
| `ch03` | Thinking phase |
| `ch04` | MiniMax / OpenAI-compatible provider |
| `ch05` | `read_file` tool |
| `ch06` | `write_file` and `bash` tools |
| `ch07` | `edit_file` tool |
| `ch08` | Concurrent tool execution |
| `ch09` | Reporter and Feishu bot skeleton |
| `ch10` | Prompt composer and `AGENTS.md` loading |
| `ch11` | Session and working memory |
| `ch12` | Context compactor |
| `ch13` | Plan mode |
| `ch14` | Recovery manager |
| `ch15` | Reminder injector |
| `ch16` | Approval middleware |
| `ch17` | Subagent tool |
| `ch18` | Cost tracker |
| `ch19` | Trace export |
| `ch20` | Benchmark runner |
| `ch21` | Full CLI options |
| `ch22` | AgentOps HTTP entry |

## Project Layout

```text
.
├── pom.xml                         # Root Maven aggregator
├── application-local.yml.example   # Local MiniMax config template
├── ch01 ... ch22                   # One Maven module per tutorial chapter
│   ├── pom.xml
│   └── src/main/java/com/bugwiki/harness
└── README.md
```

## Notes

- This repository is designed for learning, so adjacent chapters intentionally repeat some code to make each chapter self-contained.
- Generated files such as `target/`, `.idea/`, local workspaces and `application-local.yml` are ignored.
- Chapters involving Feishu or AgentOps require the corresponding platform environment variables before running those integrations.
