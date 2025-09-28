package com.dbcli.web.handler;

import com.dbcli.config.AppConfig;
import com.dbcli.config.ConfigLoader;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.service.EncryptionService;
import com.dbcli.service.FastConnectionTestService;
import com.dbcli.database.ConnectionFactory;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConnectionTestHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionTestHandler.class);
    
    // 连接测试频率限制 - 10分钟内只能执行一次
    private static final long CONNECTION_TEST_COOLDOWN = 10 * 60 * 1000; // 10分钟
    private long lastConnectionTestTime = 0;
    
    private final AppConfig config;
    
    public ConnectionTestHandler(AppConfig config) {
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
        
        // 检查频率限制
        long now = System.currentTimeMillis();
        if (now - lastConnectionTestTime < CONNECTION_TEST_COOLDOWN) {
            long remainingTime = (CONNECTION_TEST_COOLDOWN - (now - lastConnectionTestTime)) / 1000 / 60;
            String response = String.format(
                "{\"error\": \"频率限制\", \"message\": \"请等待 %d 分钟后再试\", \"remainingTime\": %d}",
                remainingTime, remainingTime
            );
            ResponseUtil.sendResponse(exchange, 429, response, "application/json");
            return;
        }
        
        try {
            // 重置黑名单
            resetBlacklist();
            
            // 执行连接测试
            ConfigLoader configLoader = new ConfigLoader(new EncryptionService());
            Map<String, DatabaseConfig> databaseConfigs = configLoader.loadDatabaseConfigs(config.getConfigPath());
            
            ConnectionFactory connectionFactory = new ConnectionFactory();
            FastConnectionTestService connectionTestService = new FastConnectionTestService(connectionFactory, config.getConcurrency());
            
            // 构造测试数据结构
            Map<String, Map<String, DatabaseConfig>> testConfigs = new HashMap<>();
            for (Map.Entry<String, DatabaseConfig> entry : databaseConfigs.entrySet()) {
                String systemName = entry.getKey();
                DatabaseConfig dbConfig = entry.getValue();
                
                if (!dbConfig.isEnable()) {
                    continue;
                }
                
                String dbType = dbConfig.getType() != null ? dbConfig.getType() : "unknown";
                // 跳过unknown类型的数据库配置，避免连接测试失败
                if ("unknown".equals(dbType)) {
                    logger.info("跳过未知类型的数据库配置: {}", systemName);
                    continue;
                }
                testConfigs.computeIfAbsent(dbType, k -> new HashMap<>()).put(systemName, dbConfig);
            }
            
            // 执行测试
            boolean testResult = connectionTestService.testConnectionsWithNames(testConfigs);
            
            // 计算成功和失败数量
            int totalEnabled = 0;
            for (Map.Entry<String, Map<String, DatabaseConfig>> entry : testConfigs.entrySet()) {
                totalEnabled += entry.getValue().size();
            }
            
            int failed = connectionTestService.getFailedEncryptedHosts().size();
            int success = Math.max(0, totalEnabled - failed);
            
            // 更新最后测试时间
            lastConnectionTestTime = now;
            
            String response = String.format(
                "{\"success\": %d, \"failed\": %d, \"total\": %d}",
                success, failed, totalEnabled
            );
            ResponseUtil.sendResponse(exchange, 200, response, "application/json");
            
        } catch (Exception e) {
            logger.error("连接测试失败", e);
            String response = String.format(
                "{\"success\": 0, \"failed\": 0, \"error\": \"%s\"}",
                e.getMessage().replace("\"", "\\\"")
            );
            ResponseUtil.sendResponse(exchange, 500, response, "application/json");
        }
    }
    
    private void resetBlacklist() {
        try {
            Path blacklistPath = Paths.get("logs/db_conn_blacklist.txt");
            if (Files.exists(blacklistPath)) {
                Files.delete(blacklistPath);
                logger.info("黑名单文件已重置");
            }
        } catch (IOException e) {
            logger.warn("重置黑名单文件失败: {}", e.getMessage());
        }
    }
    
    // 用于外部重置频率限制（测试用）
    public void resetCooldown() {
        lastConnectionTestTime = 0;
    }
}