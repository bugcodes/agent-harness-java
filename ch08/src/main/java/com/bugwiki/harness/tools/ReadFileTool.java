package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a UTF-8 text file from the configured workspace.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ReadFileTool implements BaseTool {
    private final Path workDir;

    public ReadFileTool(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "path", "Path relative to the workspace.");
        schema.withArray("required").add("path");
        return new ToolDefinition(name(), "Read a UTF-8 text file from the workspace.", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        Path target = resolve(arguments.path("path").asText());
        return new ToolResult(toolCallId, Files.readString(target), false);
    }

    private Path resolve(String path) {
        Path target = workDir.resolve(path).normalize();
        if (!target.startsWith(workDir)) {
            throw new IllegalArgumentException("Path escapes workspace: " + path);
        }
        return target;
    }
}
