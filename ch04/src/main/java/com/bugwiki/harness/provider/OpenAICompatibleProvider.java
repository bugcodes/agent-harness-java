package com.bugwiki.harness.provider;

import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls an OpenAI-compatible chat-completions endpoint such as MiniMax.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class OpenAICompatibleProvider implements LlmProvider {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public OpenAICompatibleProvider(String apiKey, String baseUrl, String model) {
        if (apiKey == null || apiKey.isBlank() || "YOUR_MINIMAX_API_KEY".equals(apiKey)) {
            throw new IllegalArgumentException("请在代码里替换 MiniMax API Key 占位符");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.model = model;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
            throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);

        ArrayNode messageArray = body.putArray("messages");
        for (Message message : messages) {
            ObjectNode item = messageArray.addObject();
            item.put("role", roleFor(message));
            item.put("content", message.getContent() == null ? "" : message.getContent());
            if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
                item.put("tool_call_id", message.getToolCallId());
            }
            if (!message.getToolCalls().isEmpty()) {
                ArrayNode calls = item.putArray("tool_calls");
                for (ToolCall toolCall : message.getToolCalls()) {
                    ObjectNode call = calls.addObject();
                    call.put("id", toolCall.getId());
                    call.put("type", "function");
                    ObjectNode function = call.putObject("function");
                    function.put("name", toolCall.getName());
                    function.put(
                            "arguments",
                            toolCall.getArguments() == null
                                    ? "{}"
                                    : mapper.writeValueAsString(toolCall.getArguments()));
                }
            }
        }

        if (availableTools != null && !availableTools.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolDefinition toolDefinition : availableTools) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", toolDefinition.getName());
                function.put("description", toolDefinition.getDescription());
                function.set("parameters", toolDefinition.getInputSchema());
            }
        }

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "chat/completions"))
                        .timeout(Duration.ofMinutes(2))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "OpenAI 兼容 API 请求失败: "
                            + response.statusCode()
                            + " "
                            + response.body());
        }

        return parseResponse(response.body());
    }

    private Message parseResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode choice = root.path("choices").path(0).path("message");
        Message result = new Message(Role.ASSISTANT, choice.path("content").asText(""));
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode node : choice.path("tool_calls")) {
            JsonNode function = node.path("function");
            calls.add(
                    new ToolCall(
                            node.path("id").asText(),
                            function.path("name").asText(),
                            mapper.readTree(function.path("arguments").asText("{}"))));
        }
        result.setToolCalls(calls);
        return result;
    }

    private String roleFor(Message message) {
        if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
            return "tool";
        }
        Role role = message.getRole();
        return role == null ? "user" : role.value();
    }
}
