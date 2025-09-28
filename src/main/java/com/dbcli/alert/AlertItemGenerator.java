package com.dbcli.alert;

import com.dbcli.model.MetricResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警条目生成器
 * 根据高亮指标值生成标准化告警条目
 */
public class AlertItemGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertItemGenerator.class);
    
    // 告警级别定义
    public enum AlertLevel {
        CRITICAL("严重", 1),
        WARNING("警告", 2),
        INFO("信息", 3);
        
        private final String description;
        private final int priority;
        
        AlertLevel(String description, int priority) {
            this.description = description;
            this.priority = priority;
        }
        
        public String getDescription() { return description; }
        public int getPriority() { return priority; }
    }
    
    // 告警条目
    public static class AlertItem {
        private String alertId;
        private AlertLevel level;
        private String systemName;
        private String databaseName;
        private String nodeIp;
        private String metricDescription;
        private String metricValue;
        private String alertMessage;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
        
        public AlertItem() {
            this.alertId = generateAlertId();
            this.timestamp = LocalDateTime.now();
            this.metadata = new HashMap<>();
        }
        
        private String generateAlertId() {
            return "ALERT_" + System.currentTimeMillis() + "_" + 
                   Integer.toHexString(new Random().nextInt());
        }
        
        // Getters and Setters
        public String getAlertId() { return alertId; }
        public void setAlertId(String alertId) { this.alertId = alertId; }
        
        public AlertLevel getLevel() { return level; }
        public void setLevel(AlertLevel level) { this.level = level; }
        
        public String getSystemName() { return systemName; }
        public void setSystemName(String systemName) { this.systemName = systemName; }
        
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        
        public String getNodeIp() { return nodeIp; }
        public void setNodeIp(String nodeIp) { this.nodeIp = nodeIp; }
        
        public String getMetricDescription() { return metricDescription; }
        public void setMetricDescription(String metricDescription) { this.metricDescription = metricDescription; }
        
        public String getMetricValue() { return metricValue; }
        public void setMetricValue(String metricValue) { this.metricValue = metricValue; }
        
        public String getAlertMessage() { return alertMessage; }
        public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s - %s@%s:%s - %s=%s - %s", 
                level.getDescription(),
                timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                systemName, databaseName, nodeIp,
                metricDescription, metricValue, alertMessage);
        }
    }
    
    // 告警规则配置
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();
    
    public AlertItemGenerator() {
        initializeDefaultRules();
    }
    
    /**
     * 初始化默认告警规则
     */
    private void initializeDefaultRules() {
        // CPU使用率告警规则
        addAlertRule("cpu_usage", new AlertRule()
            .addThreshold(AlertLevel.CRITICAL, 90.0, "CPU使用率过高")
            .addThreshold(AlertLevel.WARNING, 80.0, "CPU使用率较高"));
            
        // 内存使用率告警规则
        addAlertRule("memory_usage", new AlertRule()
            .addThreshold(AlertLevel.CRITICAL, 95.0, "内存使用率过高")
            .addThreshold(AlertLevel.WARNING, 85.0, "内存使用率较高"));
            
        // 磁盘使用率告警规则
        addAlertRule("disk_usage", new AlertRule()
            .addThreshold(AlertLevel.CRITICAL, 95.0, "磁盘空间不足")
            .addThreshold(AlertLevel.WARNING, 85.0, "磁盘空间较少"));
            
        // 连接数告警规则
        addAlertRule("connection_count", new AlertRule()
            .addThreshold(AlertLevel.CRITICAL, 1000.0, "数据库连接数过多")
            .addThreshold(AlertLevel.WARNING, 800.0, "数据库连接数较多"));
            
        // 响应时间告警规则
        addAlertRule("response_time", new AlertRule()
            .addThreshold(AlertLevel.CRITICAL, 5000.0, "响应时间过长")
            .addThreshold(AlertLevel.WARNING, 3000.0, "响应时间较长"));
    }
    
    /**
     * 添加告警规则
     */
    public void addAlertRule(String metricKey, AlertRule rule) {
        alertRules.put(metricKey, rule);
        logger.info("添加告警规则: {} -> {}", metricKey, rule);
    }
    
    /**
     * 根据指标结果生成告警条目
     */
    public List<AlertItem> generateAlertItems(String systemName, String databaseName, 
                                            String nodeIp, List<MetricResult> metricResults) {
        List<AlertItem> alertItems = new ArrayList<>();
        
        for (MetricResult result : metricResults) {
            if (result.isSuccess() && result.hasHighlightedValues()) {
                List<AlertItem> items = processMetricResult(systemName, databaseName, nodeIp, result);
                alertItems.addAll(items);
            }
        }
        
        logger.info("为系统 {}@{}:{} 生成了 {} 个告警条目", 
                   systemName, databaseName, nodeIp, alertItems.size());
        
        return alertItems;
    }
    
    /**
     * 处理单个指标结果
     */
    private List<AlertItem> processMetricResult(String systemName, String databaseName, 
                                              String nodeIp, MetricResult result) {
        List<AlertItem> alertItems = new ArrayList<>();
        
        // 获取高亮值
        Map<String, Object> highlightedValues = result.getHighlightedValues();
        
        for (Map.Entry<String, Object> entry : highlightedValues.entrySet()) {
            String metricKey = entry.getKey();
            Object value = entry.getValue();
            
            // 检查是否有对应的告警规则
            AlertRule rule = findMatchingRule(metricKey, result.getMetricName());
            if (rule != null) {
                AlertItem alertItem = createAlertItem(systemName, databaseName, nodeIp, 
                                                    result, metricKey, value, rule);
                if (alertItem != null) {
                    alertItems.add(alertItem);
                }
            }
        }
        
        return alertItems;
    }
    
    /**
     * 查找匹配的告警规则
     */
    private AlertRule findMatchingRule(String metricKey, String metricName) {
        // 精确匹配
        AlertRule rule = alertRules.get(metricKey);
        if (rule != null) {
            return rule;
        }
        
        // 模糊匹配
        for (Map.Entry<String, AlertRule> entry : alertRules.entrySet()) {
            String ruleKey = entry.getKey();
            if (metricKey.toLowerCase().contains(ruleKey.toLowerCase()) ||
                metricName.toLowerCase().contains(ruleKey.toLowerCase())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 创建告警条目
     */
    private AlertItem createAlertItem(String systemName, String databaseName, String nodeIp,
                                    MetricResult result, String metricKey, Object value, AlertRule rule) {
        try {
            double numericValue = parseNumericValue(value);
            AlertLevel level = rule.evaluateAlertLevel(numericValue);
            
            if (level != null) {
                AlertItem alertItem = new AlertItem();
                alertItem.setLevel(level);
                alertItem.setSystemName(systemName);
                alertItem.setDatabaseName(databaseName);
                alertItem.setNodeIp(nodeIp);
                alertItem.setMetricDescription(result.getMetricName() + "." + metricKey);
                alertItem.setMetricValue(String.valueOf(value));
                
                // 生成告警消息
                String message = rule.getAlertMessage(level);
                alertItem.setAlertMessage(String.format("%s (当前值: %s)", message, value));
                
                // 添加元数据
                alertItem.getMetadata().put("originalMetricName", result.getMetricName());
                alertItem.getMetadata().put("metricKey", metricKey);
                alertItem.getMetadata().put("numericValue", numericValue);
                alertItem.getMetadata().put("threshold", rule.getThreshold(level));
                
                return alertItem;
            }
        } catch (Exception e) {
            logger.warn("创建告警条目失败: metricKey={}, value={}, error={}", 
                       metricKey, value, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 解析数值
     */
    private double parseNumericValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        String strValue = String.valueOf(value).trim();
        // 移除百分号
        if (strValue.endsWith("%")) {
            strValue = strValue.substring(0, strValue.length() - 1);
        }
        
        return Double.parseDouble(strValue);
    }
    
    /**
     * 告警规则类
     */
    public static class AlertRule {
        private final Map<AlertLevel, Double> thresholds = new HashMap<>();
        private final Map<AlertLevel, String> messages = new HashMap<>();
        
        public AlertRule addThreshold(AlertLevel level, double threshold, String message) {
            thresholds.put(level, threshold);
            messages.put(level, message);
            return this;
        }
        
        public AlertLevel evaluateAlertLevel(double value) {
            // 按优先级从高到低检查
            for (AlertLevel level : Arrays.asList(AlertLevel.CRITICAL, AlertLevel.WARNING, AlertLevel.INFO)) {
                Double threshold = thresholds.get(level);
                if (threshold != null && value >= threshold) {
                    return level;
                }
            }
            return null;
        }
        
        public String getAlertMessage(AlertLevel level) {
            return messages.getOrDefault(level, "指标异常");
        }
        
        public Double getThreshold(AlertLevel level) {
            return thresholds.get(level);
        }
        
        @Override
        public String toString() {
            return "AlertRule{thresholds=" + thresholds + ", messages=" + messages + '}';
        }
    }
}