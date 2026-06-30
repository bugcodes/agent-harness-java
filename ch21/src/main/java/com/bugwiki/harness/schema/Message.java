package com.bugwiki.harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 承载一条聊天消息，以及可选的工具调用、工具结果关联 ID 和 Token 用量。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class Message {
    private Role role;
    private String content;
    private List<ToolCall> toolCalls = new ArrayList<>();
    private String toolCallId;
    private Usage usage;

    public Message() {}

    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        Message message = new Message(Role.ASSISTANT, content);
        message.setToolCalls(toolCalls);
        return message;
    }

    public static Message toolResult(String toolCallId, String output) {
        Message message = new Message(Role.USER, output);
        message.setToolCallId(toolCallId);
        return message;
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

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }
}
