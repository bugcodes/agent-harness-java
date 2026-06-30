package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.engine.TerminalReporter;
import com.bugwiki.harness.feishu.FeishuBot;
import com.bugwiki.harness.feishu.FeishuEventServer;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.EditFileTool;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 启动 ch09 飞书事件服务，并把飞书消息接入 Agent 运行循环。
 *
 * @author zhaobinjie
 * @date 2026-06-26
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        Path workDir = Path.of(".").toAbsolutePath().normalize();
        AgentEngine engine = newEngine(workDir);

        if (Arrays.asList(args).contains("--demo")) {
            prepareDemoWorkspace(workDir);
            engine.run(defaultDemoPrompt(), new TerminalReporter());
            return;
        }

        FeishuBot bot = new FeishuBot(engine);
        FeishuEventServer server = new FeishuEventServer(bot.getEventDispatcher(), 48080);
        server.start();
    }

    private static AgentEngine newEngine(Path workDir) {
        LlmProvider provider = createProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));
        return new AgentEngine(provider, registry, workDir, true);
    }

    private static String defaultDemoPrompt() {
        return "启动 Reporter/Feishu 演示，本章重点是把 Agent 生命周期事件外发。";
    }

    private static void prepareDemoWorkspace(Path workDir) throws Exception {
        Files.createDirectories(workDir);
        Files.writeString(
                workDir.resolve("reporter-demo.txt"),
                "ch09: Reporter 会把 Thinking、工具调用、工具结果和最终消息推送到飞书。\n");
    }

    private static LlmProvider createProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，切换到本地可运行 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * 在没有真实 MiniMax key 时提供本地可运行的 ch09 演示响应。
     *
     * @author zhaobinjie
     * @date 2026-06-26
     */
    private static class DemoProvider implements LlmProvider {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
                throws Exception {
            if (availableTools == null || availableTools.isEmpty()) {
                return Message.assistant("我会先通过 Reporter 通知慢思考状态，然后读取 ch09 示例文件。", List.of());
            }
            if (hasToolObservation(messages)) {
                return Message.assistant("FeishuReporter 已收到工具结果，本次 ch09 演示结束。", List.of());
            }
            return Message.assistant(
                    "我会读取 reporter-demo.txt，并把工具调用过程通过 Reporter 外发。",
                    List.of(
                            new ToolCall(
                                    "call-read-demo",
                                    "read_file",
                                    mapper.readTree("{\"path\":\"reporter-demo.txt\"}"))));
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
