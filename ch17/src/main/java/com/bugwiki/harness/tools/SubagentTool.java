package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 派出只读子智能体执行深度探索，并把精炼报告交回主 Agent。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class SubagentTool implements BaseTool {
    private final AgentRunner runner;
    private final ToolRegistry readOnlyRegistry;
    private final Object reporter;

    public SubagentTool(AgentRunner runner, ToolRegistry readOnlyRegistry, Object reporter) {
        this.runner = runner;
        this.readOnlyRegistry = readOnlyRegistry;
        this.reporter = reporter;
    }

    @Override
    public String name() {
        return "spawn_subagent";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "task_prompt", "给子智能体下达的明确探索指令。");
        schema.withArray("required").add("task_prompt");
        return new ToolDefinition(
                name(),
                "派出一个专门用于深度探索（Exploration）的子智能体。当你需要阅读大量代码、跨文件查找逻辑时请调用此工具。它在探索完毕后，会给你返回一份极度精炼的摘要报告。",
                schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        String prompt = arguments.path("task_prompt").asText();
        System.out.println("[Subagent] 🚀 主 Agent 发起委派！正在拉起探路者: [" + prompt + "]...");
        String report;
        try {
            report = runner.runSub(prompt, readOnlyRegistry, reporter);
        } catch (Exception ex) {
            return new ToolResult(toolCallId, "子智能体执行失败: " + ex.getMessage(), false);
        }
        System.out.println("[Subagent] ✅ 子智能体任务结束。报告返回给主干...");
        return new ToolResult(toolCallId, "【子智能体探索报告】:\n" + report, false);
    }
}
