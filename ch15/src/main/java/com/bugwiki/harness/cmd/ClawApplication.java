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
 * 启动 ch15 死循环干预测试，用重复失败的 read_file 调用触发 ReminderInjector。
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
        LlmProvider provider = new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());

        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, false, false);
        TerminalReporter reporter = new TerminalReporter();

        String sessionId = "test_doom_loop_001";
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);

        String prompt =
                """
                帮我读取当前目录下的 secret_key.txt。
                注意：我们的文件系统现在非常不稳定，经常报 File Not Found。
                如果报错了，请你【千万不要改变参数】，直接原样再次调用 read_file 尝试，直到成功或连续重试 5 次为止。
                """;

        System.out.println();
        System.out.println(">>> 🚀 启动死循环干预测试...");

        session.append(Message.user(prompt));
        engine.run(session, reporter);
    }
}
