package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts chapter six by registering read_file, write_file, and bash tools.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Path workDir = Path.of(".").toAbsolutePath().normalize();
        LlmProvider provider = newLlmProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, workDir, false);
        engine.run(
                """
                请帮我执行以下操作：
                1. 用 bash 查看一下我当前电脑的 Java 版本。
                2. 帮我写一个简单的 HelloWorld.java 文件，输出 "Hello, bugcodes!"。
                3. 用 bash 编译并运行这个 java 文件，确认它能正常工作。
                """);
    }

    private static LlmProvider newLlmProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，使用本地 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * Drives a local deterministic demo for the write_file and bash tool flow.
     *
     * @author zhaobinjie
     * @date 2026-06-25
     */
    private static class DemoProvider implements LlmProvider {
        private final ObjectMapper mapper = new ObjectMapper();
        private int turn;

        @Override
        public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
                throws Exception {
            turn++;
            if (turn == 1) {
                Message message = new Message(Role.ASSISTANT, "我会先查看 Java 版本，并写入 HelloWorld.java。");
                message.setToolCalls(
                        List.of(
                                new ToolCall("call_java_version", "bash", mapper.readTree("{\"command\":\"java -version\"}")),
                                new ToolCall(
                                        "call_write_hello",
                                        "write_file",
                                        mapper.readTree(
                                                "{\"path\":\"HelloWorld.java\",\"content\":\"public class HelloWorld { public static void main(String[] args) { System.out.println(\\\"Hello, go-tiny-claw!\\\"); } }\\n\"}"))));
                return message;
            }
            if (turn == 2) {
                Message message = new Message(Role.ASSISTANT, "文件已写入，我现在编译并运行它。");
                message.setToolCalls(
                        List.of(
                                new ToolCall(
                                        "call_run_hello",
                                        "bash",
                                        mapper.readTree("{\"command\":\"javac HelloWorld.java && java HelloWorld\"}"))));
                return message;
            }
            return new Message(Role.ASSISTANT, "HelloWorld.java 已经成功编译并运行。");
        }
    }
}
