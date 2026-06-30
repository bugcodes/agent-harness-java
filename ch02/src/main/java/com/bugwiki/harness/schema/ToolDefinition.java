package com.bugwiki.harness.schema;

/**
 * Describes a tool that can be exposed to the model in chapter two.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ToolDefinition {
    private String name;
    private String description;
    private Object inputSchema;

    public ToolDefinition() {}

    public ToolDefinition(String name, String description, Object inputSchema) {
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

    public Object getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Object inputSchema) {
        this.inputSchema = inputSchema;
    }
}
