package com.dbcli.web.handler;

import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * 指标查询处理器（占位实现，可后续接入真实指标源）
 */
public class MetricsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ResponseUtil.setCorsHeaders(exchange);

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        String response = "{\n" +
            "  \"connections\": 5,\n" +
            "  \"avgResponseTime\": 125,\n" +
            "  \"successRate\": 98.5,\n" +
            "  \"errorCount\": 2\n" +
            "}";
        ResponseUtil.sendResponse(exchange, 200, response, "application/json");
    }
}