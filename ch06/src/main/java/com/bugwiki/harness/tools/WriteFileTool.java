package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 创建或覆盖写入工作区文件，必要时自动创建父目录。
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class WriteFileTool implements BaseTool {
    private final Path workDir;

    public WriteFileTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "path", "要写入的文件路径，如 src/main.go");
        JsonSchemas.stringProperty(properties, "content", "要写入的完整文件内容");
        schema.withArray("required").add("path").add("content");
        return new ToolDefinition(
                name(), "创建或覆盖写入一个文件。如果目录不存在会自动创建。请提供相对于工作区的相对路径。", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        String inputPath = arguments.path("path").asText();
        Path fullPath = workDir.resolve(inputPath).normalize();
        try {
            Path parent = fullPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ex) {
            throw new IOException("创建父目录失败: " + ex.getMessage(), ex);
        }
        try {
            Files.writeString(fullPath, arguments.path("content").asText());
        } catch (IOException ex) {
            throw new IOException("写入文件失败: " + ex.getMessage(), ex);
        }
        return new ToolResult(toolCallId, "成功将内容写入到文件: " + inputPath, false);
    }
}
