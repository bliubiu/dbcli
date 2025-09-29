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

        // 页面
        var root = server.createContext("/", new DashboardHandler());
        root.getFilters().add(mdcFilter);

        // Prometheus 拉取端点
        var metricsScrape = server.createContext("/metrics", new PrometheusMetricsHandler());
        metricsScrape.getFilters().add(mdcFilter);

        // API
        var status = server.createContext("/api/status", new StatusHandler(port));
        status.getFilters().add(mdcFilter);

        var metrics = server.createContext("/api/metrics", new MetricsHandler());
        metrics.getFilters().add(mdcFilter);

        var connTest = server.createContext("/api/connection-test", new ConnectionTestHandler(config));
        connTest.getFilters().add(mdcFilter);

        var encrypt = server.createContext("/api/encrypt-config", new EncryptConfigHandler(config));
        encrypt.getFilters().add(mdcFilter);

        var report = server.createContext("/api/generate-report", new ReportGenerationHandler(config));
        report.getFilters().add(mdcFilter);

        var logs = server.createContext("/api/logs", new RealTimeLogsHandler("logs/dbcli.log"));
        logs.getFilters().add(mdcFilter);

        var configApi = server.createContext("/api/config", new ConfigManagementHandler(config));
        configApi.getFilters().add(mdcFilter);

        // 静态（报告预览）
        var reports = server.createContext("/reports/", new StaticFileHandler(config));
        reports.getFilters().add(mdcFilter);
    }
}