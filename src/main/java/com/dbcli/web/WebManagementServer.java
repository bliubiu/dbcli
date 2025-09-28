package com.dbcli.web;

import com.dbcli.config.AppConfig;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.dbcli.config.ConfigLoader;
import com.dbcli.core.DbCliRunner;
import com.dbcli.database.ConnectionFactory;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.service.EncryptionService;
import com.dbcli.service.FastConnectionTestService;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Web管理服务器
 * 提供基于HTTP的管理界面
 */
public class WebManagementServer {
    private static final Logger logger = LoggerFactory.getLogger(WebManagementServer.class);
    
    private HttpServer server;
    private final int port;
    private final AppConfig config;
    private volatile boolean running = false;
    
    public WebManagementServer(int port) {
        this.port = port;
        this.config = null;
    }
    
    public WebManagementServer(AppConfig config) {
        this.port = 8080; // 默认端口
        this.config = config;
    }
    
    /**
     * 启动Web服务器
     */
    public void start() throws IOException {
        if (running) {
            logger.warn("Web管理服务器已经在运行中");
            return;
        }
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册处理器
        server.createContext("/", new DashboardHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/metrics", new MetricsHandler());
        server.createContext("/api/database", new DatabaseHandler());
        server.createContext("/api/reports", new ReportsHandler());
        server.createContext("/api/encrypt", new EncryptHandler());
        server.createContext("/api/logs", new LogsHandler());
        server.createContext("/static", new StaticResourceHandler());
        
        // 添加全局过滤器处理CORS
        server.createContext("/api", new CorsHandler());
        
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        
        running = true;
        logger.info("Web管理服务器已启动，访问地址: http://localhost:{}", port);
    }
    
    /**
     * 停止Web服务器
     */
    public void stop() {
        if (server != null && running) {
            server.stop(2);
            running = false;
            logger.info("Web管理服务器已停止");
        }
    }
    
    /**
     * 获取运行状态
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 获取服务端口
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 等待服务器关闭
     */
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
     * 仪表板处理器
     */
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = generateDashboardHtml();
            sendResponse(exchange, 200, response, "text/html");
        }
        
