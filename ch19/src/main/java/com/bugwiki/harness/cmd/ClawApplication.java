package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.context.SessionManager;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.engine.TerminalReporter;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 启动 ch19 链路追踪测试，观察同一轮中并发工具调用的执行回放。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        Path workDir = Path.of("").toAbsolutePath().normalize().resolve("workspace");
        LlmProvider llmProvider = new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());

        ToolRegistry registry = new ToolRegistry();
        registry.register(new BashTool(workDir));
        registry.register(new WriteFileTool(workDir));

        AgentEngine engine = new AgentEngine(llmProvider, registry, false, false);
        TerminalReporter reporter = new TerminalReporter();
        Session session = SessionManager.GLOBAL.getOrCreate("test_trace_001", workDir);

        String prompt =
                """
                为了加快执行速度，请你在一轮回复中，【同时并行】完成以下两件事：
                1. 使用 bash 工具执行 'sleep 2 && echo "系统环境检查完毕"'
                2. 使用 write_file 工具，在当前目录下创建一个 'trace_test.md'，内容写上 "测试并发的写入"。
                请确保你是分别调用两个不同的工具，不要试图把它们合并成一个命令！
                """;

        System.out.println("\n>>> 🚀 启动带 Tracing 链路追踪的测试...");
        session.append(Message.user(prompt));
        engine.run(session, reporter);
    }
}
