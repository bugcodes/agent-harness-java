package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.context.SessionManager;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.engine.TerminalReporter;
import com.bugwiki.harness.observability.CostTracker;
import com.bugwiki.harness.observability.TraceSpan;
import com.bugwiki.harness.observability.Tracer;
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
import java.time.Duration;
import java.time.Instant;

/**
 * 启动 CLI 引擎，支持传入任务、工作区和会话 ID 执行真实 Agent 任务。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();
    private static final String DEFAULT_SESSION_ID = "cli_default_session";

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        String prompt = "";
        Path workDirInput = Path.of(".");
        String sessionId = DEFAULT_SESSION_ID;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-prompt".equals(arg)) {
                prompt = nextValue(args, ++i, arg);
            } else if ("-dir".equals(arg)) {
                workDirInput = Path.of(nextValue(args, ++i, arg));
            } else if ("-session".equals(arg)) {
                sessionId = nextValue(args, ++i, arg);
            } else if ("-h".equals(arg) || "--help".equals(arg)) {
                printUsage();
                return;
            } else {
                System.err.println("未知参数: " + arg);
                printUsage();
                System.exit(2);
                return;
            }
        }

        if (prompt.isBlank()) {
            printUsage();
            System.exit(1);
            return;
        }

        Path workDir = workDirInput.toAbsolutePath().normalize();

        System.out.println("==================================================");
        System.out.println("🚀 启动 go-tiny-claw CLI 引擎...");
        System.out.println("📁 锁定工作区: " + workDir);
        System.out.println("==================================================");

        LlmProvider realProvider = new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);
        LlmProvider trackedProvider = new CostTracker(realProvider, CONFIG.getMinimaxModel(), session);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(trackedProvider, registry, false, true);
        TraceSpan rootSpan = Tracer.startSpan("CLI.TaskRun");
        rootSpan.addAttribute("Prompt", prompt);
        TerminalReporter reporter = new TerminalReporter();
        Instant start = Instant.now();

        try {
            System.out.println();
            System.out.println("🎯 收到任务: " + prompt);
            System.out.println();

            session.append(Message.user(prompt));
            engine.run(session, reporter, rootSpan);
        } catch (Exception ex) {
            throw new IllegalStateException("\n💥 引擎运行崩溃: " + ex.getMessage(), ex);
        } finally {
            rootSpan.endSpan();
            Tracer.exportTraceToFile(rootSpan, workDir, session.getId());
        }

        System.out.println();
        System.out.println("==================================================");
        System.out.println("✨ 任务圆满结束。总耗时: " + Duration.between(start, Instant.now()));
        System.out.printf(
                "💰 Session 累计消耗: $%.6f | Token: Input %d, Output %d%n",
                session.getTotalCostCny(),
                session.getTotalPromptTokens(),
                session.getTotalCompletionTokens());
        System.out.println("==================================================");
    }

    private static String nextValue(String[] args, int index, String flagName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("参数 " + flagName + " 缺少值");
        }
        return args[index];
    }

    private static void printUsage() {
        System.out.println(
                "用法: java com.bugwiki.harness.cmd.ClawApplication -prompt \"你的任务描述\" [-dir /path/to/workdir] [-session session_id]");
    }
}
