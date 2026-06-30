package com.bugwiki.harness.context;

import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 保存单个 Agent 会话的工作区、历史消息以及累计资源消耗。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class Session {
    private final String id;
    private final Path workDir;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<Message> history = new ArrayList<>();
    private int totalPromptTokens;
    private int totalCompletionTokens;
    private double totalCostCny;

    public Session(String id, Path workDir) {
        this.id = id;
        this.workDir = workDir.toAbsolutePath().normalize();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public Path getWorkDir() {
        return workDir;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized Instant getUpdatedAt() {
        return updatedAt;
    }

    public synchronized void append(Message... messages) {
        Collections.addAll(history, messages);
        updatedAt = Instant.now();
    }

    public synchronized void appendAll(List<Message> messages) {
        history.addAll(messages);
        updatedAt = Instant.now();
    }

    public synchronized List<Message> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized List<Message> getWorkingMemory(int limit) {
        int total = history.size();
        if (limit <= 0 || total <= limit) {
            return new ArrayList<>(history);
        }
        List<Message> result = new ArrayList<>(history.subList(total - limit, total));
        while (!result.isEmpty()) {
            Message first = result.get(0);
            if (first.getRole() == Role.USER
                    && first.getToolCallId() != null
                    && !first.getToolCallId().isBlank()) {
                result.remove(0);
            } else {
                break;
            }
        }
        return result;
    }

    public synchronized void recordUsage(int promptTokens, int completionTokens, double costCny) {
        totalPromptTokens += promptTokens;
        totalCompletionTokens += completionTokens;
        totalCostCny += costCny;
    }

    public synchronized int getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public synchronized int getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public synchronized double getTotalCostCny() {
        return totalCostCny;
    }
}
