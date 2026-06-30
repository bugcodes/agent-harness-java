package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.context.SessionManager;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.engine.TerminalReporter;
import com.bugwiki.harness.observability.CostTracker;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.ToolRegistry;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 启动 ch18 可观测性测试，通过费用追踪器记录模型调用的 Token 与成本。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        Path workDir = Path.of("").toAbsolutePath().normalize();
        String modelName = CONFIG.getMinimaxModel();
        LlmProvider realProvider = new MiniMaxProvider(CONFIG.getMinimaxApiKey(), modelName);

        String sessionId = "test_observability_001";
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);
        LlmProvider trackedProvider = new CostTracker(realProvider, modelName, session);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new BashTool(workDir));

        AgentEngine engine = new AgentEngine(trackedProvider, registry, false, false);
        TerminalReporter reporter = new TerminalReporter();

        String prompt = "请用 bash 帮我用 date 命令查一下现在的时间。";

        System.out.println("\n>>> 🚀 启动带仪表盘的可观测性测试...");
        session.append(Message.user(prompt));
        engine.run(session, reporter);

        System.out.println("\n================ 财务报表 ================");
        System.out.println("会话 ID: " + session.getId());
        System.out.println("总消耗 Input Tokens: " + session.getTotalPromptTokens());
        System.out.println("总消耗 Output Tokens: " + session.getTotalCompletionTokens());
        System.out.printf("总计费用 (CNY): ¥%.6f%n", session.getTotalCostCny());
        System.out.println("==========================================");
    }
}
