package com.dbcli.web.handler;

import com.dbcli.config.AppConfig;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);
    
    private final AppConfig config;
    
    public StaticFileHandler(AppConfig config) {
        this.config = config;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ResponseUtil.setCorsHeaders(exchange);
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        
        String requestPath = exchange.getRequestURI().getPath();
        logger.info("请求静态文件: {}", requestPath);
        
        // 移除 /reports/ 前缀，获取实际文件路径
        String filePath = requestPath.substring("/reports/".length());
        Path reportFile = Paths.get("reports", filePath);
        
        logger.info("查找报告文件: {}", reportFile.toAbsolutePath());
        
        if (!Files.exists(reportFile)) {
            logger.warn("报告文件不存在: {}", reportFile.toAbsolutePath());
            String notFoundResponse = "<!DOCTYPE html><html><head><title>文件未找到</title></head><body>" +
                "<h1>404 - 文件未找到</h1>" +
                "<p>请求的报告文件不存在: " + filePath + "</p>" +
                "<p>请先生成报告，然后再访问。</p>" +
                "<a href=\"/\">返回主页</a>" +
                "</body></html>";
            ResponseUtil.sendResponse(exchange, 404, notFoundResponse, "text/html");
            return;
        }
        
        try {
            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(reportFile);
            
            // 根据文件扩展名确定Content-Type
            String contentType = getContentType(filePath);
            
            logger.info("成功读取报告文件: {}, 大小: {} bytes, 类型: {}", 
                reportFile.getFileName(), fileContent.length, contentType);
            
            // 设置响应头
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            ResponseUtil.setCorsHeaders(exchange);
            
            // 发送文件内容
            exchange.sendResponseHeaders(200, fileContent.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileContent);
            }
            
        } catch (IOException e) {
            logger.error("读取报告文件失败: {}", e.getMessage(), e);
            String errorResponse = "<!DOCTYPE html><html><head><title>读取错误</title></head><body>" +
                "<h1>500 - 服务器错误</h1>" +
                "<p>读取报告文件时发生错误: " + e.getMessage() + "</p>" +
                "<a href=\"/\">返回主页</a>" +
                "</body></html>";
            ResponseUtil.sendResponse(exchange, 500, errorResponse, "text/html");
        }
    }
    
    private String getContentType(String filePath) {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) {
            return "text/html";
        } else if (lowerPath.endsWith(".css")) {
            return "text/css";
        } else if (lowerPath.endsWith(".js")) {
            return "application/javascript";
        } else if (lowerPath.endsWith(".json")) {
            return "application/json";
        } else if (lowerPath.endsWith(".xml")) {
            return "application/xml";
        } else if (lowerPath.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lowerPath.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else {
            return "text/plain";
        }
    }
}