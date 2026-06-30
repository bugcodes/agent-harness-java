package com.bugwiki.harness.feishu;

import com.lark.oapi.core.request.EventReq;
import com.lark.oapi.core.response.EventResp;
import com.lark.oapi.event.EventDispatcher;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 暴露飞书事件回调 HTTP 入口，并把请求交给飞书 SDK 事件分发器处理。
 *
 * @author zhaobinjie
 * @date 2026-06-26
 */
public class FeishuEventServer {
    private static final String EVENT_PATH = "/webhook/event";

    private final HttpServer server;
    private final int port;

    public FeishuEventServer(EventDispatcher dispatcher, int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext(EVENT_PATH, exchange -> handleEvent(dispatcher, exchange));
        this.server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
        System.out.printf("🚀 go-tiny-claw 飞书服务端已启动，正在监听 :%d 端口%n", port);
    }

    private void handleEvent(EventDispatcher dispatcher, HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "method not allowed".getBytes(StandardCharsets.UTF_8), Map.of());
            return;
        }

        byte[] body = exchange.getRequestBody().readAllBytes();
        EventReq request = new EventReq();
        request.setHttpPath(exchange.getRequestURI().getPath());
        request.setBody(body);
        request.setHeaders(exchange.getRequestHeaders());

        try {
            EventResp response = dispatcher.handle(request);
            byte[] responseBody = response.getBody() == null ? new byte[0] : response.getBody();
            send(exchange, response.getStatusCode(), responseBody, response.getHeaders());
        } catch (Throwable ex) {
            byte[] responseBody =
                    ("{\"msg\":\"" + ex.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
            send(exchange, 500, responseBody, Map.of("Content-Type", List.of("application/json; charset=utf-8")));
        }
    }

    private void send(
            HttpExchange exchange, int statusCode, byte[] body, Map<String, List<String>> headers)
            throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        if (headers != null) {
            headers.forEach((name, values) -> responseHeaders.put(name, values));
        }
        if (!responseHeaders.containsKey("Content-Type")) {
            responseHeaders.set("Content-Type", "application/json; charset=utf-8");
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
