package com.bugwiki.harness.feishu;

import com.bugwiki.harness.engine.Reporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.enums.CreateMessageCreateMessageV1ReceiveIDTypeEnum;
import com.lark.oapi.service.im.v1.enums.MsgTypeEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import java.util.Map;

/**
 * 将 Agent 生命周期事件转成飞书群聊文本消息。
 *
 * @author zhaobinjie
 * @date 2026-06-26
 */
public class FeishuReporter implements Reporter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Client client;
    private final String chatId;

    public FeishuReporter(Client client, String chatId) {
        this.client = client;
        this.chatId = chatId;
    }

    @Override
    public void onThinking() {
        sendMsg("🤔 模型正在慢思考 (Thinking)...");
    }

    @Override
    public void onToolCall(String toolName, String arguments) {
        sendMsg("🛠️ **正在执行工具**：`" + toolName + "`\n参数：`" + arguments + "`");
    }

    @Override
    public void onToolResult(String toolName, String result, boolean error) {
        if (error) {
            sendMsg("⚠️ **执行报错** (" + toolName + ")：\n" + result);
        } else {
            sendMsg("✅ **执行成功** (" + toolName + ")");
        }
    }

    @Override
    public void onMessage(String content) {
        sendMsg(content);
    }

    public void sendMsg(String text) {
        try {
            String content = MAPPER.writeValueAsString(Map.of("text", text));
            CreateMessageReq request =
                    CreateMessageReq.newBuilder()
                            .receiveIdType(CreateMessageCreateMessageV1ReceiveIDTypeEnum.CHAT_ID)
                            .createMessageReqBody(
                                    CreateMessageReqBody.newBuilder()
                                            .receiveId(chatId)
                                            .msgType(MsgTypeEnum.MSG_TYPE_TEXT.getValue())
                                            .content(content)
                                            .build())
                            .build();
            CreateMessageResp response = client.im().message().create(request);
            if (!response.success()) {
                System.out.println(
                        "[Feishu] 消息发送失败: code="
                                + response.getCode()
                                + ", msg="
                                + response.getMsg());
            }
        } catch (Exception ex) {
            System.out.println("[Feishu] 消息发送异常: " + ex.getMessage());
        }
    }
}
