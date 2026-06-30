package com.bugwiki.harness.tools;

import com.bugwiki.harness.engine.Reporter;
import com.bugwiki.harness.observability.TraceSpan;
import com.bugwiki.harness.observability.Tracer;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册工具、挂载中间件，并分发模型请求到本地工具实现。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ToolRegistry {
    private final Map<String, BaseTool> tools = new LinkedHashMap<>();
    private final List<ToolMiddleware> middlewares = new ArrayList<>();

    public void register(BaseTool tool) {
        if (tools.containsKey(tool.name())) {
            System.out.println("[Warning] 工具 '" + tool.name() + "' 已经被注册，将被覆盖。");
        }
        tools.put(tool.name(), tool);
        System.out.println("[Registry] 成功挂载工具: " + tool.name());
    }

    public void use(ToolMiddleware middleware) {
        middlewares.add(middleware);
    }

    public List<ToolDefinition> getAvailableTools() {
        return tools.values().stream().map(BaseTool::definition).toList();
    }

    public ToolResult execute(ToolCall call) {
        return execute(call, null);
    }

    public ToolResult execute(ToolCall call, TraceSpan parentSpan) {
        return execute(call, parentSpan, null);
    }

    public ToolResult execute(ToolCall call, TraceSpan parentSpan, Reporter reporter) {
        TraceSpan span = parentSpan == null ? null : Tracer.startSpan(parentSpan, "Tool.Execute");
        if (span != null) {
            span.addAttribute("tool_name", call.getName());
            span.addAttribute("arguments", String.valueOf(call.getArguments()));
        }
        try {
            return executeWithTrace(call, span, reporter);
        } finally {
            if (span != null) {
                span.endSpan();
            }
        }
    }

    private ToolResult executeWithTrace(ToolCall call, TraceSpan span, Reporter reporter) {
        BaseTool tool = tools.get(call.getName());
        if (tool == null) {
            return new ToolResult(call.getId(), "Error: 系统中不存在名为 '" + call.getName() + "' 的工具。", true);
        }
        for (ToolMiddleware middleware : middlewares) {
            MiddlewareDecision decision = middleware.before(call, reporter);
            if (!decision.isAllowed()) {
                System.out.println("[Registry] ⚠️ 工具 " + call.getName() + " 被 Middleware 拦截: " + decision.getReason());
                if (span != null) {
                    span.addAttribute("intercepted", true);
                    span.addAttribute("reject_reason", decision.getReason());
                }
                return new ToolResult(call.getId(), "执行被系统拦截。原因: " + decision.getReason(), true);
            }
        }
        try {
            ToolResult result = tool.execute(call.getId(), call.getArguments());
            if (!result.isError() && span != null) {
                span.addAttribute("output_preview", truncate(result.getOutput(), 100));
            }
            return result;
        } catch (Exception ex) {
            return new ToolResult(call.getId(), "Error executing " + call.getName() + ": " + ex.getMessage(), true);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
