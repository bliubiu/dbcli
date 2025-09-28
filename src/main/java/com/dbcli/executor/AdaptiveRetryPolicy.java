package com.dbcli.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 自适应重试策略
 * 根据异常类型智能选择重试策略
 */
public class AdaptiveRetryPolicy {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveRetryPolicy.class);
    
    private final Map<Class<? extends Exception>, RetryStrategy> strategies;
    private final ScheduledExecutorService scheduler;
    
    public AdaptiveRetryPolicy() {
        this.strategies = new HashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        initializeStrategies();
    }
    
    /**
     * 初始化重试策略
     */
    private void initializeStrategies() {
        // SQL超时 - 指数退避重试
        strategies.put(SQLTimeoutException.class, 
            new ExponentialBackoffStrategy(3, 1000, 2.0));
        
        // 连接异常 - 固定间隔重试
        strategies.put(SQLException.class, 
            new FixedIntervalStrategy(2, 5000));
        
        // 网络异常 - 快速重试
        strategies.put(ConnectException.class, 
            new FixedIntervalStrategy(5, 1000));
        
        // 语法错误 - 不重试
        strategies.put(SQLSyntaxErrorException.class, 
            new NoRetryStrategy());
    }
    
    /**
     * 执行带重试的操作
     */
    public <T> CompletableFuture<T> executeWithRetry(
            Supplier<CompletableFuture<T>> operation, 
            String operationName) {
        
        return executeWithRetry(operation, operationName, 0);
    }
    
    /**
     * 递归执行重试
     */
    private <T> CompletableFuture<T> executeWithRetry(
            Supplier<CompletableFuture<T>> operation,
            String operationName,
            int attemptCount) {
        
        return operation.get()
            .handle((result, throwable) -> {
                if (throwable == null) {
                    if (attemptCount > 0) {
                        logger.info("操作 {} 在第 {} 次重试后成功", operationName, attemptCount);
                    }
                    return CompletableFuture.completedFuture(result);
                }
                
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                RetryStrategy strategy = getStrategy(cause);
                
                if (strategy.shouldRetry(attemptCount, cause)) {
                    long delay = strategy.getDelay(attemptCount);
                    logger.warn("操作 {} 第 {} 次重试，延迟 {}ms: {}", 
                        operationName, attemptCount + 1, delay, cause.getMessage());
                    
                    CompletableFuture<T> retryFuture = new CompletableFuture<>();
                    scheduler.schedule(() -> {
                        executeWithRetry(operation, operationName, attemptCount + 1)
                            .whenComplete((retryResult, retryThrowable) -> {
                                if (retryThrowable == null) {
                                    retryFuture.complete(retryResult);
                                } else {
                                    retryFuture.completeExceptionally(retryThrowable);
                                }
                            });
                    }, delay, TimeUnit.MILLISECONDS);
                    
                    return retryFuture;
                } else {
                    logger.error("操作 {} 重试失败，放弃执行: {}", operationName, cause.getMessage());
                    return CompletableFuture.<T>failedFuture(throwable);
                }
            })
            .thenCompose(Function.identity());
    }
    
    /**
     * 获取重试策略
     */
    private RetryStrategy getStrategy(Throwable throwable) {
        Class<?> exceptionType = throwable.getClass();
        
        // 直接匹配
        RetryStrategy strategy = strategies.get(exceptionType);
        if (strategy != null) {
            return strategy;
        }
        
        // 查找父类策略
        for (Map.Entry<Class<? extends Exception>, RetryStrategy> entry : strategies.entrySet()) {
            if (entry.getKey().isAssignableFrom(exceptionType)) {
                return entry.getValue();
            }
        }
        
        // 默认策略
        return new FixedIntervalStrategy(1, 1000);
    }
    
    /**
     * 关闭重试策略
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 重试策略接口
     */
    public interface RetryStrategy {
        boolean shouldRetry(int attemptCount, Throwable cause);
        long getDelay(int attemptCount);
    }
    
    /**
     * 指数退避策略
     */
    public static class ExponentialBackoffStrategy implements RetryStrategy {
        private final int maxAttempts;
        private final long baseDelayMs;
        private final double multiplier;
        
        public ExponentialBackoffStrategy(int maxAttempts, long baseDelayMs, double multiplier) {
            this.maxAttempts = maxAttempts;
            this.baseDelayMs = baseDelayMs;
            this.multiplier = multiplier;
        }
        
        @Override
        public boolean shouldRetry(int attemptCount, Throwable cause) {
            return attemptCount < maxAttempts;
        }
        
        @Override
        public long getDelay(int attemptCount) {
            return (long) (baseDelayMs * Math.pow(multiplier, attemptCount));
        }
    }
    
    /**
     * 固定间隔策略
     */
    public static class FixedIntervalStrategy implements RetryStrategy {
        private final int maxAttempts;
        private final long delayMs;
        
        public FixedIntervalStrategy(int maxAttempts, long delayMs) {
            this.maxAttempts = maxAttempts;
            this.delayMs = delayMs;
        }
        
        @Override
        public boolean shouldRetry(int attemptCount, Throwable cause) {
            return attemptCount < maxAttempts;
        }
        
        @Override
        public long getDelay(int attemptCount) {
            return delayMs;
        }
    }
    
    /**
     * 不重试策略
     */
    public static class NoRetryStrategy implements RetryStrategy {
        @Override
        public boolean shouldRetry(int attemptCount, Throwable cause) {
            return false;
        }
        
        @Override
        public long getDelay(int attemptCount) {
            return 0;
        }
    }
}