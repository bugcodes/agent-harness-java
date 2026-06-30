package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import java.util.List;

/**
 * Defines the minimal tool registry contract used by the chapter three loop.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public interface Registry {
    List<ToolDefinition> getAvailableTools();

    ToolResult execute(ToolCall call);
}
