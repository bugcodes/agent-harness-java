package com.bugwiki.harness.provider;

import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.ToolDefinition;
import java.util.List;

/**
 * Provides chat completions for the agent loop.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public interface LlmProvider {
    Message generate(List<Message> messages, List<ToolDefinition> availableTools) throws Exception;
}
