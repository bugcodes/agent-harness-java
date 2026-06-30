package com.bugwiki.harness.tools;

/**
 * 定义引擎向子智能体工具暴露的受限执行能力。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public interface AgentRunner {
    String runSub(String taskPrompt, ToolRegistry readOnlyRegistry, Object reporter) throws Exception;
}
