package com.bugwiki.harness.engine;

import com.bugwiki.harness.context.Compactor;
import com.bugwiki.harness.context.PromptComposer;
import com.bugwiki.harness.context.RecoveryManager;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolResult;
import com.bugwiki.harness.tools.AgentRunner;
import com.bugwiki.harness.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 运行主 Agent 循环，并为 spawn_subagent 提供一次性受限探索循环。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class AgentEngine implements AgentRunner {
    private final LlmProvider provider;
    private final ToolRegistry registry;
    private final boolean enableThinking;
    private final boolean planMode;
    private final Compactor compactor = new Compactor(20_000, 6);
    private final RecoveryManager recovery = new RecoveryManager();
    private final ReminderInjector reminderInjector = new ReminderInjector();

    public AgentEngine(
            LlmProvider provider, ToolRegistry registry, boolean enableThinking, boolean planMode) {
        this.provider = provider;
        this.registry = registry;
        this.enableThinking = enableThinking;
        this.planMode = planMode;
    }

    public void run(Session session, Reporter reporter) throws Exception {
        System.out.println(
                "[Engine] 唤醒会话 ["
                        + session.getId()
                        + "]，锁定工作区: "
                        + session.getWorkDir()
                        + " (PlanMode: "
                        + planMode
                        + ")");
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

            ExecutionBatch batch = executeCalls(action.getToolCalls(), reporter);
            session.appendAll(batch.messages);

            Message reminder = reminderInjector.checkAndInject(batch.lastCall, batch.lastResult);
            if (reminder != null) {
                session.append(reminder);
            }
        }
    }

    private ExecutionBatch executeCalls(List<ToolCall> calls, Reporter reporter) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, calls.size()));
        try {
            List<CompletableFuture<ToolExecution>> futures = new ArrayList<>();
            for (ToolCall call : calls) {
                futures.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    if (reporter != null) {
                                        reporter.onToolCall(call.getName(), String.valueOf(call.getArguments()));
                                    }
                                    ToolResult result = registry.execute(call);
                                    String output = result.getOutput();
                                    if (result.isError()) {
                                        output = recovery.analyzeAndInject(call.getName(), output);
                                    }
                                    if (reporter != null) {
                                        reporter.onToolResult(call.getName(), displayOutput(output), result.isError());
                                    }
                                    return new ToolExecution(call, result, Message.toolResult(call.getId(), output));
                                },
                                executor));
            }
            List<ToolExecution> executions = futures.stream().map(CompletableFuture::join).toList();
            List<Message> observations = executions.stream().map(execution -> execution.message).toList();
            ToolCall lastCall = executions.isEmpty() ? null : executions.get(0).call;
            ToolResult lastResult = executions.isEmpty() ? null : executions.get(0).result;
            return new ExecutionBatch(observations, lastCall, lastResult);
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public String runSub(String taskPrompt, ToolRegistry readOnlyRegistry, Object reporter)
            throws Exception {
        Reporter childReporter = reporter instanceof Reporter ? (Reporter) reporter : null;
        List<Message> contextHistory = new ArrayList<>();
        contextHistory.add(
                Message.system(
                        """
                        你是一个专门负责深度探索的探路者 (Explorer Subagent)。
                        你的任务是根据主架构师的指令，在当前工作区内仔细阅读代码、查阅日志，搜集足够的信息。

                        【核心纪律】
                        1. 你必须、且只能依靠内置工具（如 bash 的 find/grep，或 read_file）去寻找答案。绝对不允许凭空捏造或猜测！
                        2. 如果你没有找到确切的答案，你必须继续使用工具深入搜索。
                        3. 当且仅当你找到了确切的线索后，停止调用工具，直接输出一段纯文本作为你的终极汇报。主架构师会根据你的汇报来做下一步决策。
                        """));
        contextHistory.add(Message.user(taskPrompt));

        int maxSubTurns = 10;
        for (int turnCount = 1; ; turnCount++) {
            if (turnCount > maxSubTurns) {
                throw new IllegalStateException(
                        "子智能体探索过于深入，超过 "
                                + maxSubTurns
                                + " 轮被强制召回，请主 Agent 给它更明确的指令");
            }

            List<Message> compactedContext = compactor.compact(contextHistory);
            Message action;
            try {
                action = provider.generate(compactedContext, readOnlyRegistry.getAvailableTools());
            } catch (Exception ex) {
                throw new IllegalStateException("子智能体推理失败: " + ex.getMessage(), ex);
            }

            contextHistory.add(action);
            if (action.getToolCalls().isEmpty()) {
                return nullToEmpty(action.getContent());
            }

            List<Message> observationMsgs =
                    executeReadOnlyCalls(action.getToolCalls(), readOnlyRegistry, childReporter);
            contextHistory.addAll(observationMsgs);
        }
    }

    private List<Message> executeReadOnlyCalls(
            List<ToolCall> calls, ToolRegistry readOnlyRegistry, Reporter reporter) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, calls.size()));
        try {
            List<CompletableFuture<Message>> futures = new ArrayList<>();
            for (ToolCall call : calls) {
                futures.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    String subagentToolName = "[Subagent] " + call.getName();
                                    if (reporter != null) {
                                        reporter.onToolCall(
                                                subagentToolName, String.valueOf(call.getArguments()));
                                    }
                                    ToolResult result = readOnlyRegistry.execute(call);
                                    String output = result.getOutput();
                                    if (result.isError()) {
                                        output = recovery.analyzeAndInject(call.getName(), output);
                                    }
                                    if (reporter != null) {
                                        reporter.onToolResult(
                                                subagentToolName,
                                                displayOutput(output),
                                                result.isError());
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

    /**
     * 保存一次工具执行的原始结果和最终 observation。
     *
     * @author zhaobinjie
     * @date 2026-06-30
     */
    private static class ToolExecution {
        private final ToolCall call;
        private final ToolResult result;
        private final Message message;

        private ToolExecution(ToolCall call, ToolResult result, Message message) {
            this.call = call;
            this.result = result;
            this.message = message;
        }
    }

    /**
     * 保存工具观察结果以及用于提醒分析的首个工具调用。
     *
     * @author zhaobinjie
     * @date 2026-06-30
     */
    private static class ExecutionBatch {
        private final List<Message> messages;
        private final ToolCall lastCall;
        private final ToolResult lastResult;

        private ExecutionBatch(List<Message> messages, ToolCall lastCall, ToolResult lastResult) {
            this.messages = messages;
            this.lastCall = lastCall;
            this.lastResult = lastResult;
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
