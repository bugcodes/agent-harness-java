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
        System.out.println("[思考中] 模型正在推理...");
    }

    @Override
    public void onToolCall(String toolName, String arguments) {
        System.out.println("[调用工具] " + toolName + " 参数: " + arguments);
    }

    @Override
    public void onToolResult(String toolName, String result, boolean error) {
        String preview = result == null ? "" : result;
        if (preview.length() > 200) {
            preview = preview.substring(0, 200) + "...";
        }
        if (error) {
            System.out.println("[执行失败] " + toolName + " " + preview);
        } else {
            System.out.println("[执行成功] " + toolName);
        }
    }

    @Override
    public void onMessage(String content) {
        if (content != null && !content.isBlank()) {
            System.out.println("Agent 回复: " + content);
        }
    }
}
