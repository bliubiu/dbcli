package com.dbcli.executor;

import com.dbcli.database.DatabaseManager;
import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import com.dbcli.model.DatabaseNode;
import com.dbcli.util.DataMaskUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dbcli.util.LogManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * SQL查询执行器
 */
public class QueryExecutor {
    private static final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);
    
    private final DatabaseManager databaseManager;
    private final ExecutorService executorService;
    private final int queryTimeout;
    
    public QueryExecutor(DatabaseManager databaseManager, int threadCount) {
        this.databaseManager = databaseManager;
        this.executorService = Executors.newFixedThreadPool(threadCount);
        this.queryTimeout = 30; // 30秒查询超时
    }
    
    /**
     * 执行单个指标查询
     */
    public CompletableFuture<MetricResult> executeMetricAsync(String dbType, String systemName, 
                                                            MetricConfig metric, String nodeRole) {
        return CompletableFuture.supplyAsync(() -> {
            LogManager.setDbContext(dbType, systemName, metric != null ? metric.getName() : null);
            LogManager.setOperation("execute_metric");
            try {
                return executeMetricWithRetry(dbType, systemName, metric, nodeRole);
            } finally {
                LogManager.clearDbContext();
                LogManager.clearOperation();
            }
        }, executorService);
    }

    /**
     * 按指定节点执行单个指标查询（异步）
     */
    public CompletableFuture<MetricResult> executeMetricAsyncForNode(String dbType, String systemName,
                                                                     MetricConfig metric, DatabaseNode node) {
        return CompletableFuture.supplyAsync(() -> {
            LogManager.setDbContext(dbType, systemName, metric != null ? metric.getName() : null);
            LogManager.setOperation("execute_metric");
            try {
                return executeMetricWithRetryForNode(dbType, systemName, metric, node);
            } finally {
                LogManager.clearDbContext();
                LogManager.clearOperation();
            }
        }, executorService);
    }

    /**
     * 带重试（指定节点）
     */
    protected MetricResult executeMetricWithRetryForNode(String dbType, String systemName,
                                                       MetricConfig metric, DatabaseNode node) {
        MetricConfig.ExecutionStrategy strategy = metric.getExecutionStrategy();
        MetricConfig.RetryPolicy retryPolicy = strategy != null ? strategy.getRetryPolicy() : null;

        int maxAttempts = (retryPolicy != null && retryPolicy.isEnabled()) ?
                retryPolicy.getMaxAttempts() : 1;
        long delayMs = (retryPolicy != null) ? retryPolicy.getDelayMs() : 0;
        long backoffMs = (retryPolicy != null) ? retryPolicy.getBackoffMs() : 0;

        MetricResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 1 && delayMs > 0) {
                    Thread.sleep(delayMs + (backoffMs * (attempt - 1)));
                }

                MetricResult result = executeSingleMetricForNode(dbType, systemName, metric, node);

                if (result.isSuccess()) {
                    if (attempt > 1) {
                        logger.info("指标执行成功 (第{}次尝试): {} - {} - {}", attempt, metric.getName(), systemName, node.getHost());
                    }
                    return result;
                }

                lastResult = result;

            } catch (Exception e) {
                logger.warn("指标执行失败 (第{}次尝试): {} - {} - {} - {}",
                        attempt, metric.getName(), systemName, node.getHost(), e.getMessage());

                if (lastResult == null) {
                    lastResult = createErrorResult(dbType, systemName, metric, e.getMessage());
                }
            }
        }

        logger.error("指标执行最终失败 ({}次尝试): {} - {} - {}", maxAttempts, metric.getName(), systemName, node.getHost());
        return lastResult;
    }

    /**
     * 执行单个指标（指定节点）
     */
    private MetricResult executeSingleMetricForNode(String dbType, String systemName,
                                                    MetricConfig metric, DatabaseNode node) throws SQLException {
        
        // 检查节点是否已被标记为失败（黑名单检查）
        String nodeKey = String.format("%s-%s-%s-%s", dbType, systemName, node.getHost(), node.getSvcName());
        if (databaseManager.isNodeBlacklisted(nodeKey)) {
            String errorMsg = "节点连接已标记为失败: " + nodeKey;
            logger.warn("跳过失败节点: {} - {} - {}@{} - {}", 
                    metric.getName(), systemName, node.getRole(), node.getHost(), errorMsg);
            throw new SQLException(errorMsg);
        }
        
        try (Connection conn = databaseManager.getConnectionForNode(dbType, systemName, node)) {

            MetricResult result = new MetricResult(
                    systemName,
                    dbType,
                    DataMaskUtil.maskIpAddress(node.getHost()),
                    metric.getName(),
                    metric.getDescription(),
                    metric.getType(),
                    dbType
            );

            result.setNodeRole(node.getRole());
            result.setCollectTime(LocalDateTime.now());

            if ("SINGLE".equals(metric.getType())) {
                executeSingleValueQuery(conn, metric, result);
            } else if ("MULTI".equals(metric.getType())) {
                executeMultiValueQuery(conn, metric, result);
            } else {
                throw new SQLException("不支持的指标类型: " + metric.getType());
            }

            result.setSuccess(true);
            checkThreshold(metric, result);
            return result;

        } catch (SQLException e) {
            logger.error("执行指标查询失败: {} - {} - {}@{} - {}", metric.getName(), systemName,
                    node.getRole(), node.getHost(), e.getMessage());
            return createErrorResult(dbType, systemName, metric, e.getMessage());
        }
    }
    
    /**
     * 带重试机制的指标执行
     */
    protected MetricResult executeMetricWithRetry(String dbType, String systemName, 
                                              MetricConfig metric, String nodeRole) {
        MetricConfig.ExecutionStrategy strategy = metric.getExecutionStrategy();
        MetricConfig.RetryPolicy retryPolicy = strategy != null ? strategy.getRetryPolicy() : null;
        
        int maxAttempts = (retryPolicy != null && retryPolicy.isEnabled()) ? 
                         retryPolicy.getMaxAttempts() : 1;
        long delayMs = (retryPolicy != null) ? retryPolicy.getDelayMs() : 0;
        long backoffMs = (retryPolicy != null) ? retryPolicy.getBackoffMs() : 0;
        
        MetricResult lastResult = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 1 && delayMs > 0) {
                    Thread.sleep(delayMs + (backoffMs * (attempt - 1)));
                }
                
                MetricResult result = executeSingleMetric(dbType, systemName, metric, nodeRole);
                
                if (result.isSuccess()) {
                    if (attempt > 1) {
                        logger.info("指标执行成功 (第{}次尝试): {} - {}", attempt, metric.getName(), systemName);
                    }
                    return result;
                }
                
                lastResult = result;
                
            } catch (Exception e) {
                logger.warn("指标执行失败 (第{}次尝试): {} - {} - {}", 
                           attempt, metric.getName(), systemName, e.getMessage());
                
                if (lastResult == null) {
                    lastResult = createErrorResult(dbType, systemName, metric, e.getMessage());
                }
            }
        }
        
        logger.error("指标执行最终失败 ({}次尝试): {} - {}", maxAttempts, metric.getName(), systemName);
        return lastResult;
    }
    
    /**
     * 执行单个指标
     */
    private MetricResult executeSingleMetric(String dbType, String systemName, 
                                           MetricConfig metric, String nodeRole) throws SQLException {
        
        try (Connection conn = databaseManager.getConnection(dbType, systemName, nodeRole)) {
            
            // 推断所用节点IP（保留后两段），用于单值指标行合并
            String nodeIpDisplay = "unknown";
            java.util.List<DatabaseNode> candidates = databaseManager.getNodes(dbType, systemName);
            if (candidates != null && !candidates.isEmpty()) {
                DatabaseNode chosen = null;
                if (nodeRole != null && !nodeRole.isEmpty()) {
                    for (DatabaseNode n : candidates) {
                        if (n.getRole() != null && nodeRole.equalsIgnoreCase(n.getRole())) {
                            chosen = n;
                            break;
                        }
                    }
                }
                if (chosen == null) {
                    chosen = candidates.get(0);
                }
                if (chosen.getHost() != null) {
                    nodeIpDisplay = DataMaskUtil.maskIpAddress(chosen.getHost());
                }
            }

            MetricResult result = new MetricResult(
                systemName, 
                dbType, 
                nodeIpDisplay,
                metric.getName(),
                metric.getDescription(),
                metric.getType(),
                dbType
            );
            
            result.setCollectTime(LocalDateTime.now());
            
            if ("SINGLE".equals(metric.getType())) {
                executeSingleValueQuery(conn, metric, result);
            } else if ("MULTI".equals(metric.getType())) {
                executeMultiValueQuery(conn, metric, result);
            } else {
                throw new SQLException("不支持的指标类型: " + metric.getType());
            }
            
            result.setSuccess(true);
            
            // 检查阈值
            checkThreshold(metric, result);
            
            return result;
            
        } catch (SQLException e) {
            logger.error("执行指标查询失败: {} - {} - {}", metric.getName(), systemName, e.getMessage());
            return createErrorResult(dbType, systemName, metric, e.getMessage());
        }
    }
    
    /**
     * 执行单值查询
     */
    private void executeSingleValueQuery(Connection conn, MetricConfig metric, MetricResult result) 
            throws SQLException {
        
        try (PreparedStatement stmt = conn.prepareStatement(metric.getSql())) {
            stmt.setQueryTimeout(queryTimeout);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // 检查是否定义了columns（多列单值指标）
                    if (metric.getColumns() != null && !metric.getColumns().isEmpty()) {
                        // 处理多列单值指标
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        List<String> columnNames = getColumnNames(metric, metaData, columnCount);
                        result.setColumns(columnNames);
                        
                        // 创建单行数据
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = columnNames.get(i - 1);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        
                        // 设置为多值数据（虽然只有一行）
                        result.setMultiValues(Arrays.asList(row));
                        
                        logger.debug("多列单值指标收集成功: {} - {} 列", metric.getName(), columnCount);
                    } else {
                        // 传统单值指标
                        Object value = extractSingleValue(rs, metric);
                        result.setValue(value);
                        
                        logger.debug("单值指标收集成功: {} = {}", metric.getName(), value);
                    }
                } else {
                    logger.warn("单值指标查询无结果: {}", metric.getName());
                    result.setValue(null);
                }
            }
        }
    }
    
    /**
     * 从结果集中提取单个值，处理SHOW命令的特殊情况
     */
    private Object extractSingleValue(ResultSet rs, MetricConfig metric) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // 检查是否是SHOW命令（通常有Variable_name和Value两列）
        if (columnCount == 2) {
            String col1Name = metaData.getColumnLabel(1).toLowerCase();
            String col2Name = metaData.getColumnLabel(2).toLowerCase();
            
            // 如果是SHOW STATUS/VARIABLES格式，返回Value列
            if ((col1Name.contains("variable") && col2Name.contains("value")) ||
                (col1Name.contains("name") && col2Name.contains("value"))) {
                return rs.getObject(2); // 返回Value列
            }
        }
        
        // 默认返回第一列
        return rs.getObject(1);
    }
    
    /**
     * 执行多值查询
     */
    private void executeMultiValueQuery(Connection conn, MetricConfig metric, MetricResult result) 
            throws SQLException {
        
        List<Map<String, Object>> values = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(metric.getSql())) {
            stmt.setQueryTimeout(queryTimeout);
            
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                // 获取列名映射
                List<String> columnNames = getColumnNames(metric, metaData, columnCount);
                
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = columnNames.get(i - 1);
                        Object value = rs.getObject(i);
                        // 不对结果集做字段脱敏，保持原值输出；列名按配置/推断的 columnNames 统一
                        row.put(columnName, value);
                    }
                    
                    values.add(row);
                }
                // 将列名写入结果，供后续报表使用
                result.setColumns(columnNames);
            }
        }
        
        result.setMultiValues(values);
        logger.debug("多值指标收集成功: {} - {} 行数据", metric.getName(), values.size());
    }
    
    /**
     * 获取列名映射
     */
    private List<String> getColumnNames(MetricConfig metric, ResultSetMetaData metaData, int columnCount) 
            throws SQLException {
        
        List<String> columnNames = new ArrayList<>();
        
        // 如果配置中指定了列名，使用配置的列名
        if (metric.getColumns() != null && !metric.getColumns().isEmpty()) {
            List<String> configColumns = metric.getColumns();
            for (int i = 0; i < columnCount; i++) {
                if (i < configColumns.size()) {
                    columnNames.add(configColumns.get(i));
                } else {
                    columnNames.add(metaData.getColumnLabel(i + 1));
                }
            }
        } else {
            // 使用查询结果的列名
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(metaData.getColumnLabel(i));
            }
        }
        
        return columnNames;
    }
    
    /**
     * 检查阈值
     */
    private void checkThreshold(MetricConfig metric, MetricResult result) {
        MetricConfig.Threshold threshold = metric.getThreshold();
        if (threshold == null) {
            return;
        }
        
        try {
            if ("SINGLE".equals(metric.getType())) {
                checkSingleValueThreshold(threshold, result);
            } else if ("MULTI".equals(metric.getType())) {
                checkMultiValueThreshold(threshold, result);
            }
        } catch (Exception e) {
            logger.warn("阈值检查失败: {} - {}", metric.getName(), e.getMessage());
        }
    }
    
    /**
     * 检查单值阈值
     */
    private void checkSingleValueThreshold(MetricConfig.Threshold threshold, MetricResult result) {
        Object value = result.getValue();
        if (value == null) {
            return;
        }
        
        boolean exceedsThreshold = compareValue(value, threshold.getOperator(), threshold.getValue());
        
        if (exceedsThreshold) {
            result.setThresholdLevel(threshold.getLevel());
            logger.info("指标超过阈值: {} {} {} (实际值: {})", 
                       result.getMetricName(), threshold.getOperator(), threshold.getValue(), value);
        }
    }
    
    /**
     * 检查多值阈值
     */
    private void checkMultiValueThreshold(MetricConfig.Threshold threshold, MetricResult result) {
        List<Map<String, Object>> values = result.getMultiValues();
        if (values == null || values.isEmpty()) {
            return;
        }
        
        // 对于多值指标，检查每一行数据
        for (Map<String, Object> row : values) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String columnName = entry.getKey();
                Object value = entry.getValue();
                
                // 检查是否有针对特定列的阈值配置
                if (threshold.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> thresholdMap = (Map<String, Object>) threshold.getValue();
                    
                    if (thresholdMap.containsKey(columnName)) {
                        Object thresholdValue = thresholdMap.get(columnName);
                        boolean exceedsThreshold = compareValue(value, threshold.getOperator(), thresholdValue);
                        
                        if (exceedsThreshold) {
                            result.setThresholdLevel(threshold.getLevel());
                            row.put("_threshold_exceeded", true);
                            logger.info("多值指标超过阈值: {} - {} {} {} (实际值: {})", 
                                       result.getMetricName(), columnName, threshold.getOperator(), 
                                       thresholdValue, value);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 比较值
     */
    private boolean compareValue(Object actualValue, String operator, Object thresholdValue) {
        if (actualValue == null || thresholdValue == null) {
            return false;
        }
        
        try {
            double actual = Double.parseDouble(actualValue.toString());
            double threshold = Double.parseDouble(thresholdValue.toString());
            
            switch (operator) {
                case ">":
                    return actual > threshold;
                case ">=":
                    return actual >= threshold;
                case "<":
                    return actual < threshold;
                case "<=":
                    return actual <= threshold;
                case "=":
                case "==":
                    return Math.abs(actual - threshold) < 0.0001;
                case "!=":
                    return Math.abs(actual - threshold) >= 0.0001;
                default:
                    logger.warn("不支持的比较操作符: {}", operator);
                    return false;
            }
        } catch (NumberFormatException e) {
            // 对于非数值类型，使用字符串比较
            return actualValue.toString().equals(thresholdValue.toString());
        }
    }
    
    /**
     * 创建错误结果
     */
    private MetricResult createErrorResult(String dbType, String systemName, 
                                         MetricConfig metric, String errorMessage) {
        MetricResult result = new MetricResult(
            systemName,
            dbType,
            "unknown",
            metric.getName(),
            metric.getDescription(),
            metric.getType(),
            dbType
        );
        
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setCollectTime(LocalDateTime.now());
        
        return result;
    }
    
    /**
     * 批量执行指标
     */
    public List<CompletableFuture<MetricResult>> executeMetricsBatch(String dbType, String systemName,
                                                                   List<MetricConfig> metrics, String nodeRole) {
        List<CompletableFuture<MetricResult>> futures = new ArrayList<>();
        
        for (MetricConfig metric : metrics) {
            CompletableFuture<MetricResult> future = executeMetricAsync(dbType, systemName, metric, nodeRole);
            futures.add(future);
        }
        
        return futures;
    }
    
    /**
     * 等待所有查询完成
     */
    public List<MetricResult> waitForResults(List<CompletableFuture<MetricResult>> futures, long timeoutSeconds) {
        List<MetricResult> results = new ArrayList<>();
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        
        try {
            allOf.get(timeoutSeconds, TimeUnit.SECONDS);
            
            for (CompletableFuture<MetricResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    logger.error("获取查询结果失败", e);
                }
            }
            
        } catch (TimeoutException e) {
            logger.warn("查询执行超时，已完成的查询: {}/{}", 
                       futures.stream().mapToInt(f -> f.isDone() ? 1 : 0).sum(), futures.size());
            
            // 收集已完成的结果
            for (CompletableFuture<MetricResult> future : futures) {
                if (future.isDone()) {
                    try {
                        results.add(future.get());
                    } catch (Exception ex) {
                        logger.error("获取已完成查询结果失败", ex);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("等待查询结果失败", e);
        }
        
        return results;
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        logger.info("关闭查询执行器...");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("查询执行器未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("查询执行器已关闭");
    }
    
    /**
     * 获取执行器统计信息
     */
    public ExecutorStats getStats() {
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
            return new ExecutorStats(
                tpe.getActiveCount(),
                tpe.getCompletedTaskCount(),
                tpe.getTaskCount(),
                tpe.getPoolSize()
            );
        }
        
        return new ExecutorStats(0, 0, 0, 0);
    }
    
    /**
     * 执行器统计信息
     */
    public static class ExecutorStats {
        private final int activeThreads;
        private final long completedTasks;
        private final long totalTasks;
        private final int poolSize;
        
        public ExecutorStats(int activeThreads, long completedTasks, long totalTasks, int poolSize) {
            this.activeThreads = activeThreads;
            this.completedTasks = completedTasks;
            this.totalTasks = totalTasks;
            this.poolSize = poolSize;
        }
        
        public int getActiveThreads() { return activeThreads; }
        public long getCompletedTasks() { return completedTasks; }
        public long getTotalTasks() { return totalTasks; }
        public int getPoolSize() { return poolSize; }
        
        @Override
        public String toString() {
            return String.format("活跃线程: %d, 已完成任务: %d, 总任务: %d, 线程池大小: %d", 
                               activeThreads, completedTasks, totalTasks, poolSize);
        }
    }
}