package com.dbcli.web;

import com.dbcli.config.AppConfig;
import com.dbcli.web.handler.ConnectionTestHandler;
import com.dbcli.web.handler.EncryptConfigHandler;
import com.dbcli.web.handler.ReportGenerationHandler;
import com.dbcli.web.handler.ConfigManagementHandler;
import com.dbcli.web.handler.RealTimeLogsHandler;
import com.dbcli.web.handler.StaticFileHandler;
import com.dbcli.web.util.HtmlGeneratorUtil;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 增强版Web管理服务器
 * 包含完整的数据库连接测试、配置加密、报告生成和实时日志功能
 */
public class EnhancedWebServer {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedWebServer.class);
    
    private HttpServer server;
    private final int port;
    private final AppConfig config;
    private volatile boolean running = false;
    
    public EnhancedWebServer(AppConfig config) {
        this.port = config.getWebPort() > 0 ? config.getWebPort() : 8080;
        this.config = config;
    }
    
    public void start() throws IOException {
        if (running) {
            logger.warn("Web管理服务器已经在运行中");
            return;
        }
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册处理器
        server.createContext("/", new DashboardHandler());
        server.createContext("/api/connection-test", new ConnectionTestHandler(config));
        server.createContext("/api/encrypt-config", new EncryptConfigHandler(config));
        server.createContext("/api/generate-report", new ReportGenerationHandler(config));
        server.createContext("/api/logs", new RealTimeLogsHandler("logs/dbcli.log"));
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/config", new ConfigManagementHandler(config));
        server.createContext("/reports/", new StaticFileHandler(config));
        
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        
        running = true;
        logger.info("增强版Web管理服务器已启动，访问地址: http://localhost:{}", port);
    }
    
    public void stop() {
        if (server != null && running) {
            server.stop(2);
            running = false;
            logger.info("Web管理服务器已停止");
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getPort() {
        return port;
    }
    
    public void waitForShutdown() {
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 仪表板处理器 - 提供完整的Web管理界面
     */
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = HtmlGeneratorUtil.generateEnhancedDashboardHtml();
            ResponseUtil.sendResponse(exchange, 200, response, "text/html");
        }
    }
    
    /**
     * 状态处理器
     */
    private class StatusHandler implements HttpHandler {
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
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            ResponseUtil.sendResponse(exchange, 200, response, "application/json");
        }
    }
}