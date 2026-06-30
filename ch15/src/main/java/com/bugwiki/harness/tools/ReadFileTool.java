package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取工作区内指定文件内容，并对超长内容执行前置截断。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ReadFileTool implements BaseTool {
    private static final int MAX_BYTES = 8000;

    private final Path workDir;

    public ReadFileTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "path", "要读取的文件路径，如 cmd/claw/main.go");
        schema.withArray("required").add("path");
        return new ToolDefinition(name(), "读取指定路径的文件内容。请提供相对工作区的路径。", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        String inputPath = arguments.path("path").asText();
        Path fullPath = joinWorkDir(inputPath);
        byte[] content;
        try {
            content = Files.readAllBytes(fullPath);
        } catch (IOException ex) {
            throw new IOException("打开文件失败: " + ex.getMessage(), ex);
        }

        if (content.length > MAX_BYTES) {
            return new ToolResult(
                    toolCallId,
                    firstUtf8Bytes(content, MAX_BYTES)
                            + "\n\n...[由于内容过长，已被系统截断至前 "
                            + MAX_BYTES
                            + " 字节]...",
                    false);
        }
        return new ToolResult(toolCallId, new String(content, StandardCharsets.UTF_8), false);
    }

    private Path joinWorkDir(String inputPath) {
        String path = inputPath == null ? "" : inputPath;
        while (path.startsWith("/") || path.startsWith("\\")) {
            path = path.substring(1);
        }
        return workDir.resolve(path).normalize();
    }

    private String firstUtf8Bytes(byte[] bytes, int maxLength) {
        return new String(bytes, 0, Math.min(bytes.length, maxLength), StandardCharsets.UTF_8);
    }
}
