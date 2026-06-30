package com.bugwiki.harness.engine;

/**
 * Prints agent lifecycle events to the local terminal.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class TerminalReporter implements Reporter {
    @Override
    public void onThinking() {
        System.out.println("[Reporter] 模型正在慢思考 (Thinking)...");
    }

    @Override
    public void onToolCall(String toolName, String arguments) {
        System.out.println("[Reporter] 正在执行工具: " + toolName + " 参数: " + arguments);
    }

    @Override
    public void onToolResult(String toolName, String result, boolean error) {
        String preview = result == null ? "" : result;
        if (preview.length() > 200) {
            preview = preview.substring(0, 200) + "...";
        }
        if (error) {
            System.out.println("[Reporter] 工具报错: " + toolName + " " + preview);
        } else {
            System.out.println("[Reporter] 工具成功: " + toolName + " " + preview);
        }
    }

    @Override
    public void onMessage(String content) {
        if (content != null && !content.isBlank()) {
            System.out.println(content);
        }
    }
}
