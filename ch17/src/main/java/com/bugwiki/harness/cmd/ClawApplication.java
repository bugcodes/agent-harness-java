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
import com.bugwiki.harness.tools.EditFileTool;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.SubagentTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 启动 ch17 多智能体协同测试，演示主 Agent 委派只读 Subagent 探索遗留项目。
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
        TerminalReporter reporter = new TerminalReporter();

        ToolRegistry readOnlyRegistry = new ToolRegistry();
        readOnlyRegistry.register(new ReadFileTool(workDir));
        readOnlyRegistry.register(new BashTool(workDir));

        ToolRegistry mainRegistry = new ToolRegistry();
        mainRegistry.register(new ReadFileTool(workDir));
        mainRegistry.register(new WriteFileTool(workDir));
        mainRegistry.register(new BashTool(workDir));
        mainRegistry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(llmProvider, mainRegistry, false, false);
        mainRegistry.register(new SubagentTool(engine, readOnlyRegistry, reporter));

        String sessionId = "test_subagent_001";
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);
        String prompt =
                """
                我需要你在这个遗留项目里，找到那个“核心密码”。
                为了防止污染主上下文，请你务必派出子智能体（spawn_subagent）去执行探索任务。
                你可以让子智能体使用 bash 去查找当前目录（及其所有子目录）下名为 config.txt 的文件。
                子智能体拿到密码向你汇报后，请你亲自使用 write_file 工具，将密码写在根目录的 answer.txt 里。
                """;

        System.out.println("\n>>> 🚀 启动多智能体协同测试...");
        session.append(Message.user(prompt));
        engine.run(session, reporter);
    }
}
