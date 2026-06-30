package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.context.SessionManager;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.engine.TerminalReporter;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 启动 ch12 上下文压缩演示，使用 Session 与 Compactor 保护长日志场景。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        Path workDir = Path.of(".").toAbsolutePath().normalize();
        LlmProvider provider = createProvider();

        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new BashTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, false);
        TerminalReporter reporter = new TerminalReporter();

        String sessionId = "test_oom_protection_001";
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);
        session.append(Message.user(defaultPrompt()));

        engine.run(session, reporter);
    }

    private static String defaultPrompt() {
        return """
                请帮我执行以下三个步骤：
                1. 使用 bash 执行 echo "开始排查日志"
                2. 读取当前目录下的巨大文件 mock_log.txt
                3. 用 bash 执行 date 命令获取当前时间，并告诉我任务完成。
                """;
    }

    private static LlmProvider createProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，切换到本地可运行 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * 在没有真实 MiniMax key 时提供本地可运行的 ch12 演示响应。
     *
     * @author zhaobinjie
     * @date 2026-06-30
     */
    private static class DemoProvider implements LlmProvider {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
                throws Exception {
            if (availableTools == null || availableTools.isEmpty()) {
                return Message.assistant("", List.of());
            }
            if (hasToolObservation(messages)) {
                return Message.assistant("任务完成：已经 echo、读取大日志，并执行 date；上下文压缩保护已生效。", List.of());
            }
            return Message.assistant(
                    "我会并发执行 echo、读取巨大日志、再获取当前时间。",
                    List.of(
                            new ToolCall("call-echo", "bash", mapper.readTree("{\"command\":\"echo 开始排查日志\"}")),
                            new ToolCall("call-read-log", "read_file", mapper.readTree("{\"path\":\"mock_log.txt\"}")),
                            new ToolCall("call-date", "bash", mapper.readTree("{\"command\":\"date\"}"))));
        }

        private boolean hasToolObservation(List<Message> messages) {
            return messages.stream()
                    .anyMatch(
                            message ->
                                    message.getRole() == Role.USER
                                            && message.getToolCallId() != null
                                            && !message.getToolCallId().isBlank());
        }
    }
}
