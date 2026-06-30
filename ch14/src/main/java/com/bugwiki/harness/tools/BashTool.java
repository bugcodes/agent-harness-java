package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 在当前工作区执行 bash 命令，并返回合并后的标准输出与标准错误。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class BashTool implements BaseTool {
    private static final long TIMEOUT_SECONDS = 30;
    private static final int MAX_BYTES = 8000;

    private final Path workDir;

    public BashTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "command", "要执行的 bash 命令");
        schema.withArray("required").add("command");
        return new ToolDefinition(
                name(), "在当前工作区执行任意的 bash 命令。支持链式命令(如 &&)。返回标准输出(stdout)和标准错误(stderr)。", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        String command = arguments.path("command").asText();
        Process process =
                new ProcessBuilder("bash", "-c", command)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();
        CompletableFuture<byte[]> outputFuture =
                CompletableFuture.supplyAsync(() -> readOutput(process));
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
            String output = outputString(awaitOutput(outputFuture));
            return new ToolResult(
                    toolCallId,
                    output + "\n[警告: 命令执行超时(30s)，已被系统强制终止。]",
                    false);
        }

        byte[] outputBytes = awaitOutput(outputFuture);
        String output = outputString(outputBytes);
        if (process.exitValue() != 0) {
            return new ToolResult(
                    toolCallId,
                    "执行报错: exit status " + process.exitValue() + "\n输出:\n" + output,
                    false);
        }
        if (output.isEmpty()) {
            return new ToolResult(toolCallId, "命令执行成功，无终端输出。", false);
        }
        if (outputBytes.length > MAX_BYTES) {
            return new ToolResult(
                    toolCallId,
                    outputString(outputBytes, MAX_BYTES)
                            + "\n\n...[终端输出过长，已截断至前 "
                            + MAX_BYTES
                            + " 字节]...",
                    false);
        }
        return new ToolResult(toolCallId, output, false);
    }

    private byte[] readOutput(Process process) {
        try {
            return process.getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] awaitOutput(CompletableFuture<byte[]> outputFuture)
            throws ExecutionException, InterruptedException {
        return outputFuture.get();
    }

    private String outputString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String outputString(byte[] bytes, int maxLength) {
        return new String(bytes, 0, Math.min(bytes.length, maxLength), StandardCharsets.UTF_8);
    }
}
