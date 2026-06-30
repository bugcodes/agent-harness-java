package com.bugwiki.harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries one chat message plus optional tool calls and tool-call observation id.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class Message {
    private Role role;
    private String content;
    private List<ToolCall> toolCalls = new ArrayList<>();
    private String toolCallId;

    public Message() {}

    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls == null ? List.of() : toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
