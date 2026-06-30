package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.engine.TerminalReporter;
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
 * Starts the ch10 prompt-composer harness with a local runnable demo.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Path workDir = defaultWorkDir();
        Files.createDirectories(workDir);
        prepareWorkspace(workDir);
        String prompt = defaultPrompt();

        LlmProvider provider = createProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));
        AgentEngine engine = new AgentEngine(provider, registry, workDir, true);

        engine.run(prompt, new TerminalReporter());
    }

    private static Path defaultWorkDir() {
        return Path.of("workspace").toAbsolutePath().normalize();
    }

    private static String defaultPrompt() {
        return "我需要在当前目录下新建一个 ping.java，提供一个简单的 ping 返回。";
    }

    private static void prepareWorkspace(Path workDir) throws Exception {
        Files.createDirectories(workDir);
        Files.writeString(
                workDir.resolve("AGENTS.md"), "所有 Java 示例必须小步创建，写完后说明文件路径。\n");
        Files.writeString(workDir.resolve("README.md"), "project_key=front_demo_secret\n");
        Path skillDir = workDir.resolve(".claw").resolve("skills").resolve("java-file-writer");
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                ---
                name: java-file-writer
                description: 当需要创建 Java 源文件时触发。
                ---
                创建 Java 文件时保持类名和文件意图一致，输出后说明相对路径。
                """);
    }

    private static LlmProvider createProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，切换到本地可运行 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * Provides a deterministic ch10 model response that proves system context was composed.
     *
     * @author zhaobinjie
     * @date 2026-06-25
     */
    private static class DemoProvider implements LlmProvider {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
                throws Exception {
            if (availableTools == null || availableTools.isEmpty()) {
                Message system = messages.get(0);
                boolean hasAgents = system.getContent().contains("项目专属指南");
                boolean hasSkill = system.getContent().contains("技能名称: java-file-writer");
                return Message.assistant(
                        "系统提示已注入 AGENTS.md=" + hasAgents + "，Skill=" + hasSkill + "。", List.of());
            }
            if (hasToolObservation(messages)) {
                return Message.assistant("ping.java 已创建；ch10 的动态上下文组装链路跑通。", List.of());
            }
            return Message.assistant(
                    "我会根据 AGENTS.md 和 Skill 要求，用 write_file 创建 ping.java。",
                    List.of(
                            new ToolCall(
                                    "call-write-ping",
                                    "write_file",
                                    mapper.readTree(
                                            """
                                            {
                                              "path": "ping.java",
                                              "content": "class Ping { String pong() { return \\"pong\\"; } }\\n"
                                            }
                                            """))));
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
