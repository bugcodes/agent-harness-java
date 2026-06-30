package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a file from the configured workspace using the ch05 tutorial semantics.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class ReadFileTool implements BaseTool {
    private static final int MAX_LEN = 8000;

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
        // Definition 向大模型清晰地描述这个工具的用途和参数格式
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "要读取的文件路径，如 cmd/claw/main.go");
        schema.putArray("required").add("path");
        return new ToolDefinition(name(), "读取指定路径的文件内容。请提供相对工作区的路径。", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        Path fullPath = workDir.resolve(arguments.path("path").asText()).normalize();
        InputStream input;
        try {
            input = Files.newInputStream(fullPath);
        } catch (IOException ex) {
            throw new IOException("打开文件失败: " + ex.getMessage(), ex);
        }

        byte[] content;
        try (input) {
            content = input.readAllBytes();
        } catch (IOException ex) {
            throw new IOException("读取文件内容失败: " + ex.getMessage(), ex);
        }

        if (content.length > MAX_LEN) {
            String truncated = new String(content, 0, MAX_LEN, StandardCharsets.UTF_8);
            return new ToolResult(
                    toolCallId,
                    truncated + "\n\n...[由于内容过长，已被系统截断至前 " + MAX_LEN + " 字节]...",
                    false);
        }
        return new ToolResult(toolCallId, new String(content, StandardCharsets.UTF_8), false);
    }
}
