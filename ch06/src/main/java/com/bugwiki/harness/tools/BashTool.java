package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 在当前工作区执行 bash 命令，并返回合并后的标准输出与标准错误。
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class BashTool implements BaseTool {
    private static final long TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 8000;

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
        // 【驾驭底线 2】：绑定执行的工作区目录
        // 确保命令默认在用户指定的 WorkDir 下执行，而不是引擎启动时的绝对路径。
        Process process =
                new ProcessBuilder("bash", "-c", command)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();
        CompletableFuture<String> outputFuture =
                CompletableFuture.supplyAsync(() -> readOutput(process));
        // 【驾驭底线 1】：Time Budgeting (时间预算与超时控制)
        // 给予 bash 命令一个最大执行时间，防止大模型卡死进程 (比如运行了 top 或持续监听的 Web 服务)
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
            String output = awaitOutput(outputFuture);
            return new ToolResult(
                    toolCallId,
                    output + "\n[警告: 命令执行超时(30s)，已被系统强制终止。]",
                    false);
        }
        String output = awaitOutput(outputFuture);
        if (process.exitValue() != 0) {
            // 【驾驭底线 3】：错误原样回传 (Self-Correction 自愈机制)
            // 当 bash 报错时（err != nil），我们绝对不能返回 Go 的 error 阻断程序！
            // 我们必须把 err 和 outputStr 拼接成字符串返回，利用大模型的自纠错能力自己分析报错！
            return new ToolResult(
                    toolCallId,
                    "执行报错: exit status " + process.exitValue() + "\n输出:\n" + output,
                    false);
        }
        if (output.isEmpty()) {
            return new ToolResult(toolCallId, "命令执行成功，无终端输出。", false);
        }
        byte[] outputBytes = output.getBytes(StandardCharsets.UTF_8);
        // 【驾驭底线 4】：长度截断保护 (防 OOM)
        if (outputBytes.length > MAX_OUTPUT_LENGTH) {
            return new ToolResult(
                    toolCallId,
                    firstUtf8Bytes(outputBytes, MAX_OUTPUT_LENGTH)
                            + "\n\n...[终端输出过长，已截断至前 "
                            + MAX_OUTPUT_LENGTH
                            + " 字节]...",
                    false);
        }
        return new ToolResult(toolCallId, output, false);
    }

    private String readOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String awaitOutput(CompletableFuture<String> outputFuture)
            throws ExecutionException, InterruptedException {
        return outputFuture.get();
    }

    private String firstUtf8Bytes(byte[] bytes, int maxLength) {
        CharsetDecoder decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.IGNORE)
                        .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes, 0, maxLength)).toString();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
