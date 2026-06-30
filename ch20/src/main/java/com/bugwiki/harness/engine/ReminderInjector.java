package com.bugwiki.harness.engine;

import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolResult;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 监测同一工具同一参数的连续失败，并在疑似死循环时注入强提醒。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ReminderInjector {
    private final Map<String, Integer> consecutiveFailures = new HashMap<>();

    public Message checkAndInject(ToolCall call, ToolResult result) {
        if (call == null || result == null) {
            return null;
        }
        if (!result.isError()) {
            consecutiveFailures.clear();
            return null;
        }

        String fingerprint = fingerprint(call);
        int failCount = consecutiveFailures.getOrDefault(fingerprint, 0) + 1;
        consecutiveFailures.put(fingerprint, failCount);
        System.out.printf("[Reminder] 监控到工具 %s 执行失败，该参数特征连续失败次数: %d%n", call.getName(), failCount);

        if (failCount >= 3) {
            System.out.println("[Reminder] ⚠️ 触发死循环干预！注入强力修正指令。");
            return Message.user(
                    "[SYSTEM REMINDER 警告]\n"
                            + "你似乎陷入了死循环。你刚刚连续 "
                            + failCount
                            + " 次使用相同的参数调用了 '"
                            + call.getName()
                            + "' 工具，并且都失败了。\n"
                            + "请立即停止这种无效的重试！你的注意力被当前的报错过度吸引了。\n"
                            + "你需要：\n"
                            + "1. 停止猜测参数。跳出当前的局部思维。\n"
                            + "2. 彻底改变你的策略。\n"
                            + "3. 如果你确实无法通过系统工具解决当前问题，请直接结束任务并向用户说明你需要什么人工帮助，而不是继续盲目消耗 API 资源尝试。");
        }
        return null;
    }

    private String fingerprint(ToolCall call) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(call.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(String.valueOf(call.getArguments()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            return call.getName() + ":" + call.getArguments();
        }
    }
}
