package com.dbcli.monitoring;

import com.dbcli.alert.AlertItemGenerator;
import com.dbcli.alert.AlertItemGenerator.AlertItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监控集成适配器
 * 支持对接外部监控栈和内置监控栈
 */
public class MonitoringIntegration {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringIntegration.class);
    
    // 监控模式
    public enum MonitoringMode {
        EXTERNAL("外部监控栈"),
        EMBEDDED("内置监控栈"),
        HYBRID("混合模式");
        
        private final String description;
        
        MonitoringMode(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    // 监控配置
    public static class MonitoringConfig {
        private MonitoringMode mode = MonitoringMode.EMBEDDED;
        private String prometheusUrl = "http://localhost:9090";
        private String alertManagerUrl = "http://localhost:9093";
        private String grafanaUrl = "http://localhost:3000";
        private Map<String, String> externalEndpoints = new HashMap<>();
        private boolean enableWebhooks = true;
        private int timeoutSeconds = 30;
        
        // Getters and Setters
        public MonitoringMode getMode() { return mode; }
        public void setMode(MonitoringMode mode) { this.mode = mode; }
        
        public String getPrometheusUrl() { return prometheusUrl; }
        public void setPrometheusUrl(String prometheusUrl) { this.prometheusUrl = prometheusUrl; }
        
        public String getAlertManagerUrl() { return alertManagerUrl; }
        public void setAlertManagerUrl(String alertManagerUrl) { this.alertManagerUrl = alertManagerUrl; }
        
        public String getGrafanaUrl() { return grafanaUrl; }
        public void setGrafanaUrl(String grafanaUrl) { this.grafanaUrl = grafanaUrl; }
        
        public Map<String, String> getExternalEndpoints() { return externalEndpoints; }
        public void setExternalEndpoints(Map<String, String> externalEndpoints) { this.externalEndpoints = externalEndpoints; }
        
        public boolean isEnableWebhooks() { return enableWebhooks; }
        public void setEnableWebhooks(boolean enableWebhooks) { this.enableWebhooks = enableWebhooks; }
        
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
    
    private final MonitoringConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, MonitoringAdapter> adapters;
    
    public MonitoringIntegration(MonitoringConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build();
        this.objectMapper = new ObjectMapper();
        this.adapters = new ConcurrentHashMap<>();
        
        initializeAdapters();
    }
    
    /**
     * 初始化监控适配器
     */
    private void initializeAdapters() {
        switch (config.getMode()) {
            case EMBEDDED:
                adapters.put("prometheus", new PrometheusAdapter(config.getPrometheusUrl()));
                adapters.put("alertmanager", new AlertManagerAdapter(config.getAlertManagerUrl()));
                adapters.put("grafana", new GrafanaAdapter(config.getGrafanaUrl()));
                logger.info("初始化内置监控栈适配器");
                break;
                
            case EXTERNAL:
                for (Map.Entry<String, String> entry : config.getExternalEndpoints().entrySet()) {
                    adapters.put(entry.getKey(), new ExternalAdapter(entry.getKey(), entry.getValue()));
                }
                logger.info("初始化外部监控栈适配器: {}", config.getExternalEndpoints().keySet());
                break;
                
            case HYBRID:
                // 同时初始化内置和外部适配器
                adapters.put("prometheus", new PrometheusAdapter(config.getPrometheusUrl()));
                adapters.put("alertmanager", new AlertManagerAdapter(config.getAlertManagerUrl()));
                for (Map.Entry<String, String> entry : config.getExternalEndpoints().entrySet()) {
                    adapters.put("external_" + entry.getKey(), new ExternalAdapter(entry.getKey(), entry.getValue()));
                }
                logger.info("初始化混合监控栈适配器");
                break;
        }
    }
    
    /**
     * 发送告警条目到监控系统
     */
    public CompletableFuture<Map<String, Boolean>> sendAlertItems(List<AlertItem> alertItems) {
        if (alertItems.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        
        logger.info("开始发送 {} 个告警条目到监控系统", alertItems.size());
        
        List<CompletableFuture<Map.Entry<String, Boolean>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, MonitoringAdapter> entry : adapters.entrySet()) {
            String adapterName = entry.getKey();
            MonitoringAdapter adapter = entry.getValue();
            
            CompletableFuture<Map.Entry<String, Boolean>> future = 
                adapter.sendAlerts(alertItems)
                    .thenApply(success -> Map.entry(adapterName, success))
                    .exceptionally(throwable -> {
                        logger.error("适配器 {} 发送告警失败: {}", adapterName, throwable.getMessage());
                        return Map.entry(adapterName, false);
                    });
            
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Boolean> results = new HashMap<>();
                for (CompletableFuture<Map.Entry<String, Boolean>> future : futures) {
                    try {
                        Map.Entry<String, Boolean> result = future.get();
                        results.put(result.getKey(), result.getValue());
                    } catch (Exception e) {
                        logger.error("获取适配器结果失败: {}", e.getMessage());
                    }
                }
                
                long successCount = results.values().stream().mapToLong(b -> b ? 1 : 0).sum();
                logger.info("告警发送完成: 成功 {}/{} 个适配器", successCount, results.size());
                
                return results;
            });
    }
    
    /**
     * 发送指标数据到监控系统
     */
    public CompletableFuture<Map<String, Boolean>> sendMetrics(Map<String, Object> metrics) {
        logger.info("发送指标数据到监控系统: {} 个指标", metrics.size());
        
        List<CompletableFuture<Map.Entry<String, Boolean>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, MonitoringAdapter> entry : adapters.entrySet()) {
            String adapterName = entry.getKey();
            MonitoringAdapter adapter = entry.getValue();
            
            if (adapter.supportsMetrics()) {
                CompletableFuture<Map.Entry<String, Boolean>> future = 
                    adapter.sendMetrics(metrics)
                        .thenApply(success -> Map.entry(adapterName, success))
                        .exceptionally(throwable -> {
                            logger.error("适配器 {} 发送指标失败: {}", adapterName, throwable.getMessage());
                            return Map.entry(adapterName, false);
                        });
                
                futures.add(future);
            }
        }
        
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Boolean> results = new HashMap<>();
                for (CompletableFuture<Map.Entry<String, Boolean>> future : futures) {
                    try {
                        Map.Entry<String, Boolean> result = future.get();
                        results.put(result.getKey(), result.getValue());
                    } catch (Exception e) {
                        logger.error("获取指标发送结果失败: {}", e.getMessage());
                    }
                }
                return results;
            });
    }
    
    /**
     * 检查监控系统健康状态
     */
    public CompletableFuture<Map<String, Boolean>> checkHealth() {
        logger.info("检查监控系统健康状态");
        
        List<CompletableFuture<Map.Entry<String, Boolean>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, MonitoringAdapter> entry : adapters.entrySet()) {
            String adapterName = entry.getKey();
            MonitoringAdapter adapter = entry.getValue();
            
            CompletableFuture<Map.Entry<String, Boolean>> future = 
                adapter.checkHealth()
                    .thenApply(healthy -> Map.entry(adapterName, healthy))
                    .exceptionally(throwable -> {
                        logger.error("适配器 {} 健康检查失败: {}", adapterName, throwable.getMessage());
                        return Map.entry(adapterName, false);
                    });
            
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Boolean> results = new HashMap<>();
                for (CompletableFuture<Map.Entry<String, Boolean>> future : futures) {
                    try {
                        Map.Entry<String, Boolean> result = future.get();
                        results.put(result.getKey(), result.getValue());
                    } catch (Exception e) {
                        logger.error("获取健康检查结果失败: {}", e.getMessage());
                    }
                }
                
                long healthyCount = results.values().stream().mapToLong(b -> b ? 1 : 0).sum();
                logger.info("健康检查完成: {}/{} 个适配器正常", healthyCount, results.size());
                
                return results;
            });
    }
    
    /**
     * 获取监控配置信息
     */
    public Map<String, Object> getMonitoringInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("mode", config.getMode().getDescription());
        info.put("adapters", adapters.keySet());
        info.put("prometheusUrl", config.getPrometheusUrl());
        info.put("alertManagerUrl", config.getAlertManagerUrl());
        info.put("grafanaUrl", config.getGrafanaUrl());
        info.put("externalEndpoints", config.getExternalEndpoints());
        info.put("enableWebhooks", config.isEnableWebhooks());
        return info;
    }
    
    /**
     * 监控适配器接口
     */
    public interface MonitoringAdapter {
        CompletableFuture<Boolean> sendAlerts(List<AlertItem> alertItems);
        CompletableFuture<Boolean> sendMetrics(Map<String, Object> metrics);
        CompletableFuture<Boolean> checkHealth();
        boolean supportsMetrics();
        String getName();
    }
    
    /**
     * Prometheus适配器
     */
    public class PrometheusAdapter implements MonitoringAdapter {
        private final String baseUrl;
        
        public PrometheusAdapter(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        @Override
        public CompletableFuture<Boolean> sendAlerts(List<AlertItem> alertItems) {
            // Prometheus通过AlertManager处理告警
            return CompletableFuture.completedFuture(true);
        }
        
        @Override
        public CompletableFuture<Boolean> sendMetrics(Map<String, Object> metrics) {
            // 将指标转换为Prometheus格式并推送
            try {
                String prometheusMetrics = convertToPrometheusFormat(metrics);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/admin/tsdb/snapshot"))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(prometheusMetrics))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();
                
                return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                        if (!success) {
                            logger.warn("Prometheus指标推送失败: status={}, body={}", 
                                       response.statusCode(), response.body());
                        }
                        return success;
                    });
                    
            } catch (Exception e) {
                logger.error("Prometheus指标转换失败: {}", e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        }
        
        @Override
        public CompletableFuture<Boolean> checkHealth() {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/-/healthy"))
                .GET()
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(throwable -> false);
        }
        
        @Override
        public boolean supportsMetrics() { return true; }
        
        @Override
        public String getName() { return "Prometheus"; }
        
        private String convertToPrometheusFormat(Map<String, Object> metrics) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                String key = entry.getKey().replaceAll("[^a-zA-Z0-9_]", "_");
                Object value = entry.getValue();
                if (value instanceof Number) {
                    sb.append(key).append(" ").append(value).append("\n");
                }
            }
            return sb.toString();
        }
    }
    
    /**
     * AlertManager适配器
     */
    public class AlertManagerAdapter implements MonitoringAdapter {
        private final String baseUrl;
        
        public AlertManagerAdapter(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        @Override
        public CompletableFuture<Boolean> sendAlerts(List<AlertItem> alertItems) {
            try {
                List<Map<String, Object>> alerts = new ArrayList<>();
                
                for (AlertItem item : alertItems) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("labels", Map.of(
                        "alertname", "dbcli_alert",
                        "severity", item.getLevel().name().toLowerCase(),
                        "system", item.getSystemName(),
                        "database", item.getDatabaseName(),
                        "instance", item.getNodeIp(),
                        "metric", item.getMetricDescription()
                    ));
                    alert.put("annotations", Map.of(
                        "summary", item.getAlertMessage(),
                        "description", String.format("系统: %s, 数据库: %s, 节点: %s, 指标: %s=%s", 
                            item.getSystemName(), item.getDatabaseName(), item.getNodeIp(),
                            item.getMetricDescription(), item.getMetricValue())
                    ));
                    alerts.add(alert);
                }
                
                String json = objectMapper.writeValueAsString(alerts);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/alerts"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();
                
                return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                        if (success) {
                            logger.info("成功发送 {} 个告警到AlertManager", alertItems.size());
                        } else {
                            logger.warn("AlertManager告警发送失败: status={}, body={}", 
                                       response.statusCode(), response.body());
                        }
                        return success;
                    });
                    
            } catch (Exception e) {
                logger.error("AlertManager告警发送异常: {}", e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        }
        
        @Override
        public CompletableFuture<Boolean> sendMetrics(Map<String, Object> metrics) {
            return CompletableFuture.completedFuture(true); // AlertManager不处理指标
        }
        
        @Override
        public CompletableFuture<Boolean> checkHealth() {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/-/healthy"))
                .GET()
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(throwable -> false);
        }
        
        @Override
        public boolean supportsMetrics() { return false; }
        
        @Override
        public String getName() { return "AlertManager"; }
    }
    
    /**
     * Grafana适配器
     */
    public class GrafanaAdapter implements MonitoringAdapter {
        private final String baseUrl;
        
        public GrafanaAdapter(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        @Override
        public CompletableFuture<Boolean> sendAlerts(List<AlertItem> alertItems) {
            // Grafana通过AlertManager处理告警
            return CompletableFuture.completedFuture(true);
        }
        
        @Override
        public CompletableFuture<Boolean> sendMetrics(Map<String, Object> metrics) {
            return CompletableFuture.completedFuture(true); // Grafana从Prometheus读取指标
        }
        
        @Override
        public CompletableFuture<Boolean> checkHealth() {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/health"))
                .GET()
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(throwable -> false);
        }
        
        @Override
        public boolean supportsMetrics() { return false; }
        
        @Override
        public String getName() { return "Grafana"; }
    }
    
    /**
     * 外部监控系统适配器
     */
    public class ExternalAdapter implements MonitoringAdapter {
        private final String name;
        private final String endpoint;
        
        public ExternalAdapter(String name, String endpoint) {
            this.name = name;
            this.endpoint = endpoint;
        }
        
        @Override
        public CompletableFuture<Boolean> sendAlerts(List<AlertItem> alertItems) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("alerts", alertItems);
                payload.put("timestamp", System.currentTimeMillis());
                payload.put("source", "dbcli");
                
                String json = objectMapper.writeValueAsString(payload);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/alerts"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();
                
                return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                        if (success) {
                            logger.info("成功发送 {} 个告警到外部系统 {}", alertItems.size(), name);
                        } else {
                            logger.warn("外部系统 {} 告警发送失败: status={}", name, response.statusCode());
                        }
                        return success;
                    });
                    
            } catch (Exception e) {
                logger.error("外部系统 {} 告警发送异常: {}", name, e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        }
        
        @Override
        public CompletableFuture<Boolean> sendMetrics(Map<String, Object> metrics) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("metrics", metrics);
                payload.put("timestamp", System.currentTimeMillis());
                payload.put("source", "dbcli");
                
                String json = objectMapper.writeValueAsString(payload);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/metrics"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();
                
                return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300);
                    
            } catch (Exception e) {
                logger.error("外部系统 {} 指标发送异常: {}", name, e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        }
        
        @Override
        public CompletableFuture<Boolean> checkHealth() {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/health"))
                .GET()
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(throwable -> false);
        }
        
        @Override
        public boolean supportsMetrics() { return true; }
        
        @Override
        public String getName() { return name; }
    }
}