package com.bugwiki.harness.eval;

/**
 * 保存单个 Benchmark 用例的通过状态、成本、耗时和失败原因。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class TestResult {
    private final String testCaseId;
    private final boolean passed;
    private final double totalCostCny;
    private final long durationMillis;
    private final String errorMessage;

    public TestResult(
            String testCaseId,
            boolean passed,
            double totalCostCny,
            long durationMillis,
            String errorMessage) {
        this.testCaseId = testCaseId;
        this.passed = passed;
        this.totalCostCny = totalCostCny;
        this.durationMillis = durationMillis;
        this.errorMessage = errorMessage;
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public boolean isPassed() {
        return passed;
    }

    public double getTotalCostCny() {
        return totalCostCny;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
