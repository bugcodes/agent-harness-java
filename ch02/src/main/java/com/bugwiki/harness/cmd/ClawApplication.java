package com.bugwiki.harness.cmd;

import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.bugwiki.harness.tools.Registry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Bootstraps chapter two with the same mock provider and registry used by the Go tutorial.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ClawApplication {
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        String workDir = Path.of(".").toAbsolutePath().normalize().toString();
        LlmProvider provider = new MockProvider();
        Registry registry = new MockRegistry();
        AgentEngine engine = new AgentEngine(provider, registry, workDir);

        engine.run("帮我检查当前目录的文件");
    }

    /**
     * Returns the two deterministic assistant turns used to exercise the chapter two loop.
     *
     * @author zhaobinjie
     * @date 2026-06-24
     */
    private static class MockProvider implements LlmProvider {
        private final ObjectMapper mapper = new ObjectMapper();
        private int turn;

        @Override
        public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
                throws Exception {
            turn++;
            if (turn == 1) {
                Message message = new Message(Role.ASSISTANT, "让我来看看当前目录下有什么文件。");
                message.setToolCalls(
                        List.of(
                                new ToolCall("call_123", "bash", mapper.readTree("{\"command\": \"ls -la\"}"))));
                return message;
            }

            return new Message(Role.ASSISTANT, "我看到了文件列表，里面包含 main.go，任务完成！");
        }
    }

    /**
     * Returns the fixed mock bash result expected by the chapter two engine loop.
     *
     * @author zhaobinjie
     * @date 2026-06-24
     */
    private static class MockRegistry implements Registry {
        @Override
        public List<ToolDefinition> getAvailableTools() {
            return List.of();
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return new ToolResult(
                    call.getId(), "-rw-r--r--  1 user group  234 Oct 24 10:00 main.go\n", false);
        }
    }
}
