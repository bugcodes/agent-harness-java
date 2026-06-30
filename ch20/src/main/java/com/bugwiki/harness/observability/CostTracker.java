package com.bugwiki.harness.observability;

import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.Usage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 包装底层模型 Provider，记录每次调用延迟、Token 消耗与会话累计费用。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class CostTracker implements LlmProvider {
    private static final Map<String, Price> PRICING_MODEL =
            Map.of(
                    "glm-4.5-air", new Price(0.15, 0.15),
                    "MiniMax-M3", new Price(0.06, 0.06));

    private final LlmProvider nextProvider;
    private final String modelName;
    private final Session session;

    public CostTracker(LlmProvider nextProvider, String modelName, Session session) {
        this.nextProvider = nextProvider;
        this.modelName = modelName;
        this.session = session;
    }

    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
            throws Exception {
        Instant startTime = Instant.now();
        Message response;
        try {
            response = nextProvider.generate(messages, availableTools);
        } catch (Exception ex) {
            System.out.println("[Tracker] ❌ API 调用失败，耗时: " + Duration.between(startTime, Instant.now()));
            throw ex;
        }

        Duration latency = Duration.between(startTime, Instant.now());
        Usage usage = response.getUsage();
        if (usage != null) {
            int promptTokens = usage.getPromptTokens();
            int completionTokens = usage.getCompletionTokens();
            double cost = 0.0;
            Price price = PRICING_MODEL.get(modelName);
            if (price != null) {
                cost =
                        (promptTokens * price.inputPrice()
                                        + completionTokens * price.outputPrice())
                                / 1_000_000.0;
            }

            System.out.printf(
                    "[Tracker] 📊 API 调用完成 | 耗时: %s | 输入: %d tk | 输出: %d tk | 花费: ¥%.6f%n",
                    latency, promptTokens, completionTokens, cost);

            if (session != null) {
                session.recordUsage(promptTokens, completionTokens, cost);
                System.out.printf(
                        "[Tracker] 💰 当前会话 (%s) 累计花费: ¥%.6f%n",
                        session.getId(), session.getTotalCostCny());
            }
        } else {
            System.out.println("[Tracker] ⚠️ API 调用完成，但未返回 Usage 数据 | 耗时: " + latency);
        }
        return response;
    }

    /**
     * 保存每百万 Token 的输入和输出价格。
     *
     * @author zhaobinjie
     * @date 2026-06-30
     */
    private record Price(double inputPrice, double outputPrice) {}
}
