package com.dbcli.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 性能指标收集器
 * 收集和统计系统运行时的各项性能指标
 */
public class PerformanceMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMetricsCollector.class);
    
    private final Map<String, OperationMetrics> operationMetrics;
    private final Map<String, DatabaseMetrics> databaseMetrics;
    private final AtomicLong startTime;
    
    public PerformanceMetricsCollector() {
        this.operationMetrics = new ConcurrentHashMap<>();
        this.databaseMetrics = new ConcurrentHashMap<>();
        this.startTime = new AtomicLong(System.currentTimeMillis());
    }
    
    /**
     * 记录操作执行时间
     */
    public void recordOperationTime(String operation, long durationMs) {
        OperationMetrics metrics = operationMetrics.computeIfAbsent(operation, 
            k -> new OperationMetrics(operation));
        metrics.recordExecution(durationMs, true);
    }
    
    /**
     * 记录操作计数
     */
    public void recordOperationCount(String operation, String status) {
        OperationMetrics metrics = operationMetrics.computeIfAbsent(operation, 
            k -> new OperationMetrics(operation));
        
        if ("success".equals(status)) {
            metrics.recordExecution(0, true);
        } else {
            metrics.recordExecution(0, false);
        }
    }
    
    /**
     * 记录数据库指标
     */
    public void recordDatabaseMetrics(String dbType, String systemName, 
                                    int activeConnections, int totalConnections) {
        String key = dbType + "." + systemName;
        DatabaseMetrics metrics = databaseMetrics.computeIfAbsent(key, 
            k -> new DatabaseMetrics(dbType, systemName));
        
        metrics.updateConnectionMetrics(activeConnections, totalConnections);
    }
    
    /**
     * 记录查询指标
     */
    public void recordQueryMetrics(String dbType, String systemName, String metricName,
                                 long executionTimeMs, boolean success) {
        String operationKey = String.format("query.%s.%s.%s", dbType, systemName, metricName);
        OperationMetrics metrics = operationMetrics.computeIfAbsent(operationKey, 
            k -> new OperationMetrics(operationKey));
        
        metrics.recordExecution(executionTimeMs, success);
        
        // 更新数据库查询统计
        String dbKey = dbType + "." + systemName;
        DatabaseMetrics dbMetrics = databaseMetrics.computeIfAbsent(dbKey, 
            k -> new DatabaseMetrics(dbType, systemName));
        dbMetrics.recordQuery(success);
    }
    
    /**
     * 获取性能快照
     */
    public MetricsSnapshot getSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setCollectionStartTime(startTime.get());
        snapshot.setSnapshotTime(System.currentTimeMillis());
        
        // 计算总体统计
        long totalOperations = 0;
        long totalSuccessful = 0;
        long totalExecutionTime = 0;
        
        for (OperationMetrics metrics : operationMetrics.values()) {
            totalOperations += metrics.getTotalCount();
            totalSuccessful += metrics.getSuccessCount();
            totalExecutionTime += metrics.getTotalExecutionTime();
        }
        
        snapshot.setTotalOperations(totalOperations);
        snapshot.setSuccessfulOperations(totalSuccessful);
        snapshot.setSuccessRate(totalOperations > 0 ? (double) totalSuccessful / totalOperations : 0.0);
        snapshot.setAverageResponseTime(totalOperations > 0 ? totalExecutionTime / totalOperations : 0);
        
        // 复制操作指标
        snapshot.setOperationMetrics(new ConcurrentHashMap<>(operationMetrics));
        
        // 复制数据库指标
        snapshot.setDatabaseMetrics(new ConcurrentHashMap<>(databaseMetrics));
        
        return snapshot;
    }
    
    /**
     * 重置所有指标
     */
    public void reset() {
        operationMetrics.clear();
        databaseMetrics.clear();
        startTime.set(System.currentTimeMillis());
        logger.info("性能指标已重置");
    }
    
    /**
     * 操作指标
     */
    public static class OperationMetrics {
        private final String operationName;
        private final LongAdder totalCount = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder totalExecutionTime = new LongAdder();
        private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxExecutionTime = new AtomicLong(0);
        
        public OperationMetrics(String operationName) {
            this.operationName = operationName;
        }
        
        public void recordExecution(long executionTimeMs, boolean success) {
            totalCount.increment();
            if (success) {
                successCount.increment();
            }
            
            if (executionTimeMs > 0) {
                totalExecutionTime.add(executionTimeMs);
                
                // 更新最小执行时间
                long currentMin = minExecutionTime.get();
                while (executionTimeMs < currentMin && 
                       !minExecutionTime.compareAndSet(currentMin, executionTimeMs)) {
                    currentMin = minExecutionTime.get();
                }
                
                // 更新最大执行时间
                long currentMax = maxExecutionTime.get();
                while (executionTimeMs > currentMax && 
                       !maxExecutionTime.compareAndSet(currentMax, executionTimeMs)) {
                    currentMax = maxExecutionTime.get();
                }
            }
        }
        
        // Getters
        public String getOperationName() { return operationName; }
        public long getTotalCount() { return totalCount.sum(); }
        public long getSuccessCount() { return successCount.sum(); }
        public long getTotalExecutionTime() { return totalExecutionTime.sum(); }
        public long getMinExecutionTime() { 
            long min = minExecutionTime.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
        public long getMaxExecutionTime() { return maxExecutionTime.get(); }
        
        public double getSuccessRate() {
            long total = getTotalCount();
            return total > 0 ? (double) getSuccessCount() / total : 0.0;
        }
        
        public double getAverageExecutionTime() {
            long total = getTotalCount();
            return total > 0 ? (double) getTotalExecutionTime() / total : 0.0;
        }
    }
    
    /**
     * 数据库指标
     */
    public static class DatabaseMetrics {
        private final String dbType;
        private final String systemName;
        private final AtomicLong activeConnections = new AtomicLong(0);
        private final AtomicLong totalConnections = new AtomicLong(0);
        private final LongAdder queryCount = new LongAdder();
        private final LongAdder successfulQueries = new LongAdder();
        
        public DatabaseMetrics(String dbType, String systemName) {
            this.dbType = dbType;
            this.systemName = systemName;
        }
        
        public void updateConnectionMetrics(int active, int total) {
            activeConnections.set(active);
            totalConnections.set(total);
        }
        
        public void recordQuery(boolean success) {
            queryCount.increment();
            if (success) {
                successfulQueries.increment();
            }
        }
        
        // Getters
        public String getDbType() { return dbType; }
        public String getSystemName() { return systemName; }
        public long getActiveConnections() { return activeConnections.get(); }
        public long getTotalConnections() { return totalConnections.get(); }
        public long getQueryCount() { return queryCount.sum(); }
        public long getSuccessfulQueries() { return successfulQueries.sum(); }
        
        public double getQuerySuccessRate() {
            long total = getQueryCount();
            return total > 0 ? (double) getSuccessfulQueries() / total : 0.0;
        }
        
        public double getConnectionUtilization() {
            long total = getTotalConnections();
            return total > 0 ? (double) getActiveConnections() / total : 0.0;
        }
    }
    
    /**
     * 指标快照
     */
    public static class MetricsSnapshot {
        private long collectionStartTime;
        private long snapshotTime;
        private long totalOperations;
        private long successfulOperations;
        private double successRate;
        private long averageResponseTime;
        private Map<String, OperationMetrics> operationMetrics;
        private Map<String, DatabaseMetrics> databaseMetrics;
        
        // Getters and Setters
        public long getCollectionStartTime() { return collectionStartTime; }
        public void setCollectionStartTime(long collectionStartTime) { this.collectionStartTime = collectionStartTime; }
        
        public long getSnapshotTime() { return snapshotTime; }
        public void setSnapshotTime(long snapshotTime) { this.snapshotTime = snapshotTime; }
        
        public long getTotalOperations() { return totalOperations; }
        public void setTotalOperations(long totalOperations) { this.totalOperations = totalOperations; }
        
        public long getSuccessfulOperations() { return successfulOperations; }
        public void setSuccessfulOperations(long successfulOperations) { this.successfulOperations = successfulOperations; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public long getAverageResponseTime() { return averageResponseTime; }
        public void setAverageResponseTime(long averageResponseTime) { this.averageResponseTime = averageResponseTime; }
        
        public Map<String, OperationMetrics> getOperationMetrics() { return operationMetrics; }
        public void setOperationMetrics(Map<String, OperationMetrics> operationMetrics) { this.operationMetrics = operationMetrics; }
        
        public Map<String, DatabaseMetrics> getDatabaseMetrics() { return databaseMetrics; }
        public void setDatabaseMetrics(Map<String, DatabaseMetrics> databaseMetrics) { this.databaseMetrics = databaseMetrics; }
        
        public long getCollectionDurationMs() {
            return snapshotTime - collectionStartTime;
        }
    }
}