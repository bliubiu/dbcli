package com.dbcli.web;

import com.dbcli.config.AppConfig;
import com.dbcli.config.ConfigLoader;
import com.dbcli.core.DbCliRunner;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.service.EncryptionService;
import com.dbcli.service.FastConnectionTestService;
import com.dbcli.database.ConnectionFactory;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
    
    // 连接测试频率限制 - 10分钟内只能执行一次
    private static final long CONNECTION_TEST_COOLDOWN = 10 * 60 * 1000; // 10分钟
    private long lastConnectionTestTime = 0;
    
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
        server.createContext("/api/connection-test", new ConnectionTestHandler());
        server.createContext("/api/encrypt-config", new EncryptConfigHandler());
        server.createContext("/api/generate-report", new ReportGenerationHandler());
        server.createContext("/api/logs", new RealTimeLogsHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/config", new ConfigManagementHandler());
        server.createContext("/reports/", new StaticFileHandler());
        
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
     * 设置CORS头
     */
    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    
    /**
     * 发送HTTP响应
     */
    private static void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * 数据库连接测试处理器
     */
    private class ConnectionTestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
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
                sendResponse(exchange, 429, response, "application/json");
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
                sendResponse(exchange, 200, response, "application/json");
                
            } catch (Exception e) {
                logger.error("连接测试失败", e);
                String response = String.format(
                    "{\"success\": 0, \"failed\": 0, \"error\": \"%s\"}",
                    e.getMessage().replace("\"", "\\\"")
                );
                sendResponse(exchange, 500, response, "application/json");
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
    }
    
    /**
     * 配置加密处理器
     */
    private class EncryptConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
                return;
            }
            
            try {
                EncryptionService encryptionService = new EncryptionService();
                encryptionService.encryptConfigs(config.getConfigPath());
                
                String response = "{\"success\": true, \"message\": \"配置文件已成功使用SM4算法加密\"}";
                sendResponse(exchange, 200, response, "application/json");
                
            } catch (Exception e) {
                logger.error("配置加密失败", e);
                String response = String.format(
                    "{\"success\": false, \"message\": \"%s\"}",
                    e.getMessage().replace("\"", "\\\"")
                );
                sendResponse(exchange, 500, response, "application/json");
            }
        }
    }
    
    /**
     * 报告生成处理器
     */
    private class ReportGenerationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
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
                    sendResponse(exchange, 200, response, "application/json");
                } else {
                    sendResponse(exchange, 500, "{\"success\": false, \"message\": \"报告生成失败，请检查日志了解详细信息\"}", "application/json");
                }
                
            } catch (Exception e) {
                logger.error("报告生成失败", e);
                String response = String.format(
                    "{\"success\": false, \"message\": \"报告生成失败: %s\"}",
                    e.getMessage().replace("\"", "\\\"")
                );
                sendResponse(exchange, 500, response, "application/json");
            }
        }
    }
    
    /**
     * 实时日志处理器
     */
    private class RealTimeLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
                return;
            }
            
            try {
                List<String> logLines = readRecentLogs();
                
                // 构建JSON响应
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{\"logs\": [");
                
                for (int i = 0; i < logLines.size(); i++) {
                    if (i > 0) jsonBuilder.append(",");
                    jsonBuilder.append("\"")
                               .append(logLines.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                               .append("\"");
                }
                
                jsonBuilder.append("]}");
                sendResponse(exchange, 200, jsonBuilder.toString(), "application/json");
                
            } catch (Exception e) {
                logger.error("读取日志失败", e);
                sendResponse(exchange, 500, "{\"error\": \"读取日志失败\"}", "application/json");
            }
        }
        
        private List<String> readRecentLogs() throws IOException {
            Path logFile = Paths.get("logs/application.log");
            if (!Files.exists(logFile)) {
                return Arrays.asList("日志文件不存在");
            }
            
            List<String> lines = new ArrayList<>();
            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                long fileLength = raf.length();
                long startPosition = Math.max(0, fileLength - 20480); // 读取最后20KB
                
                raf.seek(startPosition);
                String line;
                while ((line = raf.readLine()) != null) {
                    // RandomAccessFile读取的是ISO-8859-1编码，需要转换为UTF-8
                    lines.add(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
                }
                
                // 只返回最后100行
                int start = Math.max(0, lines.size() - 100);
                return lines.subList(start, lines.size());
            }
        }
    }
    
    /**
     * 状态处理器
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
                return;
            }
            
            String response = String.format(
                "{\"status\": \"running\", \"port\": %d, \"timestamp\": \"%s\"}",
                port,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            sendResponse(exchange, 200, response, "application/json");
        }
    }
    
    /**
     * 配置管理处理器 - 用于查看、编辑和更新数据库配置文件
     */
    private class ConfigManagementHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String requestMethod = exchange.getRequestMethod();
            String requestPath = exchange.getRequestURI().getPath();
            
            try {
                if ("GET".equals(requestMethod)) {
                    // 获取配置文件列表或特定配置文件内容
                    handleGetConfig(exchange, requestPath);
                } else if ("POST".equals(requestMethod) || "PUT".equals(requestMethod)) {
                    // 更新配置文件
                    handleUpdateConfig(exchange, requestPath);
                } else if ("DELETE".equals(requestMethod)) {
                    // 删除配置文件
                    handleDeleteConfig(exchange, requestPath);
                } else {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
                }
            } catch (Exception e) {
                logger.error("配置管理处理失败", e);
                String response = String.format(
                    "{\"success\": false, \"message\": \"%s\"}",
                    e.getMessage().replace("\"", "\\\"")
                );
                sendResponse(exchange, 500, response, "application/json");
            }
        }
        
        private void handleGetConfig(HttpExchange exchange, String requestPath) throws IOException {
            String configPath = config.getConfigPath();
            String metricsPath = config.getMetricsPath();
            Path configDir = Paths.get(configPath);
            Path metricsDir = Paths.get(metricsPath);
            
            if (!Files.exists(configDir) && !Files.exists(metricsDir)) {
                sendResponse(exchange, 404, "{\"error\": \"Config and metrics directories not found\"}", "application/json");
                return;
            }
            
            // 如果请求路径是 /api/config/，返回配置文件列表（包括数据库配置和指标配置）
            if ("/api/config".equals(requestPath) || "/api/config/".equals(requestPath)) {
                List<Map<String, Object>> configFiles = new ArrayList<>();
                
                // 添加数据库配置文件
                if (Files.exists(configDir)) {
                    try (var paths = Files.walk(configDir)) {
                        paths.filter(path -> {
                            String fileName = path.getFileName().toString().toLowerCase();
                            return fileName.endsWith("-config.yml") || fileName.endsWith("-config.yaml");
                        }).forEach(path -> {
                            try {
                                Map<String, Object> fileInfo = new HashMap<>();
                                fileInfo.put("name", path.getFileName().toString());
                                fileInfo.put("path", configDir.relativize(path).toString());
                                fileInfo.put("type", "database");
                                fileInfo.put("size", Files.size(path));
                                fileInfo.put("lastModified", Files.getLastModifiedTime(path).toString());
                                configFiles.add(fileInfo);
                            } catch (IOException e) {
                                logger.warn("读取配置文件信息失败: {}", path, e);
                            }
                        });
                    } catch (IOException e) {
                        logger.error("遍历配置目录失败", e);
                    }
                }
                
                // 添加指标配置文件
                if (Files.exists(metricsDir)) {
                    try (var paths = Files.walk(metricsDir)) {
                        paths.filter(path -> {
                            String fileName = path.getFileName().toString().toLowerCase();
                            return fileName.endsWith("-metrics.yml") || fileName.endsWith("-metrics.yaml");
                        }).forEach(path -> {
                            try {
                                Map<String, Object> fileInfo = new HashMap<>();
                                fileInfo.put("name", path.getFileName().toString());
                                fileInfo.put("path", metricsDir.relativize(path).toString());
                                fileInfo.put("type", "metrics");
                                fileInfo.put("size", Files.size(path));
                                fileInfo.put("lastModified", Files.getLastModifiedTime(path).toString());
                                configFiles.add(fileInfo);
                            } catch (IOException e) {
                                logger.warn("读取指标配置文件信息失败: {}", path, e);
                            }
                        });
                    } catch (IOException e) {
                        logger.error("遍历指标配置目录失败", e);
                    }
                }
                
                String response = toJson(configFiles);
                sendResponse(exchange, 200, response, "application/json");
            } else {
                // 返回特定配置文件的内容
                String fileName = requestPath.substring("/api/config/".length());
                
                // 先在数据库配置目录中查找
                Path configFile = configDir.resolve(fileName);
                if (!Files.exists(configFile)) {
                    // 如果在数据库配置目录中找不到，则在指标配置目录中查找
                    configFile = metricsDir.resolve(fileName);
                }
                
                if (!Files.exists(configFile)) {
                    sendResponse(exchange, 404, "{\"error\": \"Config file not found\"}", "application/json");
                    return;
                }
                
                try {
                    String content = Files.readString(configFile, StandardCharsets.UTF_8);
                    Map<String, Object> result = new HashMap<>();
                    result.put("name", fileName);
                    result.put("content", content);
                    String response = toJson(result);
                    logger.info("返回配置文件内容: {} (大小: {} 字符)", fileName, content.length());
                    sendResponse(exchange, 200, response, "application/json");
                } catch (IOException e) {
                    logger.error("读取配置文件失败: {}", configFile, e);
                    String response = String.format(
                        "{\"error\": \"Failed to read config file: %s\"}",
                        e.getMessage().replace("\"", "\\\"")
                    );
                    sendResponse(exchange, 500, response, "application/json");
                }
            }
        }
        
        private void handleUpdateConfig(HttpExchange exchange, String requestPath) throws IOException {
            String configPath = config.getConfigPath();
            String metricsPath = config.getMetricsPath();
            Path configDir = Paths.get(configPath);
            Path metricsDir = Paths.get(metricsPath);
            
            if (!Files.exists(configDir) && !Files.exists(metricsDir)) {
                sendResponse(exchange, 404, "{\"error\": \"Config and metrics directories not found\"}", "application/json");
                return;
            }
            
            // 读取请求体
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            logger.info("收到配置更新请求: {}", requestBody);
            
            Map<String, Object> requestData = parseJson(requestBody);
            
            // 支持多种参数名格式
            String fileName = (String) requestData.get("fileName");
            if (fileName == null) {
                fileName = (String) requestData.get("name");
            }
            
            String content = (String) requestData.get("content");
            if (content == null) {
                content = (String) requestData.get("data");
            }
            
            String fileType = (String) requestData.get("type"); // "database" or "metrics"
            
            if (fileName == null || content == null) {
                logger.warn("缺少必要参数 - fileName: {}, content: {}", fileName != null, content != null);
                sendResponse(exchange, 400, "{\"error\": \"Missing fileName or content\"}", "application/json");
                return;
            }
            
            // 如果没有指定文件类型，根据文件名推断
            if (fileType == null) {
                if (fileName.toLowerCase().contains("metrics")) {
                    fileType = "metrics";
                } else {
                    fileType = "database";
                }
            }
            
            // 根据文件类型确定保存目录
            Path configFile;
            if ("metrics".equals(fileType)) {
                // 确保指标配置目录存在
                if (!Files.exists(metricsDir)) {
                    Files.createDirectories(metricsDir);
                }
                configFile = metricsDir.resolve(fileName);
            } else {
                // 确保数据库配置目录存在
                if (!Files.exists(configDir)) {
                    Files.createDirectories(configDir);
                }
                configFile = configDir.resolve(fileName);
            }
            
            try {
                // 确保父目录存在
                Files.createDirectories(configFile.getParent());
                
                // 处理换行符：将 \n 字符串转换为真正的换行符
                String processedContent = content.replace("\\n", "\n")
                                               .replace("\\r", "\r")
                                               .replace("\\t", "\t");
                
                // 写入文件
                Files.writeString(configFile, processedContent, StandardCharsets.UTF_8);
                logger.info("配置文件保存成功: {} (类型: {})", configFile.toAbsolutePath(), fileType);
                
                String response = "{\"success\": true, \"message\": \"配置文件更新成功\"}";
                sendResponse(exchange, 200, response, "application/json");
            } catch (IOException e) {
                logger.error("写入配置文件失败: {}", configFile, e);
                String response = String.format(
                    "{\"success\": false, \"message\": \"Failed to write config file: %s\"}",
                    e.getMessage().replace("\"", "\\\"")
                );
                sendResponse(exchange, 500, response, "application/json");
            }
        }
        
        private void handleDeleteConfig(HttpExchange exchange, String requestPath) throws IOException {
            String configPath = config.getConfigPath();
            String metricsPath = config.getMetricsPath();
            Path configDir = Paths.get(configPath);
            Path metricsDir = Paths.get(metricsPath);
            
            if (!Files.exists(configDir) && !Files.exists(metricsDir)) {
                sendResponse(exchange, 404, "{\"error\": \"Config and metrics directories not found\"}", "application/json");
                return;
            }
            
            String fileName = requestPath.substring("/api/config/".length());
            
            // 先在数据库配置目录中查找
            Path configFile = configDir.resolve(fileName);
            if (!Files.exists(configFile)) {
                // 如果在数据库配置目录中找不到，则在指标配置目录中查找
                configFile = metricsDir.resolve(fileName);
            }
            
            if (!Files.exists(configFile)) {
                sendResponse(exchange, 404, "{\"error\": \"Config file not found\"}", "application/json");
                return;
            }
            
            try {
                Files.delete(configFile);
                String response = "{\"success\": true, \"message\": \"配置文件删除成功\"}";
                sendResponse(exchange, 200, response, "application/json");
            } catch (IOException e) {
                logger.error("删除配置文件失败: {}", configFile, e);
                String response = String.format(
                    "{\"success\": false, \"message\": \"Failed to delete config file: %s\"}",
                    e.getMessage().replace("\"", "\\\"")
                );
                sendResponse(exchange, 500, response, "application/json");
            }
        }
        
        private Map<String, Object> parseJson(String json) {
            // 改进的JSON解析实现
            Map<String, Object> result = new HashMap<>();
            try {
                // 移除首尾的大括号和空白字符
                String content = json.trim();
                if (content.startsWith("{") && content.endsWith("}")) {
                    content = content.substring(1, content.length() - 1).trim();
                }
                
                // 使用更智能的方式解析JSON
                StringBuilder currentKey = new StringBuilder();
                StringBuilder currentValue = new StringBuilder();
                boolean inQuotes = false;
                boolean inKey = true;
                boolean escapeNext = false;
                int braceLevel = 0;
                
                for (int i = 0; i < content.length(); i++) {
                    char c = content.charAt(i);
                    
                    if (escapeNext) {
                        if (inKey) {
                            currentKey.append(c);
                        } else {
                            currentValue.append(c);
                        }
                        escapeNext = false;
                        continue;
                    }
                    
                    if (c == '\\') {
                        escapeNext = true;
                        if (inKey) {
                            currentKey.append(c);
                        } else {
                            currentValue.append(c);
                        }
                        continue;
                    }
                    
                    if (c == '"' && (i == 0 || content.charAt(i-1) != '\\')) {
                        inQuotes = !inQuotes;
                        continue;
                    }
                    
                    if (!inQuotes) {
                        if (c == '{') {
                            braceLevel++;
                        } else if (c == '}') {
                            braceLevel--;
                        } else if (c == ':' && braceLevel == 0 && inKey) {
                            inKey = false;
                            continue;
                        } else if (c == ',' && braceLevel == 0) {
                            // 保存当前键值对
                            String key = currentKey.toString().trim();
                            String value = currentValue.toString().trim();
                            if (!key.isEmpty()) {
                                result.put(key, value);
                            }
                            // 重置状态
                            currentKey.setLength(0);
                            currentValue.setLength(0);
                            inKey = true;
                            continue;
                        }
                    }
                    
                    // 跳过非引号内的空白字符
                    if (!inQuotes && Character.isWhitespace(c)) {
                        continue;
                    }
                    
                    if (inKey) {
                        currentKey.append(c);
                    } else {
                        currentValue.append(c);
                    }
                }
                
                // 处理最后一个键值对
                String key = currentKey.toString().trim();
                String value = currentValue.toString().trim();
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
                
                logger.debug("JSON解析结果: {}", result);
                
            } catch (Exception e) {
                logger.warn("JSON解析失败: {}, 原始JSON: {}", e.getMessage(), json);
                // 降级处理：尝试简单的分割方式
                try {
                    String content = json.trim();
                    if (content.startsWith("{") && content.endsWith("}")) {
                        content = content.substring(1, content.length() - 1);
                    }
                    
                    String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    for (String pair : pairs) {
                        String[] keyValue = pair.split(":", 2);
                        if (keyValue.length == 2) {
                            String key = keyValue[0].trim().replaceAll("^\"|\"$", "");
                            String value = keyValue[1].trim().replaceAll("^\"|\"$", "");
                            result.put(key, value);
                        }
                    }
                } catch (Exception fallbackException) {
                    logger.error("JSON解析完全失败: {}", fallbackException.getMessage());
                }
            }
            return result;
        }
        
        private String toJson(Object obj) {
            if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(toJson(list.get(i)));
                }
                sb.append("]");
                return sb.toString();
            } else if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                StringBuilder sb = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJsonString(entry.getKey().toString())).append("\":");
                    sb.append(toJson(entry.getValue()));
                    i++;
                }
                sb.append("}");
                return sb.toString();
            } else if (obj instanceof String) {
                return "\"" + escapeJsonString(obj.toString()) + "\"";
            } else {
                return obj != null ? obj.toString() : "null";
            }
        }
        
        private String escapeJsonString(String str) {
            if (str == null) return "";
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    default:
                        if (c < 0x20 || c > 0x7E) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                        break;
                }
            }
            return sb.toString();
        }
    }
    
    /**
     * 静态文件处理器 - 用于提供HTML报告等静态文件
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
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
                sendResponse(exchange, 404, notFoundResponse, "text/html");
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
                setCorsHeaders(exchange);
                
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
                sendResponse(exchange, 500, errorResponse, "text/html");
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
    
    /**
     * 仪表板处理器 - 提供完整的Web管理界面
     */
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = generateEnhancedDashboardHtml();
            sendResponse(exchange, 200, response, "text/html");
        }
        
        private String generateEnhancedDashboardHtml() {
            return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>DBCLI 增强版管理控制台</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f7fa; }\n" +
                "        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 2rem; text-align: center; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }\n" +
                "        .header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; }\n" +
                "        .header p { font-size: 1.1rem; opacity: 0.9; }\n" +
                "        .container { max-width: 1400px; margin: 2rem auto; padding: 0 1rem; }\n" +
                "        .card { background: white; border-radius: 12px; padding: 2rem; margin-bottom: 2rem; box-shadow: 0 4px 6px rgba(0,0,0,0.07); border: 1px solid #e1e8ed; }\n" +
                "        .card h3 { color: #2c3e50; margin-bottom: 1.5rem; font-size: 1.4rem; display: flex; align-items: center; }\n" +
                "        .card h3::before { content: attr(data-icon); margin-right: 0.5rem; font-size: 1.6rem; }\n" +
                "        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 2rem; }\n" +
                "        .btn { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; padding: 0.8rem 1.5rem; border-radius: 8px; cursor: pointer; margin: 0.5rem; font-size: 1rem; transition: all 0.3s ease; }\n" +
                "        .btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }\n" +
                "        .btn:disabled { background: #bdc3c7; cursor: not-allowed; transform: none; }\n" +
                "        .btn-success { background: linear-gradient(135deg, #56ab2f 0%, #a8e6cf 100%); }\n" +
                "        .btn-warning { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }\n" +
                "        .btn-info { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }\n" +
                "        .btn-secondary { background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%); color: #333; }\n" +
                "        .status-indicator { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 8px; }\n" +
                "        .status-online { background: #27ae60; }\n" +
                "        .status-offline { background: #e74c3c; }\n" +
                "        .status-warning { background: #f39c12; }\n" +
                "        .log-container { background: #2c3e50; color: #ecf0f1; padding: 1.5rem; border-radius: 8px; font-family: 'Consolas', 'Monaco', monospace; max-height: 400px; overflow-y: auto; font-size: 0.9rem; line-height: 1.4; }\n" +
                "        .log-line { margin-bottom: 4px; white-space: pre-wrap; }\n" +
                "        .log-error { color: #e74c3c; }\n" +
                "        .log-warn { color: #f39c12; }\n" +
                "        .log-info { color: #3498db; }\n" +
                "        .log-success { color: #27ae60; }\n" +
                "        .button-group { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 1rem; }\n" +
                "        .metric-card { text-align: center; padding: 1.5rem; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; border-radius: 12px; }\n" +
                "        .metric-value { font-size: 2.5rem; font-weight: bold; margin-bottom: 0.5rem; }\n" +
                "        .metric-label { font-size: 1rem; opacity: 0.9; }\n" +
                "        .progress-bar { width: 100%; height: 8px; background: #ecf0f1; border-radius: 4px; overflow: hidden; margin: 1rem 0; }\n" +
                "        .progress-fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); transition: width 0.3s ease; }\n" +
                "        .alert { padding: 1rem; border-radius: 8px; margin: 1rem 0; }\n" +
                "        .alert-info { background: #d1ecf1; color: #0c5460; border-left: 4px solid #17a2b8; }\n" +
                "        .alert-success { background: #d4edda; color: #155724; border-left: 4px solid #28a745; }\n" +
                "        .alert-warning { background: #fff3cd; color: #856404; border-left: 4px solid #ffc107; }\n" +
                "        .alert-error { background: #f8d7da; color: #721c24; border-left: 4px solid #dc3545; }\n" +
                "        .loading { display: none; }\n" +
                "        .loading.show { display: inline-block; }\n" +
                "        .spinner { display: inline-block; width: 20px; height: 20px; border: 3px solid rgba(255,255,255,.3); border-radius: 50%; border-top-color: #fff; animation: spin 1s ease-in-out infinite; }\n" +
                "        @keyframes spin { to { transform: rotate(360deg); } }\n" +
                "        .cooldown-info { background: #fff3cd; color: #856404; padding: 1rem; border-radius: 8px; margin: 1rem 0; border-left: 4px solid #ffc107; }\n" +
                "        .config-list { margin-top: 1rem; }\n" +
                "        .config-item { display: flex; justify-content: space-between; align-items: center; padding: 0.8rem; border: 1px solid #eee; border-radius: 6px; margin-bottom: 0.5rem; }\n" +
                "        .config-item:hover { background: #f8f9fa; }\n" +
                "        .config-actions { display: flex; gap: 0.5rem; }\n" +
                "        .modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); }\n" +
                "        .modal-content { background-color: white; margin: 5% auto; padding: 2rem; border-radius: 12px; width: 80%; max-width: 800px; max-height: 80vh; overflow-y: auto; }\n" +
                "        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor: pointer; }\n" +
                "        .close:hover { color: black; }\n" +
                "        .form-group { margin-bottom: 1rem; }\n" +
                "        .form-group label { display: block; margin-bottom: 0.5rem; font-weight: bold; }\n" +
                "        .form-group textarea { width: 100%; min-height: 300px; font-family: 'Consolas', 'Monaco', monospace; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; }\n" +
                "        .tabs { display: flex; margin-bottom: 1rem; border-bottom: 1px solid #eee; }\n" +
                "        .tab { padding: 1rem 2rem; cursor: pointer; border-bottom: 3px solid transparent; }\n" +
                "        .tab.active { border-bottom: 3px solid #667eea; color: #667eea; font-weight: bold; }\n" +
                "        .tab-content { display: none; }\n" +
                "        .tab-content.active { display: block; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <h1>🚀 DBCLI 增强版管理控制台</h1>\n" +
                "        <p>数据库连接性能测试工具 - 完整功能Web管理界面</p>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"tabs\">\n" +
                "            <div class=\"tab active\" data-tab=\"dashboard\">仪表板</div>\n" +
                "            <div class=\"tab\" data-tab=\"config\">配置管理</div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div id=\"dashboard-tab\" class=\"tab-content active\">\n" +
                "            <div class=\"grid\">\n" +
                "                <div class=\"card\">\n" +
                "                    <h3 data-icon=\"📊\">系统状态监控</h3>\n" +
                "                    <div class=\"metric-card\">\n" +
                "                        <div class=\"metric-value\" id=\"systemStatus\">\n" +
                "                            <span class=\"status-indicator status-online\"></span>运行中\n" +
                "                        </div>\n" +
                "                        <div class=\"metric-label\">系统运行状态</div>\n" +
                "                    </div>\n" +
                "                    <div class=\"progress-bar\">\n" +
                "                        <div class=\"progress-fill\" style=\"width: 85%\"></div>\n" +
                "                    </div>\n" +
                "                    <p>系统健康度: 85%</p>\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"card\">\n" +
                "                    <h3 data-icon=\"🔗\">数据库连接测试</h3>\n" +
                "                    <div class=\"alert alert-info\">\n" +
                "                        <strong>功能说明:</strong> 测试所有配置的数据库连接，自动重置黑名单，支持频率限制保护。\n" +
                "                    </div>\n" +
                "                    <div id=\"connectionTestResult\"></div>\n" +
                "                    <div class=\"button-group\">\n" +
                "                        <button class=\"btn btn-warning\" onclick=\"testDatabaseConnections()\" id=\"testConnectionBtn\">\n" +
                "                            <span class=\"loading\" id=\"testLoading\"><span class=\"spinner\"></span></span>\n" +
                "                            🔍 测试数据库连接\n" +
                "                        </button>\n" +
                "                    </div>\n" +
                "                    <div id=\"cooldownInfo\" class=\"cooldown-info\" style=\"display: none;\"></div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"grid\">\n" +
                "                <div class=\"card\">\n" +
                "                    <h3 data-icon=\"🔐\">配置文件加密</h3>\n" +
                "                    <div class=\"alert alert-info\">\n" +
                "                        <strong>安全提示:</strong> 使用SM4算法加密数据库配置文件中的敏感信息，确保密码安全。\n" +
                "                    </div>\n" +
                "                    <div id=\"encryptResult\"></div>\n" +
                "                    <div class=\"button-group\">\n" +
                "                        <button class=\"btn btn-success\" onclick=\"encryptConfigurations()\" id=\"encryptBtn\">\n" +
                "                            <span class=\"loading\" id=\"encryptLoading\"><span class=\"spinner\"></span></span>\n" +
                "                            🔒 加密配置文件\n" +
                "                        </button>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "                \n" +
                "                <div class=\"card\">\n" +
                "                    <h3 data-icon=\"📈\">报告生成</h3>\n" +
                "                    <div class=\"alert alert-info\">\n" +
                "                        <strong>报告类型:</strong> 支持Excel和HTML格式报告，包含详细的数据库性能指标和分析。\n" +
                "                    </div>\n" +
                "                    <div id=\"reportResult\"></div>\n" +
                "                    <div class=\"button-group\">\n" +
                "                        <button class=\"btn btn-info\" onclick=\"generateReport('excel')\" id=\"excelBtn\">\n" +
                "                            <span class=\"loading\" id=\"excelLoading\"><span class=\"spinner\"></span></span>\n" +
                "                            📊 生成Excel报告\n" +
                "                        </button>\n" +
                "                        <button class=\"btn btn-info\" onclick=\"generateReport('html')\" id=\"htmlBtn\">\n" +
                "                            <span class=\"loading\" id=\"htmlLoading\"><span class=\"spinner\"></span></span>\n" +
                "                            🌐 生成HTML报告\n" +
                "                        </button>\n" +
                "                        <button class=\"btn\" onclick=\"generateReport('both')\" id=\"bothBtn\">\n" +
                "                            <span class=\"loading\" id=\"bothLoading\"><span class=\"spinner\"></span></span>\n" +
                "                            📋 生成全部报告\n" +
                "                        </button>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"card\">\n" +
                "                <h3 data-icon=\"📝\">实时日志监控</h3>\n" +
                "                <div class=\"alert alert-info\">\n" +
                "                    <strong>日志说明:</strong> 实时显示系统运行日志，自动刷新，支持多种日志级别显示。\n" +
                "                </div>\n" +
                "                <div class=\"log-container\" id=\"logContainer\">\n" +
                "                    <div class=\"log-line log-info\">[INFO] Web管理界面已启动</div>\n" +
                "                    <div class=\"log-line log-info\">[INFO] 配置热重载功能已启用</div>\n" +
                "                    <div class=\"log-line log-success\">[SUCCESS] 系统运行正常</div>\n" +
                "                </div>\n" +
                "                <div class=\"button-group\">\n" +
                "                    <button class=\"btn\" onclick=\"refreshLogs()\">🔄 刷新日志</button>\n" +
                "                    <button class=\"btn\" onclick=\"clearLogs()\">🗑️ 清空显示</button>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div id=\"config-tab\" class=\"tab-content\">\n" +
                "            <div class=\"card\">\n" +
                "                <h3 data-icon=\"⚙️\">数据库配置管理</h3>\n" +
                "                <div class=\"alert alert-info\">\n" +
                "                    <strong>功能说明:</strong> 查看、编辑和管理数据库配置文件，支持YAML格式。\n" +
                "                </div>\n" +
                "                <div id=\"configResult\"></div>\n" +
                "                <div class=\"button-group\">\n" +
                "                    <button class=\"btn btn-info\" onclick=\"loadConfigList()\">🔄 刷新配置列表</button>\n" +
                "                    <button class=\"btn btn-success\" onclick=\"createNewConfig()\">➕ 新建配置文件</button>\n" +
                "                </div>\n" +
                "                <div class=\"config-list\" id=\"configList\">\n" +
                "                    <!-- 配置文件列表将在这里显示 -->\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <!-- 配置编辑模态框 -->\n" +
                "    <div id=\"configModal\" class=\"modal\">\n" +
                "        <div class=\"modal-content\">\n" +
                "            <span class=\"close\" onclick=\"closeConfigModal()\">&times;</span>\n" +
                "            <h2 id=\"modalTitle\">编辑配置文件</h2>\n" +
                "            <form id=\"configForm\">\n" +
                "                <input type=\"hidden\" id=\"configFileName\">\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label for=\"configContent\">配置内容 (YAML格式):</label>\n" +
                "                    <textarea id=\"configContent\" placeholder=\"在此输入YAML格式的配置内容...\"></textarea>\n" +
                "                </div>\n" +
                "                <div class=\"button-group\">\n" +
                "                    <button type=\"button\" class=\"btn btn-success\" onclick=\"saveConfig()\">💾 保存配置</button>\n" +
                "                    <button type=\"button\" class=\"btn btn-secondary\" onclick=\"closeConfigModal()\">❌ 取消</button>\n" +
                "                    <button type=\"button\" class=\"btn btn-warning\" onclick=\"deleteConfig()\" id=\"deleteConfigBtn\" style=\"display: none;\">🗑️ 删除配置</button>\n" +
                "                </div>\n" +
                "            </form>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <script>\n" +
                "        let logRefreshInterval;\n" +
                "        let lastConnectionTestTime = 0;\n" +
                "        const CONNECTION_TEST_COOLDOWN = 10 * 60 * 1000; // 10分钟\n" +
                "        \n" +
                "        // 页面加载完成后启动定时刷新\n" +
                "        document.addEventListener('DOMContentLoaded', function() {\n" +
                "            refreshLogs();\n" +
                "            startLogRefresh();\n" +
                "            \n" +
                "            // 添加标签页切换功能\n" +
                "            document.querySelectorAll('.tab').forEach(tab => {\n" +
                "                tab.addEventListener('click', function() {\n" +
                "                    // 移除所有活动状态\n" +
                "                    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));\n" +
                "                    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));\n" +
                "                    \n" +
                "                    // 激活当前标签页\n" +
                "                    this.classList.add('active');\n" +
                "                    const tabName = this.getAttribute('data-tab');\n" +
                "                    document.getElementById(tabName + '-tab').classList.add('active');\n" +
                "                    \n" +
                "                    // 如果是配置管理标签页，加载配置列表\n" +
                "                    if (tabName === 'config') {\n" +
                "                        loadConfigList();\n" +
                "                    }\n" +
                "                });\n" +
                "            });\n" +
                "        });\n" +
                "        \n" +
                "        function startLogRefresh() {\n" +
                "            logRefreshInterval = setInterval(refreshLogs, 3000); // 每3秒刷新一次\n" +
                "        }\n" +
                "        \n" +
                "        function stopLogRefresh() {\n" +
                "            if (logRefreshInterval) {\n" +
                "                clearInterval(logRefreshInterval);\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function refreshLogs() {\n" +
                "            fetch('/api/logs')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    const logContainer = document.getElementById('logContainer');\n" +
                "                    logContainer.innerHTML = ''; // 清空日志容器\n" +
                "                    data.forEach(log => {\n" +
                "                        const logLine = document.createElement('div');\n" +
                "                        logLine.className = 'log-line log-' + log.level;\n" +
                "                        logLine.textContent = log.message;\n" +
                "                        logContainer.appendChild(logLine);\n" +
                "                    });\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('加载日志失败:', error);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function clearLogs() {\n" +
                "            document.getElementById('logContainer').innerHTML = '';\n" +
                "        }\n" +
                "        \n" +
                "        function testDatabaseConnections() {\n" +
                "            const testBtn = document.getElementById('testConnectionBtn');\n" +
                "            const testLoading = document.getElementById('testLoading');\n" +
                "            const cooldownInfo = document.getElementById('cooldownInfo');\n" +
                "            const currentTime = Date.now();\n" +
                "            \n" +
                "            if (currentTime - lastConnectionTestTime < CONNECTION_TEST_COOLDOWN) {\n" +
                "                cooldownInfo.style.display = 'block';\n" +
                "                cooldownInfo.textContent = `请等待 ${Math.ceil((CONNECTION_TEST_COOLDOWN - (currentTime - lastConnectionTestTime)) / 1000)} 秒后重试`; \n" +
                "                return;\n" +
                "            }\n" +
                "            \n" +
                "            testBtn.disabled = true;\n" +
                "            testLoading.classList.add('show');\n" +
                "            cooldownInfo.style.display = 'none';\n" +
                "            \n" +
                "            fetch('/api/connection-test', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    let resultHtml = '';\n" +
                "                    if (data.error) {\n" +
                "                        resultHtml = `<div class=\"alert alert-error\"><strong>错误:</strong> ${data.error}</div>`;\n" +
                "                    } else {\n" +
                "                        const successRate = data.total > 0 ? Math.round((data.success / data.total) * 100) : 0;\n" +
                "                        resultHtml = `\n" +
                "                            <div class=\"alert alert-success\">\n" +
                "                                <h4>🎉 连接测试完成</h4>\n" +
                "                                <p><strong>成功连接:</strong> ${data.success} 个数据库</p>\n" +
                "                                <p><strong>连接失败:</strong> ${data.failed} 个数据库</p>\n" +
                "                                <p><strong>总计测试:</strong> ${data.total} 个数据库</p>\n" +
                "                                <p><strong>成功率:</strong> ${successRate}%</p>\n" +
                "                            </div>\n" +
                "                        `;\n" +
                "                    }\n" +
                "                    document.getElementById('connectionTestResult').innerHTML = resultHtml;\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    document.getElementById('connectionTestResult').innerHTML = `<div class=\"alert alert-error\"><strong>网络错误:</strong> ${error.message}</div>`;\n" +
                "                })\n" +
                "                .finally(() => {\n" +
                "                    testBtn.disabled = false;\n" +
                "                    testLoading.classList.remove('show');\n" +
                "                    lastConnectionTestTime = currentTime;\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function encryptConfigurations() {\n" +
                "            const encryptBtn = document.getElementById('encryptBtn');\n" +
                "            const encryptLoading = document.getElementById('encryptLoading');\n" +
                "            \n" +
                "            encryptBtn.disabled = true;\n" +
                "            encryptLoading.classList.add('show');\n" +
                "            \n" +
                "            fetch('/api/encrypt-config', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    let resultHtml = '';\n" +
                "                    if (data.success) {\n" +
                "                        resultHtml = `\n" +
                "                            <div class=\"alert alert-success\">\n" +
                "                                <h4>🔒 配置加密成功</h4>\n" +
                "                                <p>${data.message}</p>\n" +
                "                                <p><small>所有敏感信息已使用SM4算法安全加密</small></p>\n" +
                "                            </div>\n" +
                "                        `;\n" +
                "                    } else {\n" +
                "                        resultHtml = `<div class=\"alert alert-error\"><strong>加密失败:</strong> ${data.message}</div>`;\n" +
                "                    }\n" +
                "                    document.getElementById('encryptResult').innerHTML = resultHtml;\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    document.getElementById('encryptResult').innerHTML = `<div class=\"alert alert-error\"><strong>网络错误:</strong> ${error.message}</div>`;\n" +
                "                })\n" +
                "                .finally(() => {\n" +
                "                    encryptBtn.disabled = false;\n" +
                "                    encryptLoading.classList.remove('show');\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function generateReport(type) {\n" +
                "            const excelBtn = document.getElementById('excelBtn');\n" +
                "            const htmlBtn = document.getElementById('htmlBtn');\n" +
                "            const bothBtn = document.getElementById('bothBtn');\n" +
                "            const excelLoading = document.getElementById('excelLoading');\n" +
                "            const htmlLoading = document.getElementById('htmlLoading');\n" +
                "            const bothLoading = document.getElementById('bothLoading');\n" +
                "            \n" +
                "            // 禁用所有按钮\n" +
                "            excelBtn.disabled = true;\n" +
                "            htmlBtn.disabled = true;\n" +
                "            bothBtn.disabled = true;\n" +
                "            \n" +
                "            // 只显示对应按钮的加载状态\n" +
                "            if (type === 'excel') {\n" +
                "                excelLoading.classList.add('show');\n" +
                "            } else if (type === 'html') {\n" +
                "                htmlLoading.classList.add('show');\n" +
                "            } else if (type === 'both') {\n" +
                "                bothLoading.classList.add('show');\n" +
                "            }\n" +
                "            \n" +
                "            fetch('/api/generate-report', {\n" +
                "                method: 'POST',\n" +
                "                headers: {\n" +
                "                    'Content-Type': 'application/json'\n" +
                "                },\n" +
                "                body: JSON.stringify({ type: type })\n" +
                "            })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    let resultHtml = '';\n" +
                "                    if (data.success) {\n" +
                "                        resultHtml = `\n" +
                "                            <div class=\"alert alert-success\">\n" +
                "                                <h4>📊 报告生成成功</h4>\n" +
                "                                <p><strong>保存位置:</strong> ${data.message}</p>\n" +
                "                                <p><strong>文件名:</strong> ${data.fileName}</p>\n" +
                "                        `;\n" +
                "                        // 只有在生成HTML报告或全部报告时才显示预览按钮\n" +
                "                        if (data.previewUrl && (type === 'html' || type === 'both')) {\n" +
                "                            resultHtml += `<p><a href=\"${data.previewUrl}\" target=\"_blank\" class=\"btn btn-info\">🌐 预览HTML报告</a></p>`;\n" +
                "                        }\n" +
                "                        resultHtml += `</div>`;\n" +
                "                    } else {\n" +
                "                        resultHtml = `<div class=\"alert alert-error\"><strong>生成失败:</strong> ${data.message}</div>`;\n" +
                "                    }\n" +
                "                    document.getElementById('reportResult').innerHTML = resultHtml;\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    document.getElementById('reportResult').innerHTML = `<div class=\"alert alert-error\"><strong>网络错误:</strong> ${error.message}</div>`;\n" +
                "                })\n" +
                "                .finally(() => {\n" +
                "                    excelBtn.disabled = false;\n" +
                "                    htmlBtn.disabled = false;\n" +
                "                    bothBtn.disabled = false;\n" +
                "                    excelLoading.classList.remove('show');\n" +
                "                    htmlLoading.classList.remove('show');\n" +
                "                    bothLoading.classList.remove('show');\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function loadConfigList() {\n" +
                "            fetch('/api/config')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    const configList = document.getElementById('configList');\n" +
                "                    configList.innerHTML = ''; // 清空配置列表\n" +
                "                    data.forEach(config => {\n" +
                "                        const configItem = document.createElement('div');\n" +
                "                        configItem.className = 'config-item';\n" +
                "                        configItem.innerHTML = `\n" +
                "                            <span>${config.name}</span>\n" +
                "                            <div class=\"config-actions\">\n" +
                "                                <button class=\"btn btn-secondary\" onclick=\"editConfig('${config.name}')\">编辑</button>\n" +
                "                                <button class=\"btn btn-warning\" onclick=\"deleteConfig('${config.name}')\">删除</button>\n" +
                "                            </div>\n" +
                "                        `;\n" +
                "                        configList.appendChild(configItem);\n" +
                "                    });\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('加载配置列表失败:', error);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function createNewConfig() {\n" +
                "            document.getElementById('configFileName').value = '';\n" +
                "            document.getElementById('configContent').value = '';\n" +
                "            document.getElementById('modalTitle').textContent = '新建配置文件';\n" +
                "            document.getElementById('deleteConfigBtn').style.display = 'none';\n" +
                "            document.getElementById('configModal').style.display = 'block';\n" +
                "        }\n" +
                "        \n" +
                "        function editConfig(fileName) {\n" +
                "            console.log('编辑配置文件:', fileName);\n" +
                "            \n" +
                "            fetch(`/api/config/${fileName}`)\n" +
                "                .then(response => {\n" +
                "                    if (!response.ok) {\n" +
                "                        throw new Error(`HTTP error! status: ${response.status}`);\n" +
                "                    }\n" +
                "                    return response.json();\n" +
                "                })\n" +
                "                .then(data => {\n" +
                "                    console.log('加载配置文件成功:', data);\n" +
                "                    document.getElementById('configFileName').value = fileName;\n" +
                "                    // 处理返回的数据格式\n" +
                "                    const content = data.content || data;\n" +
                "                    document.getElementById('configContent').value = content;\n" +
                "                    document.getElementById('modalTitle').textContent = `编辑配置文件: ${fileName}`;\n" +
                "                    document.getElementById('deleteConfigBtn').style.display = 'inline-block';\n" +
                "                    document.getElementById('configModal').style.display = 'block';\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('加载配置文件失败:', error);\n" +
                "                    alert('加载配置文件失败: ' + error.message);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function saveConfig() {\n" +
                "            const fileName = document.getElementById('configFileName').value;\n" +
                "            const content = document.getElementById('configContent').value;\n" +
                "            \n" +
                "            // 如果是新建配置，需要提示输入文件名\n" +
                "            let actualFileName = fileName;\n" +
                "            if (!actualFileName) {\n" +
                "                actualFileName = prompt('请输入配置文件名（例如：test-config.yml）:');\n" +
                "                if (!actualFileName) {\n" +
                "                    alert('请输入有效的文件名');\n" +
                "                    return;\n" +
                "                }\n" +
                "            }\n" +
                "            \n" +
                "            // 确定文件类型\n" +
                "            let fileType = 'database';\n" +
                "            if (actualFileName.toLowerCase().includes('metrics')) {\n" +
                "                fileType = 'metrics';\n" +
                "            }\n" +
                "            \n" +
                "            const requestData = {\n" +
                "                fileName: actualFileName,\n" +
                "                content: content,\n" +
                "                type: fileType\n" +
                "            };\n" +
                "            \n" +
                "            console.log('发送配置保存请求:', requestData);\n" +
                "            \n" +
                "            fetch('/api/config', {\n" +
                "                method: 'POST',\n" +
                "                headers: {\n" +
                "                    'Content-Type': 'application/json'\n" +
                "                },\n" +
                "                body: JSON.stringify(requestData)\n" +
                "            })\n" +
                "                .then(response => {\n" +
                "                    if (!response.ok) {\n" +
                "                        throw new Error(`HTTP error! status: ${response.status}`);\n" +
                "                    }\n" +
                "                    return response.json();\n" +
                "                })\n" +
                "                .then(data => {\n" +
                "                    console.log('配置保存成功:', data);\n" +
                "                    if (data.success) {\n" +
                "                        alert('配置保存成功！');\n" +
                "                        closeConfigModal();\n" +
                "                        loadConfigList();\n" +
                "                    } else {\n" +
                "                        alert('配置保存失败: ' + (data.message || '未知错误'));\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('保存配置失败:', error);\n" +
                "                    alert('保存配置失败: ' + error.message);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function deleteConfig(fileName) {\n" +
                "            if (confirm('确定要删除该配置文件吗？')) {\n" +
                "                fetch(`/api/config/${fileName}`, { method: 'DELETE' })\n" +
                "                    .then(response => response.json())\n" +
                "                    .then(data => {\n" +
                "                        console.log('配置删除成功:', data);\n" +
                "                        closeConfigModal();\n" +
                "                        loadConfigList();\n" +
                "                    })\n" +
                "                    .catch(error => {\n" +
                "                        console.error('删除配置失败:', error);\n" +
                "                    });\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function closeConfigModal() {\n" +
                "            document.getElementById('configModal').style.display = 'none';\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";

        }
    }
}
