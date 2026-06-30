package com.bugwiki.harness.eval;

import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.observability.CostTracker;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.EditFileTool;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行一组 Benchmark 用例，并通过 Setup/Validate 脚本完成靶机准备与验收。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class BenchmarkRunner {
    private final String apiKey;
    private final String modelName;

    public BenchmarkRunner(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    public void runSuite(List<TestCase> cases) throws Exception {
        System.out.println("==================================================");
        System.out.println("🚀 启动自动化 Harness Benchmark 评估... | 模型: " + modelName);
        System.out.println("==================================================");

        List<TestResult> results = new ArrayList<>();
        int passedCount = 0;
        double totalCost = 0.0;
        for (TestCase testCase : cases) {
            System.out.println();
            System.out.println(">>> ⏳ 正在执行用例 [" + testCase.getId() + "]: " + testCase.getName());
            TestResult result = runOne(testCase);
            results.add(result);
            totalCost += result.getTotalCostCny();
            if (result.isPassed()) {
                passedCount++;
                System.out.printf(
                        ">>> ✅ 用例 [%s] 测试通过! | 耗时: %dms | 花费: $%.6f%n",
                        result.getTestCaseId(),
                        result.getDurationMillis(),
                        result.getTotalCostCny());
            } else {
                System.out.printf(
                        ">>> ❌ 用例 [%s] 测试失败! | 错误: %s%n",
                        result.getTestCaseId(), result.getErrorMessage());
            }
        }

        double successRate = passedCount * 100.0 / cases.size();
        System.out.println();
        System.out.println("================ 🏆 跑分终极报告 ================");
        System.out.printf("总用例数: %d | 成功数: %d | 成功率: %.2f%%%n", cases.size(), passedCount, successRate);
        System.out.printf("总消耗成本: $%.6f%n", totalCost);
        System.out.println("==================================================");
    }

    private TestResult runOne(TestCase testCase) throws Exception {
        Instant start = Instant.now();
        Path workDir =
                Path.of("workspace", testCase.getId() + "_" + Instant.now().getEpochSecond())
                        .toAbsolutePath()
                        .normalize();
        Files.createDirectories(workDir);

        if (testCase.getSetupScript() != null && !testCase.getSetupScript().isBlank()) {
            ScriptResult setup = runScript(workDir, testCase.getSetupScript());
            if (setup.exitCode() != 0) {
                return fail(testCase, start, 0.0, "靶机 Setup 失败");
            }
        }

        Session session = new Session(testCase.getId(), workDir);
        LlmProvider provider = new CostTracker(new MiniMaxProvider(apiKey, modelName), modelName, session);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, false, false);
        session.append(Message.user(testCase.getTaskPrompt()));
        try {
            engine.run(session, null);
        } catch (Exception ex) {
            return fail(testCase, start, session.getTotalCostCny(), "Agent 崩溃: " + ex.getMessage());
        }

        ScriptResult validation = runScript(workDir, testCase.getValidateScript());
        if (validation.exitCode() != 0) {
            return fail(
                    testCase,
                    start,
                    session.getTotalCostCny(),
                    "验证脚本执行失败: " + validation.output());
        }

        return new TestResult(
                testCase.getId(),
                true,
                session.getTotalCostCny(),
                Duration.between(start, Instant.now()).toMillis(),
                "");
    }

    private TestResult fail(TestCase testCase, Instant start, double cost, String error) {
        return new TestResult(
                testCase.getId(),
                false,
                cost,
                Duration.between(start, Instant.now()).toMillis(),
                error);
    }

    private ScriptResult runScript(Path workDir, String script) throws Exception {
        Process process =
                new ProcessBuilder("bash", "-c", script)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ScriptResult(exitCode, output);
    }

    private record ScriptResult(int exitCode, String output) {}
}
