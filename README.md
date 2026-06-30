# harness-from-scratch-java

        Java 17 edition of the `build-agent-harness-from-scratch` tutorial.
        Every chapter is an independent Maven module so the learning path can
        be opened and run chapter by chapter in IDEA.

        Package root: `com.bugwiki.harness`

        ## Chapters

        | Module | Focus |
        | --- | --- |
| ch01 | Minimal CLI scaffold |
| ch02 | Message, provider, registry, and first agent loop |
| ch03 | Thinking phase |
| ch04 | MiniMax/OpenAI-compatible provider |
| ch05 | read_file tool |
| ch06 | write_file and bash tools |
| ch07 | edit_file tool |
| ch08 | concurrent tool execution |
| ch09 | reporter and Feishu bot skeleton |
| ch10 | prompt composer and AGENTS.md loading |
| ch11 | session and working memory |
| ch12 | context compactor |
| ch13 | plan mode |
| ch14 | recovery manager |
| ch15 | reminder injector |
| ch16 | approval middleware |
| ch17 | subagent tool |
| ch18 | cost tracker |
| ch19 | trace export |
| ch20 | benchmark runner |
| ch21 | full CLI options |
| ch22 | AgentOps HTTP entry |

        ## Build

        The local machine has several JDKs. Use JDK 17 explicitly when the
        shell default points to Java 8:

        ```bash
        export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
        mvn test
        ```

        ## MiniMax Key

        Chapters that call the model contain this placeholder:

        ```java
        private static final String MINIMAX_API_KEY = "YOUR_MINIMAX_API_KEY";
        ```

        Replace it in the chapter entry class before running a real LLM call.
