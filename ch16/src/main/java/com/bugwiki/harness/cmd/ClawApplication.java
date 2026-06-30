package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.context.Session;
import com.bugwiki.harness.context.SessionManager;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.feishu.ApprovalManager;
import com.bugwiki.harness.feishu.ApprovalManager.ApprovalResult;
import com.bugwiki.harness.feishu.FeishuBot;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.EditFileTool;
import com.bugwiki.harness.tools.MiddlewareDecision;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import com.lark.oapi.core.request.EventReq;
import com.lark.oapi.core.response.EventResp;
import com.lark.oapi.event.EventDispatcher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 启动 ch16 飞书审批服务，并用中间件拦截高危工具调用。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        Path workDir = Path.of("").toAbsolutePath().normalize().resolve("workspace");
        LlmProvider provider = new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());

        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, false, false);

        String sessionId = "test_command_intercept_001";
        Session session = SessionManager.GLOBAL.getOrCreate(sessionId, workDir);
        session.append(Message.user(""));

        FeishuBot bot = new FeishuBot(engine, session);
        EventDispatcher dispatcher = bot.getEventDispatcher();

        registry.use(
                call -> {
                    String argsText = String.valueOf(call.getArguments());
                    if (ApprovalManager.GLOBAL.isDangerousCommand(call.getName(), argsText)) {
                        String taskId = call.getId();
                        ApprovalResult result =
                                ApprovalManager.GLOBAL.waitForApproval(
                                        taskId, call.getName(), argsText, bot.reporter());
                        if (!result.allowed()) {
                            return MiddlewareDecision.deny(result.reason());
                        }
                    }
                    return MiddlewareDecision.allow();
                });

        HttpServer server = HttpServer.create(new InetSocketAddress(48080), 0);
        server.createContext("/webhook/event", exchange -> handleEvent(exchange, dispatcher));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("🚀 go-tiny-claw 飞书服务端已启动，正在监听 :48080 端口");
    }

    private static void handleEvent(HttpExchange exchange, EventDispatcher dispatcher) throws IOException {
        EventReq request = new EventReq();
        request.setHttpPath(exchange.getRequestURI().getPath());
        request.setHeaders(headers(exchange));
        request.setBody(exchange.getRequestBody().readAllBytes());
        try {
            EventResp response = dispatcher.handle(request);
            int status = response == null ? 200 : response.getStatusCode();
            if (status == 0) {
                status = 200;
            }
            byte[] body = response == null || response.getBody() == null ? new byte[0] : response.getBody();
            if (response != null && response.getHeaders() != null) {
                response.getHeaders().forEach((key, values) -> exchange.getResponseHeaders().put(key, values));
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } catch (Throwable ex) {
            byte[] body = ("服务器处理事件失败: " + ex.getMessage()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Map<String, List<String>> headers(HttpExchange exchange) {
        return exchange.getRequestHeaders();
    }
}
