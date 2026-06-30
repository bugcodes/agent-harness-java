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
 * Runs the first tutorial agent loop by alternating model responses and tool observations.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class AgentEngine {
    private final LlmProvider provider;
    private final Registry registry;
    private final String workDir;

    public AgentEngine(LlmProvider provider, Registry registry, String workDir) {
        this.provider = provider;
        this.registry = registry;
        this.workDir = workDir;
    }

    public void run(String userPrompt) throws Exception {
        log("[Engine] 引擎启动，锁定工作区: " + workDir);

        // 1. 初始化会话的 Context (上下文内存)
        // 在真实的场景中，这里会由动态 Prompt 组装器加载 AGENTS.md。目前我们先硬编码。
        List<Message> contextHistory = new ArrayList<>();
        contextHistory.add(
                new Message(
                        Role.SYSTEM,
                        "You are go-tiny-claw, an expert coding assistant. "
                                + "You have full access to tools in the workspace."));
        contextHistory.add(new Message(Role.USER, userPrompt));

        int turnCount = 0;
        // 2. The Main Loop: 心跳开始 (标准的 ReAct 循环)
        while (true) {
            turnCount++;
            log("========== [Turn " + turnCount + "] 开始 ==========");
            // 获取当前挂载的所有工具定义
            List<ToolDefinition> availableTools = registry.getAvailableTools();
            // 向大模型发起推理请求 (包含 Reasoning)
            log("[Engine] 正在思考 (Reasoning)...");
            Message responseMessage = provider.generate(contextHistory, availableTools);
            // 将模型的响应完整追加到上下文历史中
            contextHistory.add(responseMessage);

            // 如果模型回复了纯文本，打印出来 (这通常是它的思考过程，或是最终结果)
            if (responseMessage.getContent() != null && !responseMessage.getContent().isEmpty()) {
                System.out.println("🤖 模型: " + responseMessage.getContent());
            }

            // 3. 退出条件判断
            // 如果模型没有请求任何工具调用，说明它认为任务已经完成，跳出循环。
            if (responseMessage.getToolCalls().isEmpty()) {
                log("[Engine] 任务完成，退出循环。");
                break;
            }
            // 4. 执行行动 (Action) 与 获取观察结果 (Observation)
            log("[Engine] 模型请求调用 " + responseMessage.getToolCalls().size() + " 个工具...");

            for (ToolCall toolCall : responseMessage.getToolCalls()) {
                log("  -> 🛠️ 执行工具: " + toolCall.getName() + ", 参数: " + toolCall.getArguments());

                ToolResult result = registry.execute(toolCall);
                if (result.isError()) {
                    log("  -> ❌ 工具执行报错: " + result.getOutput());
                } else {
                    log("  -> ✅ 工具执行成功 (返回 " + result.getOutput().length() + " 字节)");
                }
                // 将工具执行的观察结果 (Observation) 封装为 User Message 追加到上下文中
                // 注意：ToolCallID 必须携带！这是维系大模型推理链条的关键
                Message observationMessage = new Message(Role.USER, result.getOutput());
                observationMessage.setToolCallId(toolCall.getId());
                contextHistory.add(observationMessage);
            }
            // 循环回到开头，模型将带着新加入的 Observation 继续它的下一轮思考...
        }
    }

    private void log(String message) {
        System.out.println(message);
    }
}
