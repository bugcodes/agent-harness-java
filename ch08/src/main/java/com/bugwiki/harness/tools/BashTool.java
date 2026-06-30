package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Runs a shell command inside the configured workspace.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class BashTool implements BaseTool {
    private final Path workDir;
    private final Duration timeout;

    public BashTool(Path workDir) {
        this(workDir, Duration.ofSeconds(30));
    }

    public BashTool(Path workDir, Duration timeout) {
        this.workDir = workDir.toAbsolutePath().normalize();
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "command", "Shell command to run in the workspace.");
        schema.withArray("required").add("command");
        return new ToolDefinition(name(), "Run a shell command in the workspace.", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        String command = arguments.path("command").asText();
        Process process =
                new ProcessBuilder("/bin/bash", "-lc", command)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ToolResult(
                    toolCallId, "Command timed out after " + timeout.toSeconds() + " seconds", true);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean failed = process.exitValue() != 0;
        if (failed) {
            output = output + "\nexit code: " + process.exitValue();
        }
        return new ToolResult(toolCallId, output, failed);
    }
}
