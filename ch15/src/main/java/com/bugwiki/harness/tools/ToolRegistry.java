package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册工具并分发模型请求到本地工具实现。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ToolRegistry {
    private final Map<String, BaseTool> tools = new LinkedHashMap<>();

    public void register(BaseTool tool) {
        if (tools.containsKey(tool.name())) {
            System.out.println("[Warning] 工具 '" + tool.name() + "' 已经被注册，将被覆盖。");
        }
        tools.put(tool.name(), tool);
        System.out.println("[Registry] 成功挂载工具: " + tool.name());
    }

    public List<ToolDefinition> getAvailableTools() {
        return tools.values().stream().map(BaseTool::definition).toList();
    }

    public ToolResult execute(ToolCall call) {
        BaseTool tool = tools.get(call.getName());
        if (tool == null) {
            return new ToolResult(call.getId(), "Error: 系统中不存在名为 '" + call.getName() + "' 的工具。", true);
        }
        try {
            return tool.execute(call.getId(), call.getArguments());
        } catch (Exception ex) {
            return new ToolResult(call.getId(), "Error executing " + call.getName() + ": " + ex.getMessage(), true);
        }
    }
}
