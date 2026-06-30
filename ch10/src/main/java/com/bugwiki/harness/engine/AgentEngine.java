package com.bugwiki.harness.engine;

import com.bugwiki.harness.context.PromptComposer;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolResult;
import com.bugwiki.harness.tools.ToolRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the agent loop using a prompt composer for the system context.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class AgentEngine {
    private final LlmProvider provider;
    private final ToolRegistry registry;
    private final boolean enableThinking;
    private final Path workDir;
    private final PromptComposer composer;

    public AgentEngine(
            LlmProvider provider, ToolRegistry registry, Path workDir, boolean enableThinking) {
        this.provider = provider;
        this.registry = registry;
        this.workDir = workDir.toAbsolutePath().normalize();
        this.enableThinking = enableThinking;
        // 【核心修改】动态组装 System Prompt，彻底替换掉以前硬编码的面条提示词！
        this.composer = new PromptComposer(this.workDir);
    }

    public void run(String userPrompt) throws Exception {
        runInternal(userPrompt, null);
    }

    public void run(String userPrompt, Reporter reporter) throws Exception {
        runInternal(userPrompt, reporter);
    }

    private void runInternal(String userPrompt, Reporter reporter) throws Exception {
        List<Message> history = new ArrayList<>();
        // 【核心修改】动态组装 System Prompt，彻底替换掉以前硬编码的面条提示词！
        history.add(composer.build());
        history.add(Message.user(userPrompt));
        System.out.println("[Engine] 引擎启动，锁定工作区: " + workDir);
        for (int turn = 1; turn <= 20; turn++) {
            List<Message> contextHistory = history;
            if (enableThinking) {
                if (reporter != null) {
                    reporter.onThinking();
                }
                Message thought = provider.generate(contextHistory, null);
                if (thought.getContent() != null && !thought.getContent().isBlank()) {
                    history.add(thought);
                }
            }
            Message action = provider.generate(contextHistory, registry.getAvailableTools());
            history.add(action);
            if (reporter != null && action.getContent() != null && !action.getContent().isBlank()) {
                reporter.onMessage(action.getContent());
            }
            if (reporter == null && action.getContent() != null && !action.getContent().isBlank()) {
                System.out.println("[对外回复] " + action.getContent());
            }
            if (action.getToolCalls().isEmpty()) {
                return;
            }
            history.addAll(executeCalls(action.getToolCalls(), reporter));
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