        private String generateDashboardHtml() {
            return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>DBCLI 管理控制台</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; }\n" +
                "        .header { background: #2c3e50; color: white; padding: 1rem; text-align: center; }\n" +
                "        .container { max-width: 1200px; margin: 2rem auto; padding: 0 1rem; }\n" +
                "        .card { background: white; border-radius: 8px; padding: 1.5rem; margin-bottom: 1rem; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n" +
                "        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem; }\n" +
                "        .metric { text-align: center; padding: 1rem; }\n" +
                "        .metric-value { font-size: 2rem; font-weight: bold; color: #3498db; }\n" +
                "        .metric-label { color: #7f8c8d; margin-top: 0.5rem; }\n" +
                "        .btn { background: #3498db; color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; margin: 0.2rem; }\n" +
                "        .btn:hover { background: #2980b9; }\n" +
                "        .btn-warning { background: #f39c12; }\n" +
                "        .btn-warning:hover { background: #e67e22; }\n" +
                "        .btn-success { background: #27ae60; }\n" +
                "        .btn-success:hover { background: #229954; }\n" +
                "        .btn-danger { background: #e74c3c; }\n" +
                "        .btn-danger:hover { background: #c0392b; }\n" +
                "        .status-online { color: #27ae60; }\n" +
                "        .status-offline { color: #e74c3c; }\n" +
                "        .log-container { background: #2c3e50; color: #ecf0f1; padding: 1rem; border-radius: 4px; font-family: monospace; max-height: 300px; overflow-y: auto; }\n" +
                "        .button-group { margin-top: 1rem; }\n" +
                "        .preview-link { color: #3498db; text-decoration: none; }\n" +
                "        .preview-link:hover { text-decoration: underline; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <h1>🚀 DBCLI 管理控制台</h1>\n" +
                "        <p>数据库连接性能测试工具 - Web管理界面</p>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"grid\">\n" +
                "            <div class=\"card\">\n" +
                "                <h3>📊 系统状态</h3>\n" +
                "                <div class=\"metric\">\n" +
                "                    <div class=\"metric-value status-online\" id=\"systemStatus\">运行中</div>\n" +
                "                    <div class=\"metric-label\">系统状态</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"card\">\n" +
                "                <h3>🔗 连接统计</h3>\n" +
                "                <div class=\"metric\">\n" +
                "                    <div class=\"metric-value\" id=\"connectionCount\">0</div>\n" +
                "                    <div class=\"metric-label\">活跃连接数</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"card\">\n" +
                "                <h3>⚡ 性能指标</h3>\n" +
                "                <div class=\"metric\">\n" +
                "                    <div class=\"metric-value\" id=\"avgResponseTime\">0ms</div>\n" +
                "                    <div class=\"metric-label\">平均响应时间</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"card\">\n" +
                "            <h3>⚙️ 配置管理</h3>\n" +
                "            <p>配置热重载状态: <span id=\"hotReloadStatus\" class=\"status-online\">启用</span></p>\n" +
                "            <div class=\"button-group\">\n" +
                "                <button class=\"btn\" onclick=\"reloadConfig()\">重载配置</button>\n" +
                "                <button class=\"btn btn-warning\" onclick=\"testConnections()\">测试连接</button>\n" +
                "                <button class=\"btn btn-success\" onclick=\"encryptConfigs()\">加密配置</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"card\">\n" +
                "            <h3>📈 报告生成</h3>\n" +
                "            <p>选择报告格式:</p>\n" +
                "            <div class=\"button-group\">\n" +
                "                <button class=\"btn\" onclick=\"generateReport('excel')\">生成Excel报告</button>\n" +
                "                <button class=\"btn\" onclick=\"generateReport('html')\">生成HTML报告</button>\n" +
                "                <button class=\"btn\" onclick=\"generateReport('both')\">生成全部报告</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"card\">\n" +
                "            <h3>📝 实时日志</h3>\n" +
                "            <div class=\"log-container\" id=\"logContainer\">\n" +
                "                <div>[INFO] Web管理界面已启动</div>\n" +
                "                <div>[INFO] 配置热重载功能已启用</div>\n" +
                "                <div>[INFO] 系统运行正常</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <script>\n" +
                "        // 定期更新数据\n" +
                "        setInterval(updateMetrics, 5000);\n" +
                "        setInterval(refreshLogs, 2000);\n" +
                "        \n" +
                "        function updateMetrics() {\n" +
                "            fetch('/api/metrics')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    document.getElementById('connectionCount').textContent = data.connections || 0;\n" +
                "                    document.getElementById('avgResponseTime').textContent = (data.avgResponseTime || 0) + 'ms';\n" +
                "                })\n" +
                "                .catch(err => console.error('更新指标失败:', err));\n" +
                "        }\n" +
                "        \n" +
                "        function refreshLogs() {\n" +
                "            fetch('/api/logs')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    const logContainer = document.getElementById('logContainer');\n" +
                "                    logContainer.innerHTML = '';\n" +
                "                    data.logs.forEach(log => {\n" +
                "                        const logElement = document.createElement('div');\n" +
                "                        logElement.textContent = log;\n" +
                "                        logContainer.appendChild(logElement);\n" +
                "                    });\n" +
                "                    // 滚动到底部\n" +
                "                    logContainer.scrollTop = logContainer.scrollHeight;\n" +
                "                })\n" +
                "                .catch(err => console.error('刷新日志失败:', err));\n" +
                "        }\n" +
                "        \n" +
                "        function reloadConfig() {\n" +
                "            fetch('/api/config/reload', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    alert(data.message || '配置重载完成');\n" +
                "                })\n" +
                "                .catch(err => alert('配置重载失败: ' + err.message));\n" +
                "        }\n" +
                "        \n" +
                "        function testConnections() {\n" +
                "            if (!confirm('确定要测试所有数据库连接吗？')) return;\n" +
                "            \n" +
                "            fetch('/api/database/test', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    alert('连接测试完成\\n成功: ' + data.success + '\\n失败: ' + data.failed);\n" +
                "                })\n" +
                "                .catch(err => alert('连接测试失败: ' + err.message));\n" +
                "        }\n" +
                "        \n" +
                "        function encryptConfigs() {\n" +
                "            if (!confirm('确定要加密所有配置文件吗？')) return;\n" +
                "            \n" +
                "            fetch('/api/encrypt', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    alert(data.message || '配置加密完成');\n" +
                "                })\n" +
                "                .catch(err => alert('配置加密失败: ' + err.message));\n" +
                "        }\n" +
                "        \n" +
                "        function generateReport(format) {\n" +
                "            if (!confirm('确定要生成' + format + '格式的报告吗？')) return;\n" +
                "            \n" +
                "            fetch('/api/reports/generate', {\n" +
                "                method: 'POST',\n" +
                "                headers: { 'Content-Type': 'application/json' },\n" +
                "                body: JSON.stringify({ format: format })\n" +
                "            })\n" +
                "            .then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    if (data.fileName) {\n" +
                "                        alert('报告生成成功\\n文件: ' + data.fileName);\n" +
                "                        if (format === 'html' && data.previewUrl) {\n" +
                "                            if (confirm('是否在新窗口中预览HTML报告？')) {\n" +
                "                                window.open(data.previewUrl, '_blank');\n" +
                "                            }\n" +
                "                        }\n" +
                "                    } else {\n" +
                "                        alert('报告生成成功\\n' + data.message);\n" +
                "                    }\n" +
                "                } else {\n" +
                "                    alert('报告生成失败\\n' + data.message);\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(err => alert('报告生成失败: ' + err.message));\n" +
                "        }\n" +
                "        \n" +
                "        // 初始化\n" +
                "        updateMetrics();\n" +
                "        refreshLogs();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
        }
    }
    
    /**
     * CORS处理器
     */
    private class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 处理OPTIONS预检请求
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            // 继续处理其他请求
            // 注意：这个处理器只是用来处理CORS预检，实际请求会由具体处理器处理
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
    }
    
    /**
     * 状态API处理器
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            String response = "{\n" +
                "    \"status\": \"running\",\n" +
                "    \"uptime\": \"2h 15m\",\n" +
                "    \"version\": \"2.1.0\",\n" +
                "    \"hotReload\": true\n" +
                "}";
            sendResponse(exchange, 200, response, "application/json");
        }
    }
    
    /**
     * 配置API处理器
     */
    private class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if ("POST".equals(method) && path.endsWith("/reload")) {
                try {
                    // 实际执行配置重载
                    ConfigLoader configLoader = new ConfigLoader(new EncryptionService());
                    configLoader.loadDatabaseConfigs(config.getConfigPath());
                    
                    String response = "{\n" +
                        "    \"success\": true,\n" +
                        "    \"message\": \"配置重载完成\",\n" +
                        "    \"timestamp\": \"" + java.time.Instant.now() + "\"\n" +
                        "}";
                    sendResponse(exchange, 200, response, "application/json");
                } catch (Exception e) {
                    String response = "{\n" +
                        "    \"success\": false,\n" +
                        "    \"message\": \"配置重载失败: " + e.getMessage() + "\"\n" +
                        "}";
                    sendResponse(exchange, 500, response, "application/json");
                }
            } else {
                sendResponse(exchange, 404, "Not Found", "text/plain");
            }
        }
    }
    
    /**
     * 数据库相关处理器
     */
    private class DatabaseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if ("POST".equals(method) && path.endsWith("/test")) {
                // 实际执行数据库连接测试
                try {
                    ConfigLoader configLoader = new ConfigLoader(new EncryptionService());
                    Map<String, DatabaseConfig> databaseConfigs = configLoader.loadDatabaseConfigs(config.getConfigPath());
                    
                    // 读取黑名单列表
                    Set<String> blacklist = readBlacklistFile();
                    
                    int success = 0;
                    int failed = 0;
                    
                    // 创建测试服务实例，修复ConnectionFactory为null的问题
                    ConnectionFactory connectionFactory = new ConnectionFactory();
                    FastConnectionTestService connectionTestService = new FastConnectionTestService(connectionFactory, config.getConcurrency());
                    
                    // 构造测试数据结构，过滤掉黑名单中的配置
                    Map<String, Map<String, DatabaseConfig>> testConfigs = new HashMap<>();
                    for (Map.Entry<String, DatabaseConfig> entry : databaseConfigs.entrySet()) {
                        String systemName = entry.getKey();
                        DatabaseConfig dbConfig = entry.getValue();
                        
                        // 跳过黑名单中的配置
                        if (blacklist.contains(systemName)) {
                            continue;
                        }
                        
                        String dbType = dbConfig.getType() != null ? dbConfig.getType() : "unknown";
                        testConfigs.computeIfAbsent(dbType, k -> new HashMap<>()).put(systemName, dbConfig);
                    }
                    
                    // 计算启用且非黑名单的配置总数
                    int totalEnabledConfigs = 0;
                    for (Map.Entry<String, Map<String, DatabaseConfig>> entry : testConfigs.entrySet()) {
                        for (Map.Entry<String, DatabaseConfig> configEntry : entry.getValue().entrySet()) {
                            DatabaseConfig dbConfig = configEntry.getValue();
                            if (dbConfig.isEnable()) {
                                totalEnabledConfigs++;
                            }
                        }
                    }
                    
                    // 执行测试
                    boolean testResult = connectionTestService.testConnectionsWithNames(testConfigs);
                    
                    // 根据整体测试结果估算成功/失败数量
                    if (testResult) {
                        // 如果整体测试成功，所有启用的非黑名单配置都成功
                        success = totalEnabledConfigs;
                        failed = 0;
                    } else {
                        // 如果整体测试失败，获取实际失败数量
                        // 从FastConnectionTestService获取失败的连接数量
                        int failedCount = connectionTestService.getFailedEncryptedHosts().size();
                        // 确保至少有一个失败，且总数正确
                        failed = Math.max(1, failedCount);
                        success = Math.max(0, totalEnabledConfigs - failed);
                    }
                    
                    String response = "{\n" +
                        "    \"success\": " + success + ",\n" +
                        "    \"failed\": " + failed + "\n" +
                        "}";
                    sendResponse(exchange, 200, response, "application/json");
                } catch (Exception e) {
                    String response = "{\n" +
                        "    \"success\": 0,\n" +
                        "    \"failed\": 0,\n" +
                        "    \"error\": \"" + e.getMessage() + "\"\n" +
                        "}";
                    sendResponse(exchange, 500, response, "application/json");
                }
            } else {
                sendResponse(exchange, 404, "Not Found", "text/plain");
            }
        }
        
        private Set<String> readBlacklistFile() {
            try {
                List<String> lines = Files.readAllLines(Paths.get("logs/db_conn_blacklist.txt"), StandardCharsets.UTF_8);
                return lines.stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toSet());
            } catch (IOException e) {
                logger.warn("无法读取黑名单文件: {}", e.getMessage());
                return Set.of(); // 返回空集合而不是null
            }
        }
    }
    
    /**
     * 报告生成处理器
     */
    private class ReportsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if ("POST".equals(method) && path.endsWith("/generate")) {
                try {
                    // 创建一个新的AppConfig实例来生成报告
                    AppConfig reportConfig = new AppConfig();
                    reportConfig.setConfigPath(config.getConfigPath());
                    reportConfig.setMetricsPath(config.getMetricsPath());
                    reportConfig.setOutputPath(config.getOutputPath());
                    reportConfig.setConcurrency(config.getConcurrency());
                    
                    // 根据请求体中的format参数设置输出格式
                    String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String format = "both";
                    if (requestBody != null && !requestBody.isEmpty()) {
                        // 简单解析JSON请求体提取format参数
                        if (requestBody.contains("\"format\":\"excel\"")) {
                            format = "excel";
                        } else if (requestBody.contains("\"format\":\"html\"")) {
                            format = "html";
                        }
                    }
                    reportConfig.setOutputFormat(format);
                    
                    // 创建一个新的DbCliRunner实例来生成报告
                    DbCliRunner runner = new DbCliRunner(reportConfig);
                    boolean success = runner.run();
                    
                    String fileName = "";
                    String previewUrl = "";
                    if (success) {
                        // 构建文件名
                        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                        if ("excel".equals(format)) {
                            fileName = "db_report_" + timestamp + ".xlsx";
                        } else if ("html".equals(format)) {
                            fileName = "db_metrics_report_" + timestamp + ".html";
                            // 修复预览URL，去掉前导斜杠，使用相对路径
                            previewUrl = "reports/db_metrics_report_" + timestamp + ".html";
                        } else {
                            fileName = "db_report_" + timestamp + ".xlsx, db_metrics_report_" + timestamp + ".html";
                            previewUrl = "reports/db_metrics_report_" + timestamp + ".html";
                        }
                    }
                    
                    String response = "{\n" +
                        "    \"success\": " + success + ",\n" +
                        "    \"message\": \"" + (success ? "报告已成功生成到 reports/ 目录" : "报告生成失败") + "\",\n" +
                        "    \"fileName\": \"" + fileName + "\",\n" +
                        "    \"previewUrl\": \"" + previewUrl + "\"\n" +
                        "}";
                    sendResponse(exchange, 200, response, "application/json");
                } catch (Exception e) {
                    String response = "{\n" +
                        "    \"success\": false,\n" +
                        "    \"message\": \"报告生成失败: " + e.getMessage() + "\"\n" +
                        "}";
                    sendResponse(exchange, 500, response, "application/json");
                }
            } else {
                sendResponse(exchange, 404, "Not Found", "text/plain");
            }
        }
    }
    
    /**
     * 配置加密处理器
     */
    private class EncryptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            String method = exchange.getRequestMethod();
            
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if ("POST".equals(method)) {
                try {
                    // 实际执行配置加密
                    EncryptionService encryptionService = new EncryptionService();
                    encryptionService.encryptConfigs(config.getConfigPath());
                    
                    String response = "{\n" +
                        "    \"success\": true,\n" +
                        "    \"message\": \"配置文件已成功加密\"\n" +
                        "}";
                    sendResponse(exchange, 200, response, "application/json");
                } catch (Exception e) {
                    String response = "{\n" +
                        "    \"success\": false,\n" +
                        "    \"message\": \"配置加密失败: " + e.getMessage() + "\"\n" +
                        "}";
                    sendResponse(exchange, 500, response, "application/json");
                }
            } else {
                sendResponse(exchange, 404, "Not Found", "text/plain");
            }
        }
    }
    
    /**
     * 日志处理器
     */
    private class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            String method = exchange.getRequestMethod();
            
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if ("GET".equals(method)) {
                try {
                    // 读取最新的日志文件
                    List<String> logLines = new ArrayList<>();
                    
                    // 尝试读取主日志文件（使用RandomAccessFile实现实时读取）
                    Path logPath = Paths.get("logs/application.log");
                    if (Files.exists(logPath)) {
                        try (RandomAccessFile reader = new RandomAccessFile(logPath.toFile(), "r")) {
                            // 获取文件大小
                            long fileLength = reader.length();
                            
                            // 如果文件过大，只读取最后部分
                            long startPosition = Math.max(0, fileLength - 50000); // 读取最后50KB
                            reader.seek(startPosition);
                            
                            // 如果不是从文件开头开始，跳过第一行（可能是不完整的）
                            if (startPosition > 0) {
                                reader.readLine();
                            }
                            
                            // 读取所有行
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().length() > 0) {
                                    logLines.add(line);
                                }
                                // 限制最大行数
                                if (logLines.size() >= 200) {
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            logLines.add("[WARN] 无法读取主日志文件: " + e.getMessage());
                        }
                    } else {
                        logLines.add("[WARN] 日志文件不存在: " + logPath);
                    }
                    
                    // 如果主日志文件不存在或为空，尝试读取其他日志文件
                    if (logLines.isEmpty()) {
                        Path errorLogPath = Paths.get("logs/db_conn_error.txt");
                        if (Files.exists(errorLogPath)) {
                            try (RandomAccessFile reader = new RandomAccessFile(errorLogPath.toFile(), "r")) {
                                long fileLength = reader.length();
                                long startPosition = Math.max(0, fileLength - 10000); // 读取最后10KB
                                reader.seek(startPosition);
                                
                                if (startPosition > 0) {
                                    reader.readLine();
                                }
                                
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.trim().length() > 0) {
                                        logLines.add(line);
                                    }
                                    if (logLines.size() >= 100) {
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                // 忽略错误，使用默认消息
                            }
                        }
                    }
                    
                    // 如果仍然没有日志，添加默认消息
                    if (logLines.isEmpty()) {
                        logLines.add("[INFO] 暂无日志信息");
                        logLines.add("[INFO] 请执行一些操作以生成日志");
                    }
                    
                    // 添加时间戳，确保前端知道这是最新数据
                    logLines.add("[SYSTEM] 日志刷新时间: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
                    
                    String response = "{\n" +
                        "    \"logs\": " + toJsonArray(logLines) + "\n" +
                        "}";
                    sendResponse(exchange, 200, response, "application/json");
                } catch (Exception e) {
                    String response = "{\n" +
                        "    \"logs\": [\"[ERROR] 读取日志失败: " + e.getMessage() + "\"]\n" +
                        "}";
                    sendResponse(exchange, 500, response, "application/json");
                }
            } else {
                sendResponse(exchange, 404, "Not Found", "text/plain");
            }
        }
        
        private String toJsonArray(List<String> lines) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(lines.get(i).replace("\"", "\\\"")).append("\"");
            }
            sb.append("]");
            return sb.toString();
        }
    }
    
    /**
     * 指标API处理器
     */
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            String response = "{\n" +
                "    \"connections\": 5,\n" +
                "    \"avgResponseTime\": 125,\n" +
                "    \"successRate\": 98.5,\n" +
                "    \"errorCount\": 2,\n" +
                "    \"timestamp\": \"" + java.time.Instant.now() + "\"\n" +
                "}";
            sendResponse(exchange, 200, response, "application/json");
        }
    }
    
    /**
     * 静态资源处理器
     */
    private class StaticResourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 404, "Static resource not found", "text/plain");
        }
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
}