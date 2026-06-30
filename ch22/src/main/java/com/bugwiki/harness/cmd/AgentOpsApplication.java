package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.feishu.ApprovalManager;
import com.bugwiki.harness.feishu.FeishuBot;
import com.bugwiki.harness.feishu.FeishuReporter;
import com.bugwiki.harness.observability.CostTracker;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.EditFileTool;
import com.bugwiki.harness.tools.MiddlewareDecision;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动 ch22 AgentOps 飞书服务端，挂载高危操作审批与会话级 Agent 引擎工厂。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class AgentOpsApplication {
    private static final AppConfig CONFIG = AppConfig.load();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        System.out.println("🚀 正在启动 go-tiny-claw AgentOps 飞书服务端...");
        if (isBlank(System.getenv("FEISHU_APP_ID"))) {
            throw new IllegalStateException("❌ 请先导出飞书相关的环境变量");
        }

        Path workDir = Path.of("").toAbsolutePath().normalize().resolve("workspace");
        Files.createDirectories(workDir);

        String modelName = CONFIG.getMinimaxModel();
        LlmProvider llmProvider = new MiniMaxProvider(CONFIG.getMinimaxApiKey(), modelName);
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new EditFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.use(
                (call, reporter) -> {
                    String argsText = String.valueOf(call.getArguments());
                    if (!ApprovalManager.GLOBAL.isDangerousCommand(call.getName(), argsText)) {
                        return MiddlewareDecision.allow();
                    }

                    String taskId = call.getId();
                    System.out.println(
                            "[Middleware] 拦截到高危操作: " + call.getName() + "，触发飞书审批挂起...");
                    FeishuReporter feishuReporter =
                            reporter instanceof FeishuReporter ? (FeishuReporter) reporter : null;
                    ApprovalManager.ApprovalResult result =
                            ApprovalManager.GLOBAL.waitForApproval(
                                    taskId, call.getName(), argsText, feishuReporter);
                    if (!result.allowed()) {
                        return MiddlewareDecision.deny(result.reason());
                    }
                    return MiddlewareDecision.allow();
                });
        System.out.println("🛡️ 安全防御 Middleware 已挂载。");

        FeishuBot.AgentEngineFactory engineFactory =
                session -> createEngine(llmProvider, modelName, session, registry);
        FeishuBot bot = new FeishuBot(engineFactory, workDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(48080), 0);
        server.createContext(
                "/webhook/event",
                exchange -> {
                    try {
                        String body =
                                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String chatId = chatIdOf(body, exchange.getRequestHeaders().getFirst("X-Chat-Id"));
                        String content = contentOf(body);
                        bot.handleIncomingMessage(chatId, content);
                        byte[] ok = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                        exchange.sendResponseHeaders(200, ok.length);
                        exchange.getResponseBody().write(ok);
                    } catch (Exception ex) {
                        byte[] error =
                                ("{\"status\":\"error\",\"message\":\""
                                                + ex.getMessage().replace("\"", "\\\"")
                                                + "\"}")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                        exchange.sendResponseHeaders(500, error.length);
                        exchange.getResponseBody().write(error);
                    } finally {
                        exchange.close();
                    }
                });

        server.start();
        System.out.println("📡 Webhook 服务已启动，正在监听端口 :48080...");
    }

    private static AgentEngine createEngine(
            LlmProvider llmProvider, String modelName, Session session, ToolRegistry registry) {
        LlmProvider trackedProvider = new CostTracker(llmProvider, modelName, session);
        return new AgentEngine(trackedProvider, registry, false, false);
    }

    private static String chatIdOf(String body, String headerValue) throws Exception {
        if (!isBlank(headerValue)) {
            return headerValue;
        }
        JsonNode root = parseJson(body);
        String chatId = root.path("chat_id").asText("");
        return isBlank(chatId) ? "local-chat" : chatId;
    }

    private static String contentOf(String body) throws Exception {
        if (isBlank(body)) {
            return "";
        }
        JsonNode root = parseJson(body);
        if (!root.isMissingNode()) {
            String prompt = root.path("prompt").asText("");
            if (!isBlank(prompt)) {
                return prompt;
            }
            String text = root.path("text").asText("");
            if (!isBlank(text)) {
                return text;
            }
        }
        return body.trim();
    }

    private static JsonNode parseJson(String body) throws Exception {
        String trimmed = body == null ? "" : body.trim();
        if (!trimmed.startsWith("{")) {
            return MAPPER.missingNode();
        }
        return MAPPER.readTree(trimmed);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
