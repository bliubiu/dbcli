package com.dbcli.web.handler;

import com.dbcli.web.util.LogReaderUtil;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class RealTimeLogsHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(RealTimeLogsHandler.class);
    
    private final String logFilePath;
    
    public RealTimeLogsHandler(String logFilePath) {
        this.logFilePath = logFilePath;
    }
    
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
        
        try {
            List<String> logLines = LogReaderUtil.readRecentLogs(logFilePath);
            
            // 构建JSON响应
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"logs\": [");
            
            for (int i = 0; i < logLines.size(); i++) {
                if (i > 0) jsonBuilder.append(",");
                jsonBuilder.append("\"")
                           .append(logLines.get(i))
                           .append("\"");
            }
            
            jsonBuilder.append("]}");
            ResponseUtil.sendResponse(exchange, 200, jsonBuilder.toString(), "application/json");
            
        } catch (Exception e) {
            logger.error("读取日志失败", e);
            ResponseUtil.sendResponse(exchange, 500, "{\"error\": \"读取日志失败: " + e.getMessage() + "\"}", "application/json");
        }
    }
}