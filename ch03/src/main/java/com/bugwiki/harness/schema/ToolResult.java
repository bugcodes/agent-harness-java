package com.bugwiki.harness.schema;

/**
 * Contains the local execution result of one tool call.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ToolResult {
    private String toolCallId;
    private String output;
    private boolean error;

    public ToolResult() {}

    public ToolResult(String toolCallId, String output, boolean error) {
        this.toolCallId = toolCallId;
        this.output = output;
        this.error = error;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }
}
