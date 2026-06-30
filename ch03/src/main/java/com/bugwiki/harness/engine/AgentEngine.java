package com.bugwiki.harness.engine;

import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.bugwiki.harness.tools.Registry;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs chapter three's two-phase agent loop with an optional thinking phase.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class AgentEngine {
    private final LlmProvider provider;
    private final Registry registry;
    private final String workDir;
    private final boolean enableThinking;

    public AgentEngine(
            LlmProvider provider, Registry registry, String workDir, boolean enableThinking) {
        this.provider = provider;
        this.registry = registry;
        this.workDir = workDir;
        this.enableThinking = enableThinking;
    }

    public void run(String userPrompt) throws Exception {
        log("[Engine] 引擎启动，锁定工作区: " + workDir);
        log("[Engine] 慢思考模式 (Thinking Phase): " + enableThinking);

        List<Message> contextHistory = new ArrayList<>();
        contextHistory.add(
                new Message(
                        Role.SYSTEM,
                        "You are go-tiny-claw, an expert coding assistant. "
                                + "You have full access to tools in the workspace."));
        contextHistory.add(new Message(Role.USER, userPrompt));

        int turnCount = 0;
        while (true) {
            turnCount++;
            log("");
            log("========== [Turn " + turnCount + "] 开始 ==========");

            List<ToolDefinition> availableTools = registry.getAvailableTools();

            if (enableThinking) {
                log("[Engine][Phase 1] 剥夺工具访问权，强制进入慢思考与规划阶段...");
                Message thinkResponse = provider.generate(contextHistory, null);
                // 如果模型输出了思考过程，我们将其作为 Assistant 消息追加到上下文中
                if (thinkResponse.getContent() != null && !thinkResponse.getContent().isEmpty()) {
                    System.out.println("🧠 [内部思考 Trace]: " + thinkResponse.getContent());
                    contextHistory.add(thinkResponse);
                }
            }
            // 此时的 contextHistory 中已经包含了上一阶段模型自己的 Thinking Trace。
            // 模型会顺着自己的逻辑，结合恢复的 availableTools 发起精准的工具调用。
            log("[Engine][Phase 2] 恢复工具挂载，等待模型采取行动...");
            Message actionResponse = provider.generate(contextHistory, availableTools);
            contextHistory.add(actionResponse);

            if (actionResponse.getContent() != null && !actionResponse.getContent().isEmpty()) {
                System.out.println("🤖 [对外回复]: " + actionResponse.getContent());
            }

            if (actionResponse.getToolCalls().isEmpty()) {
                log("[Engine] 模型未请求调用工具，任务宣告完成。");
                break;
            }

            log("[Engine] 模型请求调用 " + actionResponse.getToolCalls().size() + " 个工具...");

            for (ToolCall toolCall : actionResponse.getToolCalls()) {
                log("  -> 🛠️ 执行工具: " + toolCall.getName() + ", 参数: " + toolCall.getArguments());

                ToolResult result = registry.execute(toolCall);
                if (result.isError()) {
                    log("  -> ❌ 工具执行报错: " + result.getOutput());
                } else {
                    log("  -> ✅ 工具执行成功 (返回 " + result.getOutput().length() + " 字节)");
                }

                Message observationMessage = new Message(Role.USER, result.getOutput());
                observationMessage.setToolCallId(toolCall.getId());
                contextHistory.add(observationMessage);
            }
        }
    }

    private void log(String message) {
        System.out.println(message);
    }
}
