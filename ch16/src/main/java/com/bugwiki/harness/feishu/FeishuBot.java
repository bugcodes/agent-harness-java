package com.bugwiki.harness.feishu;

import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.schema.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReadV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;

/**
 * 接入飞书消息事件，处理审批口令，并把普通消息委托给 Agent 引擎执行。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class FeishuBot {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Client client;
    private final String appId;
    private final String appSecret;
    private final AgentEngine engine;
    private final Session session;
    private FeishuReporter reporter;

    public FeishuBot(AgentEngine engine, Session session) {
        this(engine, session, System.getenv("FEISHU_APP_ID"), System.getenv("FEISHU_APP_SECRET"));
    }

    public FeishuBot(AgentEngine engine, Session session, String appId, String appSecret) {
        if (isBlank(appId) || isBlank(appSecret)) {
            throw new IllegalStateException("请设置 FEISHU_APP_ID 和 FEISHU_APP_SECRET");
        }
        this.engine = engine;
        this.session = session;
        this.appId = appId;
        this.appSecret = appSecret;
        this.client = Client.newBuilder(appId, appSecret).build();
    }

    public Client getClient() {
        return client;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public FeishuReporter reporter() {
        return reporter;
    }

    public EventDispatcher getEventDispatcher() {
        String encryptKey = env("FEISHU_ENCRYPT_KEY");
        String verifyToken = env("FEISHU_VERIFY_TOKEN");
        return EventDispatcher.newBuilder(verifyToken, encryptKey)
                .onP2MessageReceiveV1(
                        new ImService.P2MessageReceiveV1Handler() {
                            @Override
                            public void handle(P2MessageReceiveV1 event) {
                                EventMessage message = event.getEvent().getMessage();
                                String content = extractText(message.getContent());
                                String chatId = message.getChatId();
                                System.out.printf("[Feishu] 收到会话 %s 消息: %s%n", chatId, content);

                                if (content.startsWith("approve ")) {
                                    String taskId = content.substring("approve ".length()).trim();
                                    ApprovalManager.GLOBAL.resolveApproval(taskId, true, "人类管理员已批准操作");
                                    System.out.printf("[Feishu] 会话 %s: ✅ 已为您批准任务 %s%n", chatId, taskId);
                                    return;
                                }
                                if (content.startsWith("reject ")) {
                                    String taskId = content.substring("reject ".length()).trim();
                                    ApprovalManager.GLOBAL.resolveApproval(taskId, false, "人类管理员认为该操作存在极高风险，已无情拒绝");
                                    System.out.printf("[Feishu] 会话 %s: 🚫 已拒绝任务 %s%n", chatId, taskId);
                                    return;
                                }

                                Thread worker = new Thread(() -> handleAgentRun(chatId, content));
                                worker.setName("feishu-agent-" + chatId);
                                worker.start();
                            }
                        })
                .onP2MessageReadV1(
                        new ImService.P2MessageReadV1Handler() {
                            @Override
                            public void handle(P2MessageReadV1 event) {
                                // 消息已读事件，静默忽略。
                            }
                        })
                .build();
    }

    public void handleAgentRun(String chatId, String prompt) {
        FeishuReporter nextReporter = new FeishuReporter(client, chatId);
        this.reporter = nextReporter;
        try {
            session.append(Message.user(prompt));
            engine.run(session, nextReporter);
        } catch (Exception ex) {
            nextReporter.sendMsg("❌ Agent 运行崩溃: " + ex.getMessage());
        }
    }

    private String extractText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(content);
            JsonNode text = node.get("text");
            return text == null ? content : text.asText();
        } catch (Exception ignored) {
            return content;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
