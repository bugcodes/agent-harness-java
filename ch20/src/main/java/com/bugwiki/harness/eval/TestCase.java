package com.bugwiki.harness.eval;

/**
 * 定义一个独立 Benchmark 用例，包含初始化脚本、任务提示词与验证脚本。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class TestCase {
    private final String id;
    private final String name;
    private final String setupScript;
    private final String taskPrompt;
    private final String validateScript;
    private final int maxTurns;

    public TestCase(
            String id, String name, String setupScript, String taskPrompt, String validateScript) {
        this(id, name, setupScript, taskPrompt, validateScript, 0);
    }

    public TestCase(
            String id,
            String name,
            String setupScript,
            String taskPrompt,
            String validateScript,
            int maxTurns) {
        this.id = id;
        this.name = name;
        this.setupScript = setupScript;
        this.taskPrompt = taskPrompt;
        this.validateScript = validateScript;
        this.maxTurns = maxTurns;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSetupScript() {
        return setupScript;
    }

    public String getTaskPrompt() {
        return taskPrompt;
    }

    public String getValidateScript() {
        return validateScript;
    }

    public int getMaxTurns() {
        return maxTurns;
    }
}
