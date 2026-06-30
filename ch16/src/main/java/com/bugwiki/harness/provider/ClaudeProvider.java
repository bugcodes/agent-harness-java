package com.bugwiki.harness.provider;

import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.ToolDefinition;
import java.util.List;

/**
 * Marks the Claude provider slot from the Go tutorial while MiniMax is used locally.
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ClaudeProvider implements LlmProvider {
    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools) {
        throw new UnsupportedOperationException(
                "Claude provider is intentionally left as an exercise in this Java port.");
    }
}
