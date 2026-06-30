package com.bugwiki.harness.engine;

/**
 * 将 Agent 生命周期事件打印到本地终端。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class TerminalReporter implements Reporter {
    @Override
    public void onThinking() {
        System.out.println();
        System.out.println("[🤔 思考中] 模型正在推理...");
    }

    @Override
    public void onToolCall(String toolName, String arguments) {
        System.out.println("[🛠️ 调用工具] " + toolName);
        String displayArgs =
                arguments == null ? "" : arguments.replace("\n", "\\n").replace("\r", "\\r");
        if (displayArgs.length() > 150) {
            displayArgs = displayArgs.substring(0, 150) + "... (已截断)";
        }
        System.out.println("   参数: " + displayArgs);
    }

    @Override
    public void onToolResult(String toolName, String result, boolean error) {
        String preview = result == null ? "" : result;
        if (preview.length() > 200) {
            preview = preview.substring(0, 200) + "...";
        }
        if (error) {
            System.out.println("[❌ 执行失败] " + toolName);
            if (!preview.isEmpty()) {
                System.out.println("   错误: " + preview);
            }
        } else {
            System.out.println("[✅ 执行成功] " + toolName);
        }
    }

    @Override
    public void onMessage(String content) {
        if (content != null && !content.isBlank()) {
            System.out.println();
            System.out.println("🤖 Agent 回复:");
            System.out.println(content);
            System.out.println();
        }
    }
}
