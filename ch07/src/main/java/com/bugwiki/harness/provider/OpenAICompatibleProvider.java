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
 * Calls an OpenAI-compatible chat completion endpoint such as MiniMax.
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
            throw new IllegalArgumentException(
                    "Replace YOUR_MINIMAX_API_KEY before running a real provider.");
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
                for (ToolCall call : message.getToolCalls()) {
                    ObjectNode callNode = calls.addObject();
                    callNode.put("id", call.getId());
                    callNode.put("type", "function");
                    ObjectNode function = callNode.putObject("function");
                    function.put("name", call.getName());
                    function.put(
                            "arguments",
                            call.getArguments() == null ? "{}" : mapper.writeValueAsString(call.getArguments()));
                }
            }
        }

        if (availableTools != null && !availableTools.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolDefinition definition : availableTools) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", definition.getName());
                function.put("description", definition.getDescription());
                function.set("parameters", definition.getInputSchema());
            }
            body.put("tool_choice", "auto");
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
                    "Model API failed: " + response.statusCode() + " " + response.body());
        }
        return parseResponse(response.body());
    }

    private Message parseResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode first = root.path("choices").path(0).path("message");
        String content =
                first.path("content").isMissingNode() || first.path("content").isNull()
                        ? ""
                        : first.path("content").asText();
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode node : first.path("tool_calls")) {
            JsonNode function = node.path("function");
            String rawArguments = function.path("arguments").asText("{}");
            JsonNode arguments =
                    rawArguments.isBlank() ? mapper.createObjectNode() : mapper.readTree(rawArguments);
            calls.add(new ToolCall(node.path("id").asText(), function.path("name").asText(), arguments));
        }
        Message message = new Message(Role.ASSISTANT, content);
        message.setToolCalls(calls);
        return message;
    }

    private String roleFor(Message message) {
        if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
            return "tool";
        }
        Role role = message.getRole();
        return role == null ? "user" : role.value();
    }
}
