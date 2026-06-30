package com.bugwiki.harness.schema;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Defines a tool schema that can be exposed to an OpenAI-compatible model.
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ToolDefinition {
    private String name;
    private String description;
    private JsonNode inputSchema;

    public ToolDefinition() {}

    public ToolDefinition(String name, String description, JsonNode inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JsonNode getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonNode inputSchema) {
        this.inputSchema = inputSchema;
    }
}
