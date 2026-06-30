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
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 启动 ch13 计划模式，通过 prompt 参数唤醒可断点续传的 Agent。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        String prompt = promptArg(args);
        if (prompt.isBlank()) {
            System.out.println("用法: java com.bugwiki.harness.cmd.ClawApplication -prompt \"你的任务指令\"");
            System.exit(1);
        }

        Path workDir = Path.of("").toAbsolutePath().normalize().resolve("workspace");
        LlmProvider provider = createProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, false, true);
        TerminalReporter reporter = new TerminalReporter();

        String sessionId = "task_web_server_01";
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);

        System.out.println();
        System.out.println(">>> 🚀 收到指令: " + prompt);

        session.append(Message.user(prompt));
        engine.run(session, reporter);
    }

    private static String promptArg(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-prompt".equals(arg) && i + 1 < args.length) {
                return args[i + 1];
            }
            if (arg.startsWith("-prompt=")) {
                return arg.substring("-prompt=".length());
            }
        }
        return "";
    }

    private static LlmProvider createProvider() {
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }
}
