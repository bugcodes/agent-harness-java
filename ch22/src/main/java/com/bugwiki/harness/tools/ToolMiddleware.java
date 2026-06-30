package com.bugwiki.harness.tools;

import com.bugwiki.harness.engine.Reporter;
import com.bugwiki.harness.schema.ToolCall;

/**
 * 在工具真正执行前进行放行或拒绝判断。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public interface ToolMiddleware {
    MiddlewareDecision before(ToolCall call, Reporter reporter);
}
