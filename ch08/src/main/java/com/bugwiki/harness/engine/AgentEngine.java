package com.bugwiki.harness.engine;

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
 * Runs the ReAct loop that alternates model calls with local tool execution.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class AgentEngine {
    private final LlmProvider provider;
    private final ToolRegistry registry;
    private final boolean enableThinking;
    private final Path workDir;

    public AgentEngine(
            LlmProvider provider, ToolRegistry registry, Path workDir, boolean enableThinking) {
        this.provider = provider;
        this.registry = registry;
        this.workDir = workDir.toAbsolutePath().normalize();
        this.enableThinking = enableThinking;
    }

    public void run(String userPrompt) throws Exception {
        runInternal(userPrompt, null);
    }

    private void runInternal(String userPrompt, Object ignoredReporter) throws Exception {
        List<Message> history = new ArrayList<>();
        history.add(
                new Message(
                        com.bugwiki.harness.schema.Role.SYSTEM,
                        "You are go-tiny-claw, an expert coding assistant. "
                                + "You have full access to tools in the workspace."));
        history.add(new Message(com.bugwiki.harness.schema.Role.USER, userPrompt));
        System.out.println("[Engine] 引擎启动，锁定工作区: " + workDir);
        System.out.println("[Engine] 慢思考模式 (Thinking Phase): " + enableThinking);
        for (int turn = 1; turn <= 20; turn++) {
            System.out.println();
            System.out.println("========== [Turn " + turn + "] 开始 ==========");
            List<Message> context = history;
            if (enableThinking) {
                System.out.println("[Engine][Phase 1] 剥夺工具访问权，强制进入慢思考与规划阶段...");
                Message thought = provider.generate(context, List.of());
                if (thought.getContent() != null && !thought.getContent().isBlank()) {
                    System.out.println("[内部思考 Trace] " + thought.getContent());
                    history.add(thought);
                }
            }
            System.out.println("[Engine][Phase 2] 恢复工具挂载，等待模型采取行动...");
            Message action = provider.generate(context, registry.getAvailableTools());
            history.add(action);
            if (action.getContent() != null && !action.getContent().isBlank()) {
                System.out.println("[对外回复] " + action.getContent());
            }
            if (action.getToolCalls().isEmpty()) {
                System.out.println("[Engine] 模型未请求调用工具，任务宣告完成。");
                return;
            }
            System.out.println("[Engine] 模型请求并发调用 " + action.getToolCalls().size() + " 个工具...");
            history.addAll(executeCalls(action.getToolCalls()));
        }
        throw new IllegalStateException("Agent exceeded max turns.");
    }

    private List<Message> executeCalls(List<ToolCall> calls) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, calls.size()));
        try {
            List<CompletableFuture<Message>> futures = new ArrayList<>();
            for (int i = 0; i < calls.size(); i++) {
                final int index = i;
                ToolCall call = calls.get(i);
                futures.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    System.out.println("  -> [Java-" + index + "] 🛠️ 触发并行执行: " + call.getName());
                                    ToolResult result = registry.execute(call);
                                    String output = result.getOutput();
                                    if (result.isError()) {
                                        System.out.println("  -> [Java-" + index + "] ❌ 工具执行报错: " + output);
                                    } else {
                                        System.out.println("  -> [Java-" + index + "] ✅ 工具执行成功 (返回 " + output.length() + " 字节)");
                                    }
                                    Message observation = new Message(com.bugwiki.harness.schema.Role.USER, output);
                                    observation.setToolCallId(call.getId());
                                    return observation;
                                },
                                executor));
            }
            List<Message> observations = futures.stream().map(CompletableFuture::join).toList();
            System.out.println("[Engine] 所有并发工具执行完毕，开始聚合观察结果 (Observation)...");
            return observations;
        } finally {
            executor.shutdownNow();
        }
    }
}
