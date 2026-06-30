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
        // 1. 路由查找：如果在注册表中找不到该工具，这是模型产生了幻觉，直接向模型抛出错误
        BaseTool tool = tools.get(call.getName());
        if (tool == null) {
            return new ToolResult(call.getId(), "Error: 系统中不存在名为 '" + call.getName() + "' 的工具。", true);
        }
        try {
            // 2. 执行工具逻辑：将原始的 JSON 字节流直接丢给具体工具
            return tool.execute(call.getId(), call.getArguments());
        } catch (Exception ex) {
            // 3. 封装结果：将执行结果或底层物理错误封装后返回给 Main Loop
            return new ToolResult(call.getId(), "Error executing " + call.getName() + ": " + ex, true);
        }
    }
}
