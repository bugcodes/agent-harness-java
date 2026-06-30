package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts chapter five by registering the real read_file tool.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Path workDir = Path.of(".").toAbsolutePath().normalize();
        Files.writeString(workDir.resolve("hello.txt"), "hello from ch05\n");

        LlmProvider provider = newLlmProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, workDir.toString(), false);
        engine.run("请调用工具读取一下当前工作区目录下 hello.txt 文件的内容，并用一句话向我总结它说了什么。");
    }

    private static LlmProvider newLlmProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，使用本地 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * Allows the chapter to run locally while still leaving the MiniMax provider in place.
     *
     * @author zhaobinjie
     * @date 2026-06-24
     */
    private static class DemoProvider implements LlmProvider {
        private final ObjectMapper mapper = new ObjectMapper();
        private int turn;

        @Override
        public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
                throws Exception {
            turn++;
            if (turn == 1) {
                Message message = new Message(Role.ASSISTANT, "我会调用 read_file 读取 hello.txt。");
                message.setToolCalls(
                        List.of(
                                new ToolCall(
                                        "call_read_hello",
                                        "read_file",
                                        mapper.readTree("{\"path\":\"hello.txt\"}"))));
                return message;
            }
            return new Message(Role.ASSISTANT, "hello.txt 说：hello from ch05。");
        }
    }
}
