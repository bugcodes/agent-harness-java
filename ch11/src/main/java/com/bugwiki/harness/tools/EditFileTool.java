package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Applies a simple string replacement to a workspace file.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class EditFileTool implements BaseTool {
    private final Path workDir;

    public EditFileTool(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "path", "Path relative to the workspace.");
        JsonSchemas.stringProperty(properties, "old_string", "Text to replace.");
        JsonSchemas.stringProperty(properties, "new_string", "Replacement text.");
        schema.withArray("required").add("path").add("old_string").add("new_string");
        return new ToolDefinition(name(), "Replace text in a workspace file.", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        Path target = resolve(arguments.path("path").asText());
        String oldText = firstText(arguments, "old_string", "oldText", "search");
        String newText = firstText(arguments, "new_string", "newText", "replace");
        String content = Files.readString(target);
        if (!content.contains(oldText)) {
            return new ToolResult(toolCallId, "Target text was not found.", true);
        }
        Files.writeString(target, content.replace(oldText, newText));
        return new ToolResult(toolCallId, "Edited " + target, false);
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name)) {
                return node.path(name).asText();
            }
        }
        return "";
    }

    private Path resolve(String path) {
        Path target = workDir.resolve(path).normalize();
        if (!target.startsWith(workDir)) {
            throw new IllegalArgumentException("Path escapes workspace: " + path);
        }
        return target;
    }
}
