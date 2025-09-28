package com.dbcli.executor;

import com.dbcli.config.ConfigLoader;
import com.dbcli.config.ConfigurationValidator;
import com.dbcli.database.DatabaseManager;
import com.dbcli.database.DatabaseSpecificConnectionManager;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import com.dbcli.monitor.PerformanceMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 增强的指标执行器
 * 集成了重试策略、熔断器、性能监控等优化功能
 */
public class EnhancedMetricsExecutor implements MetricsExecutor {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedMetricsExecutor.class);
    
    private final AdaptiveThreadPoolManager threadPoolManager;
    private final DatabaseSpecificConnectionManager connectionManager;
    private final AdaptiveRetryPolicy retryPolicy;
    private final Map<String, CircuitBreaker> circuitBreakers;
    private final PerformanceMetricsCollector metricsCollector;
    private final ConfigurationValidator validator;
    private final ConfigLoader configLoader;
    private final QueryExecutor queryExecutor;
    
    private Set<String> failedEncryptedHosts = ConcurrentHashMap.newKeySet();
    private final ExecutionStats executionStats = new ExecutionStats();
    
    public EnhancedMetricsExecutor(int concurrency, 
                                  DatabaseManager databaseManager,
                                  ConfigLoader configLoader) {
        this.threadPoolManager = new AdaptiveThreadPoolManager(concurrency);
        this.connectionManager = new DatabaseSpecificConnectionManager();
        this.retryPolicy = new AdaptiveRetryPolicy();
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.metricsCollector = new PerformanceMetricsCollector();
        this.validator = new ConfigurationValidator();
        this.configLoader = configLoader;
        this.queryExecutor = new QueryExecutor(databaseManager, threadPoolManager.getCorePoolSize());
        
        logger.info("增强指标执行器已初始化，并发数: {}", concurrency);
    }
    
    @Override
    public List<MetricResult> executeMetrics(Map<String, DatabaseConfig> databaseConfigs,
                                           List<MetricConfig> metricConfigs, 
                                           int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        logger.info("开始执行指标收集，数据库: {}, 指标: {}, 超时: {}秒", 
            databaseConfigs.size(), metricConfigs.size(), timeoutSeconds);
        
        // 验证配置
        if (!validateConfigurations(databaseConfigs, metricConfigs)) {
            return Collections.emptyList();
        }
        
        // 创建执行任务
        List<CompletableFuture<MetricResult>> futures = createExecutionTasks(
            databaseConfigs, metricConfigs);
        
        // 等待所有任务完成
        List<MetricResult> results = waitForCompletion(futures, timeoutSeconds);
        
        // 更新执行统计
        updateExecutionStats(results, System.currentTimeMillis() - startTime);
        
        logger.info("指标收集完成，总计: {}, 成功: {}, 失败: {}, 耗时: {}ms", 
            results.size(), 
            results.stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum(),
            results.stream().mapToInt(r -> r.isSuccess() ? 0 : 1).sum(),
            System.currentTimeMillis() - startTime);
        
        return results;
    }
    
    @Override
    public List<MetricResult> executeAllMetrics(String configPath, String metricsPath) {
        try {
            // 加载配置
            Map<String, DatabaseConfig> databaseConfigs = configLoader.loadDatabaseConfigs(configPath);
            List<MetricConfig> metricConfigs = configLoader.loadMetricConfigs(metricsPath);
            
            // 执行指标收集
            return executeMetrics(databaseConfigs, metricConfigs, 300); // 默认5分钟超时
            
        } catch (Exception e) {
            logger.error("执行所有指标失败", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public void setFailedEncryptedHosts(Set<String> hosts) {
        if (hosts != null) {
            this.failedEncryptedHosts = ConcurrentHashMap.newKeySet();
            this.failedEncryptedHosts.addAll(hosts);
        } else {
            this.failedEncryptedHosts = ConcurrentHashMap.newKeySet();
        }
        logger.info("设置失败主机列表，数量: {}", this.failedEncryptedHosts.size());
    }
    
    @Override
    public ExecutionStats getExecutionStats() {
        return executionStats;
    }
    
    @Override
    public void shutdown() {
        logger.info("开始关闭增强指标执行器");
        
        try {
            // 关闭线程池管理器
            threadPoolManager.shutdown();
            
            // 关闭连接管理器
            connectionManager.shutdown();
            
            // 关闭重试策略
            retryPolicy.shutdown();
            
            logger.info("增强指标执行器已关闭");
        } catch (Exception e) {
            logger.error("关闭增强指标执行器时发生异常", e);
        }
    }
    
    /**
     * 验证配置
     */
    private boolean validateConfigurations(Map<String, DatabaseConfig> databaseConfigs,
                                         List<MetricConfig> metricConfigs) {
        boolean allValid = true;
        
        // 验证数据库配置
        for (Map.Entry<String, DatabaseConfig> entry : databaseConfigs.entrySet()) {
            ConfigurationValidator.ValidationResult result = 
                validator.validateDatabaseConfig(entry.getValue());
            
            if (result.hasErrors()) {
                logger.error("数据库配置验证失败 [{}]: {}", entry.getKey(), result.getErrors());
                allValid = false;
            }
            
            if (result.hasWarnings()) {
                logger.warn("数据库配置警告 [{}]: {}", entry.getKey(), result.getWarnings());
            }
        }
        
        // 验证指标配置
        for (MetricConfig metricConfig : metricConfigs) {
            boolean isValid = validator.validateMetricConfig(metricConfig);
            
            if (!isValid) {
                logger.error("指标配置验证失败 [{}]", metricConfig.getName());
                allValid = false;
            }
        }
        
        return allValid;
    }
    
    /**
     * 创建执行任务
     */
    private List<CompletableFuture<MetricResult>> createExecutionTasks(
            Map<String, DatabaseConfig> databaseConfigs,
            List<MetricConfig> metricConfigs) {
        
        List<CompletableFuture<MetricResult>> futures = new ArrayList<>();
        
        for (Map.Entry<String, DatabaseConfig> dbEntry : databaseConfigs.entrySet()) {
            String systemName = dbEntry.getKey();
            DatabaseConfig dbConfig = dbEntry.getValue();
            
            // 跳过禁用的数据库
            if (!dbConfig.isEnable()) {
                logger.debug("跳过禁用的数据库: {}", systemName);
                continue;
            }
            
            for (MetricConfig metricConfig : metricConfigs) {
                // 跳过禁用的指标
                if (metricConfig.getExecutionStrategy() != null && 
                    !metricConfig.getExecutionStrategy().isEnabled()) {
                    logger.debug("跳过禁用的指标: {}", metricConfig.getName());
                    continue;
                }
                
                // 创建执行任务
                CompletableFuture<MetricResult> future = createSingleTask(
                    systemName, dbConfig, metricConfig);
                futures.add(future);
            }
        }
        
        return futures;
    }
    
    /**
     * 创建单个执行任务
     */
    private CompletableFuture<MetricResult> createSingleTask(String systemName,
                                                           DatabaseConfig dbConfig,
                                                           MetricConfig metricConfig) {
        String taskKey = systemName + ":" + metricConfig.getName();
        
        return retryPolicy.executeWithRetry(() -> {
            // 获取熔断器
            CircuitBreaker breaker = getCircuitBreaker(systemName);
            
            // 使用熔断器保护执行
            return breaker.execute(() -> {
                // 获取对应的线程池
                String dbType = dbConfig.getType();
                return CompletableFuture.supplyAsync(() -> {
                    long startTime = System.currentTimeMillis();
                    
                    try {
                        // 记录开始执行
                        metricsCollector.recordOperationCount("metric_execution", "started");
                        
                        // 执行查询 - 使用现有的QueryExecutor方法
                        MetricResult result = queryExecutor.executeMetricWithRetry(
                            dbConfig.getType(), systemName, metricConfig, null);
                        
                        // 记录执行时间和结果
                        long duration = System.currentTimeMillis() - startTime;
                        metricsCollector.recordQueryMetrics(dbType, systemName, 
                            metricConfig.getName(), duration, result.isSuccess());
                        
                        return result;
                        
                    } catch (Exception e) {
                        long duration = System.currentTimeMillis() - startTime;
                        metricsCollector.recordQueryMetrics(dbType, systemName, 
                            metricConfig.getName(), duration, false);
                        
                        logger.error("执行指标失败: {} - {}", taskKey, e.getMessage());
                        return createErrorResult(metricConfig.getName(), e.getMessage());
                    }
                }, threadPoolManager.getExecutor(dbType));
            });
        }, taskKey);
    }
    
    /**
     * 获取熔断器
     */
    private CircuitBreaker getCircuitBreaker(String systemName) {
        return circuitBreakers.computeIfAbsent(systemName, 
            name -> new CircuitBreaker(name, 5, 30000, 60000));
    }
    
    /**
     * 等待任务完成
     */
    private List<MetricResult> waitForCompletion(List<CompletableFuture<MetricResult>> futures,
                                               int timeoutSeconds) {
        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            // 等待所有任务完成或超时
            allOf.get(timeoutSeconds, TimeUnit.SECONDS);
            
            // 收集结果
            return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("等待任务完成时发生异常，收集已完成的结果: {}", e.getMessage());
            
            // 收集已完成的结果
            return futures.stream()
                .filter(CompletableFuture::isDone)
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception ex) {
                        return createErrorResult("unknown", ex.getMessage());
                    }
                })
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 更新执行统计
     */
    private void updateExecutionStats(List<MetricResult> results, long totalTime) {
        int total = results.size();
        int successful = (int) results.stream().filter(MetricResult::isSuccess).count();
        int failed = total - successful;
        
        executionStats.setTotalTasks(total);
        executionStats.setCompletedTasks(successful);
        executionStats.setFailedTasks(failed);
        executionStats.setTotalExecutionTime(totalTime);
        executionStats.setAverageExecutionTime(total > 0 ? totalTime / total : 0);
    }
    
    /**
     * 获取性能指标快照
     */
    public PerformanceMetricsCollector.MetricsSnapshot getPerformanceSnapshot() {
        return metricsCollector.getSnapshot();
    }
    
    /**
     * 获取线程池统计信息
     */
    public Map<String, AdaptiveThreadPoolManager.ThreadPoolStats> getThreadPoolStats() {
        return threadPoolManager.getThreadPoolStats();
    }
    
    /**
    /**
     * 获取熔断器状态
     */
    public Map<String, CircuitBreaker.State> getCircuitBreakerStates() {
        return circuitBreakers.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getState()
            ));
    }
    
    /**
     * 创建错误结果
     */
    private MetricResult createErrorResult(String metricName, String errorMessage) {
        MetricResult result = new MetricResult(
            "unknown",
            "unknown", 
            "unknown",
            metricName,
            "执行失败",
            "SINGLE",
            "unknown"
        );
        
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setCollectTime(java.time.LocalDateTime.now());
        
        return result;
    }
}
