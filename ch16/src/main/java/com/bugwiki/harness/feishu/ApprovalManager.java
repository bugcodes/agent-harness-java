package com.bugwiki.harness.feishu;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 管理飞书人工审批请求，并识别需要审批的高危工具调用。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ApprovalManager {
    public static final ApprovalManager GLOBAL = new ApprovalManager();

    private final Map<String, CompletableFuture<ApprovalResult>> pendingTasks = new ConcurrentHashMap<>();

    public ApprovalResult waitForApproval(
            String taskId, String toolName, String args, FeishuReporter reporter) {
        CompletableFuture<ApprovalResult> future = new CompletableFuture<>();
        pendingTasks.put(taskId, future);

        String noticeMsg =
                """
                ⚠️ **高危操作审批请求**
                Agent 试图执行以下动作:
                - 工具: %s
                - 参数: %s

                任务 ID: **%s**

                👉 请回复 "approve %s" 或 "reject %s" 决定是否放行。"""
                        .formatted(toolName, args, taskId, taskId, taskId);

        if (reporter != null) {
            reporter.sendMsg(noticeMsg);
        } else {
            System.out.println("\n\u001B[31m[需要审批 TaskID: " + taskId + "]\u001B[0m " + noticeMsg);
        }

        System.out.println("[Approval] 发送审批请求 (TaskID: " + taskId + ")，协程挂起等待...");
        try {
            return future.join();
        } finally {
            pendingTasks.remove(taskId);
        }
    }

    public void resolveApproval(String taskId, boolean allowed, String reason) {
        CompletableFuture<ApprovalResult> future = pendingTasks.get(taskId);
        if (future != null) {
            System.out.println("[Approval] 收到飞书审批结果 (TaskID: " + taskId + ", Allowed: " + allowed + ")");
            future.complete(new ApprovalResult(allowed, reason));
        }
    }

    public boolean isDangerousCommand(String toolName, String arguments) {
        if (!"bash".equals(toolName) && !"write_file".equals(toolName) && !"edit_file".equals(toolName)) {
            return false;
        }
        String text = arguments == null ? "" : arguments;
        if ("bash".equals(toolName)) {
            return Pattern.compile("rm\\s+-r").matcher(text).find()
                    || Pattern.compile("sudo\\s+").matcher(text).find()
                    || Pattern.compile("drop\\s+").matcher(text).find()
                    || Pattern.compile(">.*\\.go").matcher(text).find();
        }
        return false;
    }

    /**
     * 表示一次人工审批的最终结果。
     *
     * @author zhaobinjie
     * @date 2026-06-30
     */
    public record ApprovalResult(boolean allowed, String reason) {}
}
