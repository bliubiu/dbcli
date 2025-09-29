package com.dbcli.web;

import com.dbcli.util.TraceContext;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全局 MDC 过滤器：为每个 HTTP 请求建立/传播追踪上下文
 */
public class WebMdcFilter extends Filter {

    @Override
    public String description() {
        return "Sets MDC traceId/spanId/operation for each HTTP request";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        // 读取请求头中的 traceId（若无则生成）
        String headerTraceId = headerFirst(exchange.getRequestHeaders(), "X-Trace-Id");
        if (headerTraceId == null) {
            headerTraceId = headerFirst(exchange.getRequestHeaders(), "X-Request-Id");
        }
        String traceId = headerTraceId != null && !headerTraceId.isEmpty() ? headerTraceId : genTraceId();
        String spanId = genSpanId();

        // operation 使用 METHOD + 空格 + 路径，便于识别
        URI uri = exchange.getRequestURI();
        String operation = exchange.getRequestMethod() + " " + (uri != null ? uri.getPath() : "/");

        // 设置 MDC
        MDC.put(TraceContext.TRACE_ID, traceId);
        MDC.put(TraceContext.SPAN_ID, spanId);
        MDC.put(TraceContext.OPERATION, operation);

        try {
            chain.doFilter(exchange);
        } finally {
            // 请求结束清理，避免线程复用导致脏数据
            MDC.clear();
        }
    }

    private static String headerFirst(Map<String, List<String>> headers, String key) {
        if (headers == null) return null;
        List<String> values = headers.get(key);
        if (values == null || values.isEmpty()) return null;
        return values.get(0);
    }

    // 生成与 TraceContext 相同风格的 ID（16位/8位十六进制）
    private static String genTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String genSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}