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
 * @date 2026-06-25
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
            System.out.println("  -> 执行工具: " + call.getName() + ", 参数: " + call.getArguments());
            ToolResult result = tool.execute(call.getId(), call.getArguments());
            System.out.println(
                    "  -> " + (result.isError() ? "工具执行报错: " : "工具执行成功: ") + preview(result.getOutput()));
            return result;
        } catch (Exception ex) {
            System.out.println("  -> 工具执行异常: " + ex.getMessage());
            return new ToolResult(call.getId(), ex.getMessage(), true);
        }
    }

    private String preview(String output) {
        if (output == null) {
            return "";
        }
        String normalized = output.replace("\n", " ");
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }
}
