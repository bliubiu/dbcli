package com.dbcli.web.handler;

import com.dbcli.config.AppConfig;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class ConfigManagementHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManagementHandler.class);
    
    private final AppConfig config;
    
    public ConfigManagementHandler(AppConfig config) {
        this.config = config;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ResponseUtil.setCorsHeaders(exchange);
        
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
                ResponseUtil.sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}", "application/json");
            }
        } catch (Exception e) {
            logger.error("配置管理处理失败", e);
            String response = String.format(
                "{\"success\": false, \"message\": \"%s\"}",
                e.getMessage().replace("\"", "\\\"")
            );
            ResponseUtil.sendResponse(exchange, 500, response, "application/json");
        }
    }
    
    private void handleGetConfig(HttpExchange exchange, String requestPath) throws IOException {
        String configPath = config.getConfigPath();
        String metricsPath = config.getMetricsPath();
        Path configDir = Paths.get(configPath);
        Path metricsDir = Paths.get(metricsPath);
        
        if (!Files.exists(configDir) && !Files.exists(metricsDir)) {
            ResponseUtil.sendResponse(exchange, 404, "{\"error\": \"Config and metrics directories not found\"}", "application/json");
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
            ResponseUtil.sendResponse(exchange, 200, response, "application/json");
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
                ResponseUtil.sendResponse(exchange, 404, "{\"error\": \"Config file not found\"}", "application/json");
                return;
            }
            
            try {
                String content = Files.readString(configFile, StandardCharsets.UTF_8);
                Map<String, Object> result = new HashMap<>();
                result.put("name", fileName);
                result.put("content", content);
                String response = toJson(result);
                logger.info("返回配置文件内容: {} (大小: {} 字符)", fileName, content.length());
                ResponseUtil.sendResponse(exchange, 200, response, "application/json");
            } catch (IOException e) {
                logger.error("读取配置文件失败: {}", configFile, e);
                String response = String.format(
                    "{\"error\": \"Failed to read config file: %s\"}",
                    e.getMessage().replace("\"", "\\\"")
                );
                ResponseUtil.sendResponse(exchange, 500, response, "application/json");
            }
        }
    }
    
    private void handleUpdateConfig(HttpExchange exchange, String requestPath) throws IOException {
        String configPath = config.getConfigPath();
        String metricsPath = config.getMetricsPath();
        Path configDir = Paths.get(configPath);
        Path metricsDir = Paths.get(metricsPath);
        
        if (!Files.exists(configDir) && !Files.exists(metricsDir)) {
            ResponseUtil.sendResponse(exchange, 404, "{\"error\": \"Config and metrics directories not found\"}", "application/json");
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
            ResponseUtil.sendResponse(exchange, 400, "{\"error\": \"Missing fileName or content\"}", "application/json");
            return;
        }
        
        // 规范化文件名并进行内容与YAML语法校验
        String normalizedFileName = ensureYamlSuffix(fileName);
        String contentForValidation = stripSurroundingQuotes(content)
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
        // 拒绝空内容（如需允许空内容可移除此校验）
        if (isBlank(contentForValidation)) {
            ResponseUtil.sendResponse(exchange, 400, "{\"error\": \"Content is empty\"}", "application/json");
            return;
        }
        try {
            // YAML 语法校验
            new Yaml().load(contentForValidation);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().replace("\"", "\\\"") : "YAML parse error";
            ResponseUtil.sendResponse(exchange, 400, "{\"error\": \"Invalid YAML: " + msg + "\"}", "application/json");
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
            configFile = metricsDir.resolve(normalizedFileName);
        } else {
            // 确保数据库配置目录存在
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            configFile = configDir.resolve(normalizedFileName);
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
            ResponseUtil.sendResponse(exchange, 200, response, "application/json");
        } catch (IOException e) {
            logger.error("写入配置文件失败: {}", configFile, e);
            String response = String.format(
                "{\"success\": false, \"message\": \"Failed to write config file: %s\"}",
                e.getMessage().replace("\"", "\\\"")
            );
            ResponseUtil.sendResponse(exchange, 500, response, "application/json");
        }
    }
    
    private void handleDeleteConfig(HttpExchange exchange, String requestPath) throws IOException {
        String configPath = config.getConfigPath();
        String metricsPath = config.getMetricsPath();
        Path configDir = Paths.get(configPath);
        Path metricsDir = Paths.get(metricsPath);
        
        if (!Files.exists(configDir) && !Files.exists(metricsDir)) {
            ResponseUtil.sendResponse(exchange, 404, "{\"error\": \"Config and metrics directories not found\"}", "application/json");
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
            ResponseUtil.sendResponse(exchange, 404, "{\"error\": \"Config file not found\"}", "application/json");
            return;
        }
        
        try {
            Files.delete(configFile);
            String response = "{\"success\": true, \"message\": \"配置文件删除成功\"}";
            ResponseUtil.sendResponse(exchange, 200, response, "application/json");
        } catch (IOException e) {
            logger.error("删除配置文件失败: {}", configFile, e);
            String response = String.format(
                "{\"success\": false, \"message\": \"Failed to delete config file: %s\"}",
                e.getMessage().replace("\"", "\\\"")
            );
            ResponseUtil.sendResponse(exchange, 500, response, "application/json");
        }
    }
    
    private String ensureYamlSuffix(String name) {
        if (name == null) return "config.yml";
        String n = name.trim();
        String lower = n.toLowerCase();
        if (!(lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
            return n + ".yml";
        }
        return n;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String stripSurroundingQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
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