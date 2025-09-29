package com.dbcli.web;

import com.dbcli.config.AppConfig;
import com.dbcli.web.handler.*;
import com.sun.net.httpserver.HttpServer;

import java.util.Objects;

/**
 * 统一路由注册器：集中管理所有 Web 路由到对应 Handler
 */
public final class RouteRegistrar {

    private RouteRegistrar() {}

    public static void register(HttpServer server, AppConfig config, int port) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(config, "config");

        WebMdcFilter mdcFilter = new WebMdcFilter();
        com.dbcli.web.util.SizeLimitFilter sizeLimit = new com.dbcli.web.util.SizeLimitFilter();
        com.dbcli.web.util.RateLimitFilter rateLimit = new com.dbcli.web.util.RateLimitFilter();

        // 页面
        var root = server.createContext("/", new DashboardHandler());
        root.getFilters().add(mdcFilter);

        // Prometheus 拉取端点
        var metricsScrape = server.createContext("/metrics", new PrometheusMetricsHandler());
        metricsScrape.getFilters().add(mdcFilter);
        metricsScrape.getFilters().add(rateLimit);

        // API
        var status = server.createContext("/api/status", new StatusHandler(port));
        status.getFilters().add(mdcFilter);
        // 可选：状态接口一般不限制体积与频率，这里仅保留 MDC

        var metrics = server.createContext("/api/metrics", new MetricsHandler());
        metrics.getFilters().add(mdcFilter);
        // 读接口，通常无需 SizeLimit；如访问频繁，可按需添加 rateLimit

        var connTest = server.createContext("/api/connection-test", new ConnectionTestHandler(config));
        connTest.getFilters().add(mdcFilter);
        connTest.getFilters().add(sizeLimit);

        var encrypt = server.createContext("/api/encrypt-config", new EncryptConfigHandler(config));
        encrypt.getFilters().add(mdcFilter);
        encrypt.getFilters().add(sizeLimit);

        var report = server.createContext("/api/generate-report", new ReportGenerationHandler(config));
        report.getFilters().add(mdcFilter);
        report.getFilters().add(sizeLimit);

        var logs = server.createContext("/api/logs", new RealTimeLogsHandler("logs/dbcli.log"));
        logs.getFilters().add(mdcFilter);
        logs.getFilters().add(rateLimit);

        var configApi = server.createContext("/api/config", new ConfigManagementHandler(config));
        configApi.getFilters().add(mdcFilter);
        configApi.getFilters().add(sizeLimit);

        // 静态（报告预览）
        var reports = server.createContext("/reports/", new StaticFileHandler(config));
        reports.getFilters().add(mdcFilter);
    }
}