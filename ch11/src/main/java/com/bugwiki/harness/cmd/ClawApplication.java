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
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Starts the ch11 session isolation demo.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Path frontDir = defaultWorkDir("project_front");
        Path backDir = defaultWorkDir("project_back");
        prepareWorkspace(frontDir, "project_key=front_demo_secret\n");
        prepareWorkspace(backDir, "backend_name=orders\n");

        LlmProvider provider = createProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(frontDir));
        AgentEngine engine = new AgentEngine(provider, registry, false);
        TerminalReporter reporter = new TerminalReporter();
        CountDownLatch latch = new CountDownLatch(2);

        Thread sessionA =
                new Thread(
                        () -> {
                            try {
                                Session session =
                                        SessionManager.GLOBAL.getOrCreate("chat_front_001", frontDir);
                                System.out.println("\n>>> [Session A / Turn 1]: 帮我看看 README.md 里记录了什么密钥？");
                                session.append(Message.user("帮我看看 README.md 里记录了什么密钥？"));
                                engine.run(session, reporter);

                                for (int i = 0; i < 6; i++) {
                                    session.append(Message.user("这只是一句闲聊占位符。"));
                                    session.append(new Message(Role.ASSISTANT, "好的，收到闲聊。"));
                                }

                                System.out.println(
                                        "\n>>> [Session A / Turn 2]: 请直接告诉我，刚才第一轮你查到的那个密钥是什么？");
                                session.append(Message.user("请直接告诉我，刚才第一轮你查到的那个密钥是什么？不准调用工具！"));
                                engine.run(session, reporter);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            } finally {
                                latch.countDown();
                            }
                        });

        Thread sessionB =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(200);
                                Session session = SessionManager.GLOBAL.getOrCreate("chat_back_002", backDir);
                                System.out.println("\n>>> [Session B]: 别人查到了一个密钥，你这里能看到吗？");
                                session.append(Message.user("别人查到了一个密钥，你这里能看到吗？不准调用工具！"));
                                engine.run(session, reporter);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            } finally {
                                latch.countDown();
                            }
                        });

        sessionA.start();
        sessionB.start();
        latch.await();
    }

    private static Path defaultWorkDir(String name) {
        return Path.of("workspace").resolve(name).toAbsolutePath().normalize();
    }

    private static void prepareWorkspace(Path workDir, String readme) throws Exception {
        Files.createDirectories(workDir);
        Files.writeString(
                workDir.resolve("AGENTS.md"), "Use small steps. Inspect files before editing.\n");
        Files.writeString(workDir.resolve("README.md"), readme);
    }

    private static LlmProvider createProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，切换到本地可运行 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * Provides deterministic ch11 responses for the session-isolation scenario.
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
                return Message.assistant("", List.of());
            }
            String prompt = lastUserPrompt(messages);
            String observations = toolObservations(messages);
            if (!observations.isBlank()) {
                return Message.assistant("README.md 里记录的密钥是: front_demo_secret。", List.of());
            }
            if (prompt.contains("README.md")) {
                return Message.assistant(
                        "我会读取 README.md。",
                        List.of(new ToolCall("call-read-front-readme", "read_file", mapper.readTree("{\"path\":\"README.md\"}"))));
            }
            if (prompt.contains("刚才第一轮")) {
                return Message.assistant("短期工作记忆里已经没有第一轮工具结果了，我不能凭空回答。", List.of());
            }
            if (prompt.contains("别人查到了")) {
                return Message.assistant("这是另一个 Session，我看不到 Session A 的历史和密钥。", List.of());
            }
            return Message.assistant("当前 Session 没有可用上下文。", List.of());
        }

        private String lastUserPrompt(List<Message> messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                if (message.getRole() == Role.USER
                        && (message.getToolCallId() == null || message.getToolCallId().isBlank())) {
                    return message.getContent() == null ? "" : message.getContent();
                }
            }
            return "";
        }

        private String toolObservations(List<Message> messages) {
            StringBuilder builder = new StringBuilder();
            for (Message message : messages) {
                if (message.getRole() == Role.USER
                        && message.getToolCallId() != null
                        && !message.getToolCallId().isBlank()) {
                    builder.append(message.getContent()).append('\n');
                }
            }
            return builder.toString();
        }
    }
}
