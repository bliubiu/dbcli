package com.dbcli.web.handler;

import com.dbcli.config.AppConfig;
import com.dbcli.service.EncryptionService;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class EncryptConfigHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(EncryptConfigHandler.class);
    
    private final AppConfig config;
    
    public EncryptConfigHandler(AppConfig config) {
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
            EncryptionService encryptionService = new EncryptionService();
            encryptionService.encryptConfigs(config.getConfigPath());
            
            String response = "{\"success\": true, \"message\": \"配置文件已成功使用SM4算法加密\"}";
            ResponseUtil.sendResponse(exchange, 200, response, "application/json");
            
        } catch (Exception e) {
            logger.error("配置加密失败", e);
            String response = String.format(
                "{\"success\": false, \"message\": \"%s\"}",
                e.getMessage().replace("\"", "\\\"")
            );
            ResponseUtil.sendResponse(exchange, 500, response, "application/json");
        }
    }
}