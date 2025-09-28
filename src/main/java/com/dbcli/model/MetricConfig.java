package com.dbcli.model;

import java.util.List;

/**
 * 指标配置模型
 */
public class MetricConfig {
    private String type;
    private String name;
    private String description;
    private String sql;
    private List<String> columns;
    private ExecutionStrategy executionStrategy;
    private Threshold threshold;

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public ExecutionStrategy getExecutionStrategy() {
        return executionStrategy;
    }

    public void setExecutionStrategy(ExecutionStrategy executionStrategy) {
        this.executionStrategy = executionStrategy;
    }

    public Threshold getThreshold() {
        return threshold;
    }

    public void setThreshold(Threshold threshold) {
        this.threshold = threshold;
    }

    public Threshold getThresholds() {
        return threshold;
    }

    /**
     * 执行策略
     */
    public static class ExecutionStrategy {
        private String mode;
        private RetryPolicy retryPolicy;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }

        public void setRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
        }

        public boolean isEnabled() {
            return true; // 默认启用
        }
    }

    /**
     * 重试策略
     */
    public static class RetryPolicy {
        private boolean enabled;
        private int maxAttempts;
        private long backoffMs;
        private long delayMs;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBackoffMs() {
            return backoffMs;
        }

        public void setBackoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }

    /**
     * 阈值配置
     */
    public static class Threshold {
        private String level;
        private String operator;
        private Object value;

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }
}