package com.bugwiki.harness.engine;

import com.bugwiki.harness.context.PromptComposer;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolResult;
import com.bugwiki.harness.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the agent loop against an isolated session working memory.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class AgentEngine {
    private final LlmProvider provider;
    private final ToolRegistry registry;
    private final boolean enableThinking;

    public AgentEngine(LlmProvider provider, ToolRegistry registry, boolean enableThinking) {
        this.provider = provider;
        this.registry = registry;
        this.enableThinking = enableThinking;
    }

    public void run(Session session, Reporter reporter) throws Exception {
        System.out.println("[Engine] 唤醒会话 [" + session.getId() + "]，锁定工作区: " + session.getWorkDir());
        Message systemMsg = new PromptComposer(session.getWorkDir()).build();
        for (int turn = 1; turn <= 20; turn++) {
            List<Message> context = new ArrayList<>();
            context.add(systemMsg);
            context.addAll(session.getWorkingMemory(6));
            if (enableThinking) {
                if (reporter != null) {
                    reporter.onThinking();
                }
                Message thought = provider.generate(context, null);
                if (thought.getContent() != null && !thought.getContent().isBlank()) {
                    session.append(thought);
                    context.add(thought);
                }
            }
            Message action = provider.generate(context, registry.getAvailableTools());
            session.append(action);
            context.add(action);
            if (reporter != null && action.getContent() != null && !action.getContent().isBlank()) {
                reporter.onMessage(action.getContent());
            }
            if (reporter == null && action.getContent() != null && !action.getContent().isBlank()) {
                System.out.println("[对外回复] " + action.getContent());
            }
            if (action.getToolCalls().isEmpty()) {
                return;
            }
            session.appendAll(executeCalls(action.getToolCalls(), reporter));
        }
        throw new IllegalStateException("Agent exceeded max turns.");
    }

    private List<Message> executeCalls(List<ToolCall> calls, Reporter reporter) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, calls.size()));
        try {
            List<CompletableFuture<Message>> futures = new ArrayList<>();
            for (ToolCall call : calls) {
                futures.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    if (reporter != null) {
                                        reporter.onToolCall(call.getName(), String.valueOf(call.getArguments()));
                                    }
                                    ToolResult result = registry.execute(call);
                                    String output = result.getOutput();
                                    if (reporter != null) {
                                        reporter.onToolResult(call.getName(), displayOutput(output), result.isError());
                                    }
                                    return Message.toolResult(call.getId(), output);
                                },
                                executor));
            }
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private String displayOutput(String output) {
        if (output == null || output.length() <= 200) {
            return output;
        }
        return output.substring(0, 200) + "... (已截断)";
    }
}
