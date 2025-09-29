package com.dbcli.web.util;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 简单请求体大小限制过滤器
 * - 仅对 POST/PUT 方法生效
 * - 基于 Content-Length 进行快速拦截（无 Content-Length 时放行）
 * 配置：
 *   -Ddbcli.http.maxBodyBytes 或 环境变量 DBCLI_HTTP_MAX_BODY_BYTES（默认 2MB）
 */
public class SizeLimitFilter extends Filter {
    private final long maxBytes;

    public SizeLimitFilter() {
        this.maxBytes = getLongConfig("dbcli.http.maxBodyBytes", "DBCLI_HTTP_MAX_BODY_BYTES", 2L * 1024 * 1024);
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String method = exchange.getRequestMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            List<String> cl = exchange.getRequestHeaders().get("Content-Length");
            if (cl != null && !cl.isEmpty()) {
                try {
                    long len = Long.parseLong(cl.get(0));
                    if (len > maxBytes) {
                        byte[] body = ("Payload Too Large: " + len + " > " + maxBytes).getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                        exchange.sendResponseHeaders(413, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                        return;
                    }
                } catch (NumberFormatException ignore) {
                    // 无法解析时放行
                }
            }
        }
        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return "Limit request body size based on Content-Length";
    }

    private static long getLongConfig(String sysProp, String envVar, long defVal) {
        try {
            String v = System.getProperty(sysProp);
            if (v == null || v.isEmpty()) v = System.getenv(envVar);
            if (v == null || v.isEmpty()) return defVal;
            long parsed = Long.parseLong(v.trim());
            return parsed > 0 ? parsed : defVal;
        } catch (Exception e) {
            return defVal;
        }
    }
}