package com.bugwiki.harness.schema;

/**
 * 记录单次模型 API 调用消耗的输入与输出 Token 数量。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class Usage {
    private int promptTokens;
    private int completionTokens;

    public Usage() {}

    public Usage(int promptTokens, int completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }
}
