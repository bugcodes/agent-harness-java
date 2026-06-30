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
        System.out.println("\n[思考中] 模型正在推理...");
    }

    @Override
    public void onToolCall(String toolName, String arguments) {
        // 截断过长的参数显示，保持终端清爽
        String displayArgs = arguments == null ? "" : arguments.replace("\n", "\\n").replace("\r", "\\r");
        if (displayArgs.length() > 150) {
            displayArgs = displayArgs.substring(0, 150) + "... (已截断)";
        }
        System.out.println("[调用工具] " + toolName);
        System.out.println("   参数: " + displayArgs);
    }

    @Override
    public void onToolResult(String toolName, String result, boolean error) {
        if (error) {
            System.out.println("[执行失败] " + toolName);
            if (result != null && !result.isBlank()) {
                System.out.println("   错误: " + result);
            }
        } else {
            System.out.println("[执行成功] " + toolName);
        }
    }

    @Override
    public void onMessage(String content) {
        if (content != null && !content.isBlank()) {
            System.out.println("\nAgent 回复:\n" + content + "\n");
        }
    }
}
