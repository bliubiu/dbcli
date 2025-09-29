package com.dbcli.web.handler;

import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 系统状态查询处理器
 */
public class StatusHandler implements HttpHandler {

    private final int port;

    public StatusHandler(int port) {
        this.port = port;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ResponseUtil.setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
            return;
        }

        String response = String.format(
            "{\"status\": \"running\", \"port\": %d, \"timestamp\": \"%s\"}",
            port,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        ResponseUtil.sendResponse(exchange, 200, response, "application/json");
    }
}