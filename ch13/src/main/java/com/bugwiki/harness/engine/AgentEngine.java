package com.bugwiki.harness.engine;

import com.bugwiki.harness.context.Compactor;
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
 * 运行 Agent 的思考、行动和工具观察循环，并在计划模式下维护上下文压缩。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class AgentEngine {
    private final LlmProvider provider;
    private final ToolRegistry registry;
    private final boolean enableThinking;
    private final boolean planMode;
    private final Compactor compactor = new Compactor(20_000, 6);

    public AgentEngine(
            LlmProvider provider, ToolRegistry registry, boolean enableThinking, boolean planMode) {
        this.provider = provider;
        this.registry = registry;
        this.enableThinking = enableThinking;
        this.planMode = planMode;
    }

    public void run(Session session, Reporter reporter) throws Exception {
        System.out.println("[Engine] 唤醒会话 [" + session.getId() + "]，工作区: " + session.getWorkDir());
        Message systemMsg = new PromptComposer(session.getWorkDir(), planMode).build();
        while (true) {
            List<Message> context = new ArrayList<>();
            context.add(systemMsg);
            context.addAll(session.getWorkingMemory(20));
            List<Message> compactedContext = new ArrayList<>(compactor.compact(context));
            String currentTurnThinkingContent = "";

            if (enableThinking) {
                if (reporter != null) {
                    reporter.onThinking();
                }
                Message thought = provider.generate(compactedContext, null);
                if (thought.getContent() != null && !thought.getContent().isBlank()) {
                    currentTurnThinkingContent = thought.getContent();
                    compactedContext.add(thought);
                }
            }
            Message action = provider.generate(compactedContext, registry.getAvailableTools());
            Message finalAssistant =
                    Message.assistant(
                            (currentTurnThinkingContent + "\n" + nullToEmpty(action.getContent())).trim(),
                            action.getToolCalls());
            session.append(finalAssistant);
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
