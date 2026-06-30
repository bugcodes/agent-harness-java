package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Defines one executable tool exposed to the agent.
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public interface BaseTool {
    String name();

    ToolDefinition definition();

    ToolResult execute(String toolCallId, JsonNode arguments) throws Exception;
}
