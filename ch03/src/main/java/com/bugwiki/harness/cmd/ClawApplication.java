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
 * Bootstraps chapter three with a mock provider that demonstrates thinking before action.
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
        AgentEngine engine = new AgentEngine(provider, registry, workDir, true);

        engine.run("帮我检查当前目录的文件");
    }

    /**
     * Returns the thinking trace first, then the two action turns used by chapter three.
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
            if (availableTools == null || availableTools.isEmpty()) {
                return new Message(
                        Role.ASSISTANT,
                        "【推理中】目标是检查文件。我不能直接盲猜，我需要先调用 bash 工具执行 ls 命令，"
                                + "看看当前目录下有什么，然后再做定夺。");
            }

            turn++;
            if (turn == 1) {
                Message message = new Message(Role.ASSISTANT, "我要执行我刚才计划的步骤了。");
                message.setToolCalls(
                        List.of(
                                new ToolCall("call_123", "bash", mapper.readTree("{\"command\":\"ls -la\"}"))));
                return message;
            }

            return new Message(Role.ASSISTANT, "根据工具返回的结果，我看到了 main.go，任务圆满完成！");
        }
    }

    /**
     * Exposes the mock bash tool definition and returns a fixed file listing.
     *
     * @author zhaobinjie
     * @date 2026-06-24
     */
    private static class MockRegistry implements Registry {
        @Override
        public List<ToolDefinition> getAvailableTools() {
            return List.of(new ToolDefinition("bash", "", null));
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return new ToolResult(
                    call.getId(), "-rw-r--r--  1 user group  234 Oct 24 10:00 main.go\n", false);
        }
    }
}
