package com.dbcli.web.handler;

import com.dbcli.metrics.MetricsRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Prometheus 抓取端点：/metrics
 * 输出 text/plain; version=0.0.4
 */
public class PrometheusMetricsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 仅 GET
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        byte[] body = MetricsRegistry.getInstance().renderPrometheus().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (var os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}