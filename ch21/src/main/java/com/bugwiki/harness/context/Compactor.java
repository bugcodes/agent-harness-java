package com.bugwiki.harness.context;

import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * 在上下文过长时压缩早期消息，并保护最近的工作记忆。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class Compactor {
    private final int maxChars;
    private final int retainLastMessages;

    public Compactor(int maxChars, int retainLastMessages) {
        this.maxChars = maxChars;
        this.retainLastMessages = retainLastMessages;
    }

    public List<Message> compact(List<Message> messages) {
        int currentLength = estimate(messages);
        if (currentLength < maxChars) {
            return messages;
        }
        System.out.printf(
                "[Compactor] ⚠️ 内存告警：当前上下文长度 (%d 字符) 超过阈值 (%d)，触发压缩清理...%n",
                currentLength, maxChars);
        List<Message> result = new ArrayList<>();
        int protectStartIndex = Math.max(0, messages.size() - retainLastMessages);

        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message.getRole() == Role.SYSTEM) {
                result.add(message);
                continue;
            }

            Message next = copyOf(message);
            boolean inWorkingMemory = i >= protectStartIndex;

            if (message.getRole() == Role.USER
                    && message.getToolCallId() != null
                    && !message.getToolCallId().isBlank()) {
                String content = message.getContent() == null ? "" : message.getContent();
                if (!inWorkingMemory && content.length() > 200) {
                    next.setContent("...[为了节省内存，早期的工具输出已被系统强制清理。原始长度: " + content.length() + " 字节]...");
                } else if (inWorkingMemory && content.length() > 1000) {
                    String head = content.substring(0, 500);
                    String tail = content.substring(content.length() - 500);
                    next.setContent(
                            head
                                    + "\n\n...[内容过长，中间 "
                                    + (content.length() - 1000)
                                    + " 字节已被系统截断]...\n\n"
                                    + tail);
                }
            } else if (message.getRole() == Role.ASSISTANT && message.getContent() != null) {
                if (!inWorkingMemory && message.getContent().length() > 200) {
                    next.setContent("...[早期的推理思考过程已折叠]...");
                }
            }
            result.add(next);
        }
        System.out.printf("[Compactor] ✅ 压缩完成。上下文长度从 %d 降至 %d 字符。%n", currentLength, estimate(result));
        return result;
    }

    private int estimate(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            total += message.getContent() == null ? 0 : message.getContent().length();
            for (var toolCall : message.getToolCalls()) {
                total += toolCall.getName() == null ? 0 : toolCall.getName().length();
                total += toolCall.getArguments() == null ? 0 : toolCall.getArguments().toString().length();
            }
        }
        return total;
    }

    private Message copyOf(Message message) {
        Message copy = new Message(message.getRole(), message.getContent());
        copy.setToolCalls(message.getToolCalls());
        copy.setToolCallId(message.getToolCallId());
        return copy;
    }
}
