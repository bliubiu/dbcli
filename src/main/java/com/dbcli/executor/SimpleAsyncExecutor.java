package com.dbcli.executor;

import com.dbcli.model.MetricResult;
import com.dbcli.model.MetricConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化的异步性能执行器
 * 提供基本的异步执行、超时控制和性能统计功能
 */
public class SimpleAsyncExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SimpleAsyncExecutor.class);
    
    private final ExecutorService executorService;
    private final ScheduledExecutorService timeoutScheduler;
    private final Map<String, CompletableFuture<MetricResult>> runningTasks;
    private final AtomicInteger taskCounter;
    private final AtomicLong totalExecutionTime;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    
    // 超时配置
    private final long defaultTimeoutMs;
    private final long maxTimeoutMs;
    
    public SimpleAsyncExecutor() {
        this(30000L, 300000L); // 默认30秒超时，最大5分钟
    }
    
    public SimpleAsyncExecutor(long defaultTimeoutMs, long maxTimeoutMs) {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2;
        
        this.executorService = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "simple-async-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        this.timeoutScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "timeout-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.runningTasks = new ConcurrentHashMap<>();
        this.taskCounter = new AtomicInteger(0);
        this.totalExecutionTime = new AtomicLong(0);
        this.successCount = new AtomicInteger(0);
        this.failureCount = new AtomicInteger(0);
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.maxTimeoutMs = maxTimeoutMs;
        
        logger.info("SimpleAsyncExecutor initialized with timeout: {}ms, max: {}ms", 
                   defaultTimeoutMs, maxTimeoutMs);
    }
    
    /**
     * 异步执行指标收集任务
     */
    public CompletableFuture<MetricResult> executeAsync(MetricConfig config, 
                                                       Supplier<MetricResult> task) {
        return executeAsync(config, task, defaultTimeoutMs);
    }
    
    /**
     * 异步执行指标收集任务（带超时控制）
     */
    public CompletableFuture<MetricResult> executeAsync(MetricConfig config, 
                                                       Supplier<MetricResult> task,
                                                       long timeoutMs) {
        String taskId = generateTaskId(config);
        long actualTimeout = Math.min(timeoutMs, maxTimeoutMs);
        
        logger.debug("Starting async task: {} with timeout: {}ms", taskId, actualTimeout);
        
        CompletableFuture<MetricResult> future = CompletableFuture
            .supplyAsync(() -> executeWithRetry(config, task, taskId), executorService)
            .orTimeout(actualTimeout, TimeUnit.MILLISECONDS)
            .whenComplete((result, throwable) -> {
                runningTasks.remove(taskId);
                if (throwable == null) {
                    handleSuccess(taskId, result);
                } else {
                    handleFailure(taskId, throwable);
                }
            });
        
        runningTasks.put(taskId, future);
        return future;
    }
    
    /**
     * 批量异步执行
     */
    public CompletableFuture<List<MetricResult>> executeAllAsync(List<MetricConfig> configs,
                                                                List<Supplier<MetricResult>> tasks) {
        if (configs.size() != tasks.size()) {
            throw new IllegalArgumentException("Configs and tasks size mismatch");
        }
        
        List<CompletableFuture<MetricResult>> futures = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            futures.add(executeAsync(configs.get(i), tasks.get(i)));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }
    
    /**
     * 使用简单重试机制执行任务
     */
    private MetricResult executeWithRetry(MetricConfig config, 
                                         Supplier<MetricResult> task,
                                         String taskId) {
        long startTime = System.currentTimeMillis();
        int maxRetries = 3;
        long retryDelayMs = 1000;
        
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("Executing task: {} (attempt {})", taskId, attempt + 1);
                MetricResult result = task.get();
                
                if (result == null) {
                    throw new RuntimeException("Task returned null result");
                }
                
                long executionTime = System.currentTimeMillis() - startTime;
                totalExecutionTime.addAndGet(executionTime);
                
                if (attempt > 0) {
                    logger.info("Task {} succeeded on attempt {}", taskId, attempt + 1);
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < maxRetries) {
                    logger.warn("Task {} failed on attempt {}, retrying in {}ms: {}", 
                               taskId, attempt + 1, retryDelayMs, e.getMessage());
                    
                    try {
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 2; // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Task interrupted during retry", ie);
                    }
                } else {
                    logger.error("Task {} failed after {} attempts", taskId, maxRetries + 1, e);
                }
            }
        }
        
        throw new RuntimeException("Task execution failed after retries", lastException);
    }
    
    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        CompletableFuture<MetricResult> future = runningTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                runningTasks.remove(taskId);
                logger.info("Task cancelled: {}", taskId);
            }
            return cancelled;
        }
        return false;
    }
    
    /**
     * 取消所有运行中的任务
     */
    public int cancelAllTasks() {
        int cancelledCount = 0;
        for (Map.Entry<String, CompletableFuture<MetricResult>> entry : runningTasks.entrySet()) {
            if (entry.getValue().cancel(true)) {
                cancelledCount++;
            }
        }
        runningTasks.clear();
        logger.info("Cancelled {} tasks", cancelledCount);
        return cancelledCount;
    }
    
    /**
     * 等待所有任务完成
     */
    public void awaitAllTasks(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        while (!runningTasks.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        
        if (!runningTasks.isEmpty()) {
            logger.warn("Still have {} running tasks after timeout", runningTasks.size());
        }
    }
    
    /**
     * 获取性能统计信息
     */
    public PerformanceStats getPerformanceStats() {
        return new PerformanceStats(
            taskCounter.get(),
            successCount.get(),
            failureCount.get(),
            runningTasks.size(),
            totalExecutionTime.get()
        );
    }
    
    /**
     * 处理任务成功
     */
    private void handleSuccess(String taskId, MetricResult result) {
        successCount.incrementAndGet();
        logger.debug("Task completed successfully: {}", taskId);
    }
    
    /**
     * 处理任务失败
     */
    private void handleFailure(String taskId, Throwable throwable) {
        failureCount.incrementAndGet();
        
        if (throwable instanceof TimeoutException) {
            logger.warn("Task timed out: {}", taskId);
        } else if (throwable instanceof CancellationException) {
            logger.info("Task was cancelled: {}", taskId);
        } else {
            logger.error("Task failed: {}", taskId, throwable);
        }
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId(MetricConfig config) {
        return String.format("task-%d-%s-%s", 
                           taskCounter.incrementAndGet(),
                           config.getName(),
                           System.currentTimeMillis());
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        logger.info("Shutting down SimpleAsyncExecutor...");
        
        // 取消所有运行中的任务
        int cancelledTasks = cancelAllTasks();
        
        // 关闭线程池
        executorService.shutdown();
        timeoutScheduler.shutdown();
        
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("SimpleAsyncExecutor shutdown completed. Cancelled {} tasks", cancelledTasks);
    }
    
    /**
     * 性能统计信息
     */
    public static class PerformanceStats {
        private final int totalTasks;
        private final int successTasks;
        private final int failedTasks;
        private final int runningTasks;
        private final long totalExecutionTime;
        
        public PerformanceStats(int totalTasks, int successTasks, int failedTasks, 
                               int runningTasks, long totalExecutionTime) {
            this.totalTasks = totalTasks;
            this.successTasks = successTasks;
            this.failedTasks = failedTasks;
            this.runningTasks = runningTasks;
            this.totalExecutionTime = totalExecutionTime;
        }
        
        public double getSuccessRate() {
            return totalTasks > 0 ? (double) successTasks / totalTasks : 0.0;
        }
        
        public double getAverageExecutionTime() {
            return totalTasks > 0 ? (double) totalExecutionTime / totalTasks : 0.0;
        }
        
        // Getters
        public int getTotalTasks() { return totalTasks; }
        public int getSuccessTasks() { return successTasks; }
        public int getFailedTasks() { return failedTasks; }
        public int getRunningTasks() { return runningTasks; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        
        @Override
        public String toString() {
            return String.format(
                "PerformanceStats{totalTasks=%d, successTasks=%d, failedTasks=%d, " +
                "runningTasks=%d, successRate=%.2f%%, avgExecutionTime=%.2fms}",
                totalTasks, successTasks, failedTasks, runningTasks,
                getSuccessRate() * 100, getAverageExecutionTime()
            );
        }
    }
}