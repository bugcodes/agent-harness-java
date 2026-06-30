package com.bugwiki.harness.feishu;

import com.bugwiki.harness.engine.Reporter;

/**
 * Adapts agent events to a Feishu-style chat stream.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class FeishuReporter implements Reporter {
    private final String chatId;

    public FeishuReporter(String chatId) {
        this.chatId = chatId;
    }

    @Override
    public void onThinking() {
        send("thinking");
    }

    @Override
    public void onToolCall(String toolName, String arguments) {
        send("tool call: " + toolName + " " + arguments);
    }

    @Override
    public void onToolResult(String toolName, String result, boolean error) {
        send("tool result: " + toolName + " error=" + error);
    }

    @Override
    public void onMessage(String content) {
        send(content);
    }

    public void send(String text) {
        System.out.println("[feishu:" + chatId + "] " + text);
    }
}
