package com.dbcli.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标结果模型
 */
public class MetricResult {
    private String systemName;
    private String databaseName;
    private String nodeIp;
    private String metricName;
    private String metricDescription;
    private String metricType;
    private Object value;
    private List<Map<String, Object>> multiValues;
    private LocalDateTime executeTime;
    private LocalDateTime collectTime;
    private boolean success;
    private String errorMessage;
    private String dbType;
    private String thresholdLevel; // high, medium, low
    private String unit; // 指标单位
    private String nodeRole; // primary, standby, master, slave等

    // Constructors
    public MetricResult() {}

    public MetricResult(String systemName, String databaseName, String nodeIp, 
                       String metricName, String metricDescription, String metricType, String dbType) {
        this.systemName = systemName;
        this.databaseName = databaseName;
        this.nodeIp = nodeIp;
        this.metricName = metricName;
        this.metricDescription = metricDescription;
        this.metricType = metricType;
        this.dbType = dbType;
        this.executeTime = LocalDateTime.now();
    }

    // Getters and Setters
    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getNodeIp() {
        return nodeIp;
    }

    public void setNodeIp(String nodeIp) {
        this.nodeIp = nodeIp;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getMetricDescription() {
        return metricDescription;
    }

    public void setMetricDescription(String metricDescription) {
        this.metricDescription = metricDescription;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public List<Map<String, Object>> getMultiValues() {
        return multiValues;
    }

    public void setMultiValues(List<Map<String, Object>> multiValues) {
        this.multiValues = multiValues;
    }

    public LocalDateTime getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(LocalDateTime executeTime) {
        this.executeTime = executeTime;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public LocalDateTime getCollectTime() {
        return collectTime;
    }

    public void setCollectTime(LocalDateTime collectTime) {
        this.collectTime = collectTime;
    }

    public String getThresholdLevel() {
        return thresholdLevel;
    }

    public void setThresholdLevel(String thresholdLevel) {
        this.thresholdLevel = thresholdLevel;
    }

    public String getNodeRole() {
        return nodeRole;
    }

    public void setNodeRole(String nodeRole) {
        this.nodeRole = nodeRole;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    // 测试兼容性方法
    public String getNodeName() {
        return nodeIp; // 使用nodeIp作为nodeName
    }

    public Object getData() {
        return value; // 使用value作为data
    }

    // 测试中使用的额外方法
    public String getDescription() {
        return metricDescription;
    }

    public void setDescription(String description) {
        this.metricDescription = description;
    }

    public String getType() {
        return metricType;
    }

    public void setType(String type) {
        this.metricType = type;
    }

    public String getStatus() {
        return success ? "SUCCESS" : "ERROR";
    }

    public void setStatus(String status) {
        this.success = "SUCCESS".equals(status);
    }

    public String getDatabaseType() {
        return dbType;
    }

    public void setDatabaseType(String databaseType) {
        this.dbType = databaseType;
    }

    public String getNodeHost() {
        return nodeIp;
    }

    public void setNodeHost(String nodeHost) {
        this.nodeIp = nodeHost;
    }

    // 时间戳相关方法
    private Long timestamp;
    private Long executionTime;

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    // 阈值相关方法
    private String thresholdValue;
    private String thresholdOperator;

    public String getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(String thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public String getThresholdOperator() {
        return thresholdOperator;
    }

    public void setThresholdOperator(String thresholdOperator) {
        this.thresholdOperator = thresholdOperator;
    }

    // 多值数据相关方法
    private List<String> columns;
    private List<Map<String, Object>> rows;

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }
    
    // 监控集成相关方法
    private Map<String, Object> highlightedValues;
    
    /**
     * 获取高亮值
     */
    public Map<String, Object> getHighlightedValues() {
        return highlightedValues;
    }
    
    /**
     * 设置高亮值
     */
    public void setHighlightedValues(Map<String, Object> highlightedValues) {
        this.highlightedValues = highlightedValues;
    }
    
    /**
     * 是否有高亮值
     */
    public boolean hasHighlightedValues() {
        return highlightedValues != null && !highlightedValues.isEmpty();
    }
    
    /**
     * 获取单值结果
     */
    public Object getSingleValue() {
        return value;
    }
    
    /**
     * 获取多值结果
     */
    public List<Map<String, Object>> getMultipleValues() {
        return multiValues != null ? multiValues : rows;
    }
}
