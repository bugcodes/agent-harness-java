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
import com.bugwiki.harness.tools.EditFileTool;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts chapter eight by exercising concurrent tool execution.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Path workDir = Path.of(".").toAbsolutePath().normalize();
        prepareWorkspace(workDir);

        LlmProvider provider = newLlmProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, workDir, true);
        engine.run(
                """
                我当前目录下有 a.txt, b.txt, c.txt 三个文件。(如果没有请忽略找不到的报错)
                为了节省时间，请你同时一次性利用工具读取这三个文件，并将它们的内容综合起来告诉我。
                """);
    }

    private static void prepareWorkspace(Path workDir) throws Exception {
        Files.writeString(workDir.resolve("a.txt"), "a\n");
        Files.writeString(workDir.resolve("b.txt"), "b\n");
        Files.writeString(workDir.resolve("c.txt"), "c\n");
    }

    private static LlmProvider newLlmProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，使用本地 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * Drives a deterministic local demo that emits three tool calls in one turn.
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
            if (availableTools == null || availableTools.isEmpty()) {
                return new Message(Role.ASSISTANT, "我会一次性规划读取 a.txt、b.txt、c.txt。");
            }
            turn++;
            if (turn == 1) {
                Message message = new Message(Role.ASSISTANT, "我会并发读取三个文件。");
                message.setToolCalls(
                        List.of(
                                new ToolCall("call_read_a", "read_file", mapper.readTree("{\"path\":\"a.txt\"}")),
                                new ToolCall("call_read_b", "read_file", mapper.readTree("{\"path\":\"b.txt\"}")),
                                new ToolCall("call_read_c", "read_file", mapper.readTree("{\"path\":\"c.txt\"}"))));
                return message;
            }
            return new Message(Role.ASSISTANT, "三个文件内容合并后是：a、b、c。");
        }
    }
}
