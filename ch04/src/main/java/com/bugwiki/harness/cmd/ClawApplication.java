package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.bugwiki.harness.tools.Registry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts chapter four with a MiniMax OpenAI-compatible provider and mock weather tool.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        String workDir = Path.of(".").toAbsolutePath().normalize().toString();
        LlmProvider provider = newLlmProvider();
        Registry registry = new MockRegistry();
        AgentEngine engine = new AgentEngine(provider, registry, workDir, true);

        engine.run("我想去北京跑步，帮我查查天气适合吗？");
    }

    private static LlmProvider newLlmProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，使用本地 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * Exposes the weather function schema and returns a deterministic weather observation.
     *
     * @author zhaobinjie
     * @date 2026-06-24
     */
    private static class MockRegistry implements Registry {
        @Override
        public List<ToolDefinition> getAvailableTools() {
            ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ObjectNode city = properties.putObject("city");
            city.put("type", "string");
            schema.putArray("required").add("city");
            return List.of(new ToolDefinition("get_weather", "获取指定城市的当前天气情况。", schema));
        }

        @Override
        public ToolResult execute(ToolCall call) {
            System.out.println("  -> [Mock 工具执行] 获取 " + call.getName() + " 的天气中...");
            return new ToolResult(call.getId(), "API 返回：今天是晴天，气温 25 度。", false);
        }
    }

    /**
     * Allows the chapter to run in IDEA before the MiniMax placeholder is replaced.
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
                Message message = new Message(Role.ASSISTANT, "我先调用天气工具确认北京当前天气。");
                message.setToolCalls(
                        List.of(
                                new ToolCall(
                                        "call_weather",
                                        "get_weather",
                                        mapper.readTree("{\"city\":\"北京\"}"))));
                return message;
            }
            return new Message(Role.ASSISTANT, "北京今天晴天、25 度，适合轻松跑步。");
        }
    }
}
