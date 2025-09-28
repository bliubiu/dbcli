package com.dbcli.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 超时和取消机制管理器
 * 提供细粒度的超时控制和任务取消功能
 */
public class TimeoutCancellationManager {
    private static final Logger logger = LoggerFactory.getLogger(TimeoutCancellationManager.class);
    
    private final ScheduledExecutorService timeoutScheduler;
    private final Map<String, TaskContext> activeTasks;
    private final AtomicInteger taskIdCounter;
    private final AtomicLong totalTimeouts;
    private final AtomicLong totalCancellations;
    
    // 默认超时配置
    private static final long DEFAULT_TIMEOUT_MS = 30000L; // 30秒
    private static final long MAX_TIMEOUT_MS = 600000L;    // 10分钟
    private static final long MIN_TIMEOUT_MS = 1000L;     // 1秒
    
    public TimeoutCancellationManager() {
        this.timeoutScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "timeout-manager");
            t.setDaemon(true);
            return t;
        });
        this.activeTasks = new ConcurrentHashMap<>();
        this.taskIdCounter = new AtomicInteger(0);
        this.totalTimeouts = new AtomicLong(0);
        this.totalCancellations = new AtomicLong(0);
        
        logger.info("TimeoutCancellationManager initialized");
    }
    
    /**
     * 注册任务并设置超时
     */
    public String registerTask(CompletableFuture<?> future, long timeoutMs) {
        return registerTask(future, timeoutMs, null);
    }
    
    /**
     * 注册任务并设置超时（带描述）
     */
    public String registerTask(CompletableFuture<?> future, long timeoutMs, String description) {
        long actualTimeout = validateTimeout(timeoutMs);
        String taskId = generateTaskId();
        
        TaskContext context = new TaskContext(taskId, future, actualTimeout, description);
        activeTasks.put(taskId, context);
        
        // 设置超时处理
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(
            () -> handleTimeout(taskId), actualTimeout, TimeUnit.MILLISECONDS);
        
        context.setTimeoutFuture(timeoutFuture);
        
        // 任务完成时清理
        future.whenComplete((result, throwable) -> {
            TaskContext removed = activeTasks.remove(taskId);
            if (removed != null && removed.getTimeoutFuture() != null) {
                removed.getTimeoutFuture().cancel(false);
            }
        });
        
        logger.debug("Registered task: {} with timeout: {}ms", taskId, actualTimeout);
        return taskId;
    }
    
    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        TaskContext context = activeTasks.remove(taskId);
        if (context != null) {
            boolean cancelled = context.getFuture().cancel(true);
            if (context.getTimeoutFuture() != null) {
                context.getTimeoutFuture().cancel(false);
            }
            
            if (cancelled) {
                totalCancellations.incrementAndGet();
                logger.info("Task cancelled: {} ({})", taskId, context.getDescription());
            }
            return cancelled;
        }
        return false;
    }
    
    /**
     * 批量取消任务
     */
    public int cancelTasks(String... taskIds) {
        int cancelledCount = 0;
        for (String taskId : taskIds) {
            if (cancelTask(taskId)) {
                cancelledCount++;
            }
        }
        return cancelledCount;
    }
    
    /**
     * 取消所有任务
     */
    public int cancelAllTasks() {
        int cancelledCount = 0;
        for (String taskId : activeTasks.keySet()) {
            if (cancelTask(taskId)) {
                cancelledCount++;
            }
        }
        logger.info("Cancelled all {} tasks", cancelledCount);
        return cancelledCount;
    }
    
    /**
     * 处理超时
     */
    private void handleTimeout(String taskId) {
        TaskContext context = activeTasks.remove(taskId);
        if (context != null) {
            boolean cancelled = context.getFuture().cancel(true);
            if (cancelled) {
                totalTimeouts.incrementAndGet();
                logger.warn("Task timed out and cancelled: {} ({}) after {}ms", 
                           taskId, context.getDescription(), context.getTimeoutMs());
            }
        }
    }
    
    /**
     * 验证超时时间
     */
    private long validateTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.max(MIN_TIMEOUT_MS, Math.min(timeoutMs, MAX_TIMEOUT_MS));
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task-" + taskIdCounter.incrementAndGet() + "-" + System.currentTimeMillis();
    }
    
    /**
     * 获取活跃任务数量
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }
    
    /**
     * 获取统计信息
     */
    public TimeoutStats getStats() {
        return new TimeoutStats(
            activeTasks.size(),
            totalTimeouts.get(),
            totalCancellations.get(),
            taskIdCounter.get()
        );
    }
    
    /**
     * 等待所有任务完成
     */
    public boolean awaitAllTasks(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        while (!activeTasks.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        
        return activeTasks.isEmpty();
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        logger.info("Shutting down TimeoutCancellationManager...");
        
        // 取消所有活跃任务
        int cancelledTasks = cancelAllTasks();
        
        // 关闭调度器
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("TimeoutCancellationManager shutdown completed. Cancelled {} tasks", cancelledTasks);
    }
    
    /**
     * 任务上下文
     */
    private static class TaskContext {
        private final String taskId;
        private final CompletableFuture<?> future;
        private final long timeoutMs;
        private final String description;
        private ScheduledFuture<?> timeoutFuture;
        
        public TaskContext(String taskId, CompletableFuture<?> future, long timeoutMs, String description) {
            this.taskId = taskId;
            this.future = future;
            this.timeoutMs = timeoutMs;
            this.description = description != null ? description : "Unknown task";
        }
        
        // Getters and Setters
        public String getTaskId() { return taskId; }
        public CompletableFuture<?> getFuture() { return future; }
        public long getTimeoutMs() { return timeoutMs; }
        public String getDescription() { return description; }
        public ScheduledFuture<?> getTimeoutFuture() { return timeoutFuture; }
        public void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) { this.timeoutFuture = timeoutFuture; }
    }
    
    /**
     * 超时统计信息
     */
    public static class TimeoutStats {
        private final int activeTasks;
        private final long totalTimeouts;
        private final long totalCancellations;
        private final int totalTasks;
        
        public TimeoutStats(int activeTasks, long totalTimeouts, long totalCancellations, int totalTasks) {
            this.activeTasks = activeTasks;
            this.totalTimeouts = totalTimeouts;
            this.totalCancellations = totalCancellations;
            this.totalTasks = totalTasks;
        }
        
        public int getActiveTasks() { return activeTasks; }
        public long getTotalTimeouts() { return totalTimeouts; }
        public long getTotalCancellations() { return totalCancellations; }
        public int getTotalTasks() { return totalTasks; }
        
        public double getTimeoutRate() {
            return totalTasks > 0 ? (double) totalTimeouts / totalTasks : 0.0;
        }
        
        public double getCancellationRate() {
            return totalTasks > 0 ? (double) totalCancellations / totalTasks : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "TimeoutStats{activeTasks=%d, totalTimeouts=%d, totalCancellations=%d, " +
                "totalTasks=%d, timeoutRate=%.2f%%, cancellationRate=%.2f%%}",
                activeTasks, totalTimeouts, totalCancellations, totalTasks,
                getTimeoutRate() * 100, getCancellationRate() * 100
            );
        }
    }
}