package com.bugwiki.harness.engine;

/**
 * Receives visible lifecycle events from the agent engine.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public interface Reporter {
    void onThinking();

    void onToolCall(String toolName, String arguments);

    void onToolResult(String toolName, String result, boolean error);

    void onMessage(String content);
}
