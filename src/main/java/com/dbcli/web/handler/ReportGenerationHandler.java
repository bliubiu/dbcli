package com.dbcli.web.handler;

import com.dbcli.config.AppConfig;
import com.dbcli.core.DbCliRunner;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ReportGenerationHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationHandler.class);
    
    private final AppConfig config;
    
    public ReportGenerationHandler(AppConfig config) {
        this.config = config;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ResponseUtil.setCorsHeaders(exchange);
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        
        if (!"POST".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
            return;
        }
        
        try {
            // 读取请求体
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String format = "both";
            
            // 解析JSON请求体
            if (requestBody != null && !requestBody.isEmpty()) {
                // 更准确的JSON解析 - 前端发送的是 type 字段
                if (requestBody.contains("\"type\":\"excel\"")) {
                    format = "excel";
                } else if (requestBody.contains("\"type\":\"html\"")) {
                    format = "html";
                } else if (requestBody.contains("\"type\":\"both\"")) {
                    format = "both";
                }
                // 兼容旧的 format 字段
                else if (requestBody.contains("\"format\":\"excel\"")) {
                    format = "excel";
                } else if (requestBody.contains("\"format\":\"html\"")) {
                    format = "html";
                } else if (requestBody.contains("\"format\":\"both\"")) {
                    format = "both";
                }
            }
            
            // 创建报告配置
            AppConfig reportConfig = new AppConfig();
            reportConfig.setConfigPath(config.getConfigPath());
            reportConfig.setMetricsPath(config.getMetricsPath());
            reportConfig.setOutputPath(config.getOutputPath());
            reportConfig.setConcurrency(config.getConcurrency());
            reportConfig.setOutputFormat(format);
            
            // 确保输出目录存在
            Path outputDir = Paths.get(config.getOutputPath());
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            
            // 生成报告
            DbCliRunner runner = new DbCliRunner(reportConfig);
            boolean success = runner.run();
            
            // 更新指标（Prometheus）
            com.dbcli.metrics.MetricsRegistry mr = com.dbcli.metrics.MetricsRegistry.getInstance();
            mr.setGauge("dbcli_report_generation_success", success ? 1 : 0);
            mr.setNowEpochGauge("dbcli_last_report_generated_timestamp_seconds");
            // 报告格式以 label 信息方式编码：dbcli_last_report_format_info{format="excel|html|both"} 1
            java.util.Map<String,String> fmtLabel = java.util.Map.of("format", format);
            mr.setGauge("dbcli_last_report_format_info", 1, fmtLabel);
            // 可选推送
            com.dbcli.metrics.PrometheusPushUtil.pushIfConfigured(mr.renderPrometheus());

            if (success) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String fileName = "";
                String previewUrl = "";
                
                if ("excel".equals(format)) {
                    fileName = "db_report_" + timestamp + ".xlsx";
                    previewUrl = "reports/db_report_" + timestamp + ".xlsx";
                } else if ("html".equals(format)) {
                    fileName = "db_metrics_report_" + timestamp + ".html";
                    previewUrl = "reports/db_metrics_report_" + timestamp + ".html";
                } else {
                    fileName = "db_report_" + timestamp + ".xlsx 和 db_metrics_report_" + timestamp + ".html";
                    previewUrl = "reports/db_metrics_report_" + timestamp + ".html";
                }
                
                String response = String.format(
                    "{\"success\": true, \"message\": \"报告已成功生成到 %s 目录\", \"fileName\": \"%s\", \"previewUrl\": \"%s\"}",
                    config.getOutputPath(), fileName, previewUrl
                );
                ResponseUtil.sendResponse(exchange, 200, response, "application/json");
            } else {
                ResponseUtil.sendResponse(exchange, 500, "{\"success\": false, \"message\": \"报告生成失败，请检查日志了解详细信息\"}", "application/json");
            }
            
        } catch (Exception e) {
            logger.error("报告生成失败", e);
            String response = String.format(
                "{\"success\": false, \"message\": \"报告生成失败: %s\"}",
                e.getMessage().replace("\"", "\\\"")
            );
            ResponseUtil.sendResponse(exchange, 500, response, "application/json");
        }
    }
}