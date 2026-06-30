package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers tools and dispatches model tool calls to local implementations.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ToolRegistry {
    private final Map<String, BaseTool> tools = new LinkedHashMap<>();

    public void register(BaseTool tool) {
        tools.put(tool.name(), tool);
        System.out.println("[Registry] 成功挂载工具: " + tool.name());
    }

    public List<ToolDefinition> getAvailableTools() {
        return tools.values().stream().map(BaseTool::definition).toList();
    }

    public ToolResult execute(ToolCall call) {
        BaseTool tool = tools.get(call.getName());
        if (tool == null) {
            System.out.println("[Registry] 未知工具: " + call.getName());
            return new ToolResult(call.getId(), "Unknown tool: " + call.getName(), true);
        }
        try {
            return tool.execute(call.getId(), call.getArguments());
        } catch (Exception ex) {
            System.out.println("  -> 工具执行异常: " + ex.getMessage());
            return new ToolResult(call.getId(), ex.getMessage(), true);
        }
    }
}
