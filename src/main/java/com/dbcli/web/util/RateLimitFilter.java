package com.dbcli.web.util;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单令牌桶限流过滤器（基于路径）
 * 配置：
 *  -Ddbcli.http.ratelimit.qps / DBCLI_HTTP_RATELIMIT_QPS （默认 20）
 *  -Ddbcli.http.ratelimit.burst / DBCLI_HTTP_RATELIMIT_BURST （默认 40）
 */
public class RateLimitFilter extends Filter {
    private final int qps;
    private final int burst;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        this.qps = getIntConfig("dbcli.http.ratelimit.qps", "DBCLI_HTTP_RATELIMIT_QPS", 20);
        this.burst = getIntConfig("dbcli.http.ratelimit.burst", "DBCLI_HTTP_RATELIMIT_BURST", 40);
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String path = exchange.getHttpContext().getPath();
        TokenBucket bucket = buckets.computeIfAbsent(path, p -> new TokenBucket(qps, burst));
        if (!bucket.tryConsume()) {
            byte[] body = "Too Many Requests".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Retry-After", "1");
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return "Simple token-bucket rate limiter by path";
    }

    private static class TokenBucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int qps, int burst) {
            this.capacity = Math.max(1, burst);
            this.refillPerSecond = Math.max(1, qps);
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSec > 0) {
                tokens = Math.min(capacity, tokens + elapsedSec * refillPerSecond);
                lastRefillNanos = now;
            }
        }
    }

    private static int getIntConfig(String sysProp, String envVar, int defVal) {
        try {
            String v = System.getProperty(sysProp);
            if (v == null || v.isEmpty()) v = System.getenv(envVar);
            if (v == null || v.isEmpty()) return defVal;
            int parsed = Integer.parseInt(v.trim());
            return parsed > 0 ? parsed : defVal;
        } catch (Exception e) {
            return defVal;
        }
    }
}