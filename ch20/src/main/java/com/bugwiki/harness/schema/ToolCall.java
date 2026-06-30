package com.bugwiki.harness.schema;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Describes a function call requested by the language model.
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ToolCall {
    private String id;
    private String name;
    private JsonNode arguments;

    public ToolCall() {}

    public ToolCall(String id, String name, JsonNode arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JsonNode getArguments() {
        return arguments;
    }

    public void setArguments(JsonNode arguments) {
        this.arguments = arguments;
    }
}
