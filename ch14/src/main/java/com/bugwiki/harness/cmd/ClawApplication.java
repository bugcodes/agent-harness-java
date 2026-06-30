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
 * 启动 ch14 自愈测试任务，用错误 old_text 触发 RecoveryManager 的救援提示。
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

        String sessionId = "test_recovery_001";
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);

        String prompt =
                """
                我当前目录下有一个 auth.go 文件。
                请修改 auth.go 中的 login 函数。
                请直接使用 edit_file 工具替换下面的代码块，将判断条件改为同时允许"admin"、"root"和"guest"三种用户登录：

                    // 鉴权入口函数
                    func login(user string) bool {
                        // 检查用户名
                        if user == "admin" {
                            return true
                        }
                        return false
                    }
                """;

        System.out.println();
        System.out.println(">>> 🚀 启动自愈测试任务...");

        session.append(Message.user(prompt));
        engine.run(session, reporter);
    }
}
