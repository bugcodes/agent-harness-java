package com.bugwiki.harness.tools;

/**
 * 表示工具中间件的放行或拒绝结果。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class MiddlewareDecision {
    private final boolean allowed;
    private final String reason;

    private MiddlewareDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static MiddlewareDecision allow() {
        return new MiddlewareDecision(true, "");
    }

    public static MiddlewareDecision deny(String reason) {
        return new MiddlewareDecision(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
