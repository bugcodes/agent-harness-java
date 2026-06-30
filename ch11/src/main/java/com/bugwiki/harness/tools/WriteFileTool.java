package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes UTF-8 content to a file inside the configured workspace.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class WriteFileTool implements BaseTool {
    private final Path workDir;

    public WriteFileTool(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "path", "Path relative to the workspace.");
        JsonSchemas.stringProperty(properties, "content", "File content to write.");
        schema.withArray("required").add("path").add("content");
        return new ToolDefinition(name(), "Write a UTF-8 text file in the workspace.", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        Path target = resolve(arguments.path("path").asText());
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, arguments.path("content").asText());
        return new ToolResult(toolCallId, "Wrote " + target, false);
    }

    private Path resolve(String path) {
        Path target = workDir.resolve(path).normalize();
        if (!target.startsWith(workDir)) {
            throw new IllegalArgumentException("Path escapes workspace: " + path);
        }
        return target;
    }
}
