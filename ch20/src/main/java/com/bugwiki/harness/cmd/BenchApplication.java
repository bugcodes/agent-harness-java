package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.eval.BenchmarkRunner;
import com.bugwiki.harness.eval.TestCase;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 启动 ch20 微型 Benchmark 评测集，批量验证 Agent 工具驾驭能力。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class BenchApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        List<TestCase> testCases =
                List.of(
                        new TestCase(
                                "test_001_edit",
                                "测试模糊替换工具的准确性",
                                "echo '{\"name\": \"tiny-claw\", \"version\": \"v1.0.0\"}' > config.json",
                                "当前目录下有一个 config.json。请你使用 edit_file 工具，将其中的 version 从 v1.0.0 改为 v2.0.0。不要做其他多余操作。",
                                "grep '\"version\": \"v2.0.0\"' config.json"),
                        new TestCase(
                                "test_002_code_gen",
                                "测试代码阅读与创建新文件的综合能力",
                                "echo 'package math\n\nfunc Multiply(a, b int) int {\n\treturn a * b\n}' > math.go",
                                "当前目录下有一个 math.go。请你仔细阅读它，然后在同级目录下，帮我写一个规范的单元测试文件 math_test.go，用来测试 Multiply 函数。请务必包含正常的测试用例。",
                                "go mod init bench && go test -v ./..."));

        BenchmarkRunner runner = new BenchmarkRunner(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
        runner.runSuite(testCases);
    }
}
