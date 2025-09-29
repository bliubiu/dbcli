package com.dbcli.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自适应线程池管理器
 * 为不同数据库类型提供独立的线程池，并支持动态调整
 */
public class AdaptiveThreadPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveThreadPoolManager.class);
    
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final int queueCapacity;
    private final Map<String, ThreadPoolExecutor> dbTypeExecutors;
    private final ScheduledExecutorService monitor;
    
    public AdaptiveThreadPoolManager(int baseConcurrency) {
        // 从系统属性/环境变量读取参数，提供合理默认
        this.corePoolSize = getIntConfig("dbcli.pool.core", "DBCLI_POOL_CORE", Math.max(2, baseConcurrency / 4));
        this.maximumPoolSize = getIntConfig("dbcli.pool.max", "DBCLI_POOL_MAX", Math.max(this.corePoolSize, baseConcurrency));
        this.queueCapacity = getIntConfig("dbcli.pool.queue", "DBCLI_POOL_QUEUE", 100);
        this.dbTypeExecutors = new ConcurrentHashMap<>();
        this.monitor = Executors.newScheduledThreadPool(1);
        
        // 启动监控任务
        startMonitoring();
    }
    
    /**
     * 获取指定数据库类型的线程池
     */
    public ThreadPoolExecutor getExecutor(String dbType) {
        return dbTypeExecutors.computeIfAbsent(dbType, this::createExecutor);
    }
    
    /**
     * 创建线程池
     */
    private ThreadPoolExecutor createExecutor(String dbType) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            new DbCliThreadFactory(dbType),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        logger.info("为数据库类型 {} 创建线程池: core={}, max={}, queue={}", 
            dbType, corePoolSize, maximumPoolSize, queueCapacity);
        
        return executor;
    }
    
    /**
     * 启动监控任务
     */
    private void startMonitoring() {
        monitor.scheduleAtFixedRate(() -> {
            try {
                monitorThreadPools();
            } catch (Exception e) {
                logger.error("线程池监控异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 监控线程池状态
     */
    private void monitorThreadPools() {
        dbTypeExecutors.forEach((dbType, executor) -> {
            int active = executor.getActiveCount();
            int queue = executor.getQueue().size();
            int poolSize = executor.getPoolSize();
            int maxPoolSize = executor.getMaximumPoolSize();
            
            // 记录线程池状态
            if (logger.isDebugEnabled()) {
                logger.debug("线程池状态 {}: 活跃={}, 队列={}, 池大小={}/{}", 
                    dbType, active, queue, poolSize, maxPoolSize);
            }
            
            // 队列积压告警
            if (queue > 50) {
                logger.warn("线程池 {} 队列积压严重: {} 个任务等待执行", dbType, queue);
                
                // 动态扩展线程池
                if (poolSize < maximumPoolSize * 2) {
                    int newMax = Math.min(maximumPoolSize * 2, poolSize + 2);
                    executor.setMaximumPoolSize(newMax);
                    logger.info("动态扩展线程池 {} 最大线程数至: {}", dbType, newMax);
                }
            }
            
            // 线程池使用率告警
            double utilizationRate = (double) active / Math.max(1, maxPoolSize);
            if (utilizationRate > 0.8) {
                logger.warn("线程池 {} 使用率过高: {}%", dbType, String.format(java.util.Locale.ROOT, "%.1f", utilizationRate * 100));
            }
        });
    }
    
    /**
     * 获取线程池统计信息
     */
    public Map<String, ThreadPoolStats> getThreadPoolStats() {
        Map<String, ThreadPoolStats> stats = new ConcurrentHashMap<>();
        
        dbTypeExecutors.forEach((dbType, executor) -> {
            ThreadPoolStats poolStats = new ThreadPoolStats();
            poolStats.setDbType(dbType);
            poolStats.setActiveCount(executor.getActiveCount());
            poolStats.setQueueSize(executor.getQueue().size());
            poolStats.setPoolSize(executor.getPoolSize());
            poolStats.setMaxPoolSize(executor.getMaximumPoolSize());
            poolStats.setCompletedTaskCount(executor.getCompletedTaskCount());
            poolStats.setTaskCount(executor.getTaskCount());
            
            stats.put(dbType, poolStats);
        });
        
        return stats;
    }
    
    /**
     * 关闭所有线程池
     */
    public void shutdown() {
        logger.info("开始关闭线程池管理器");
        
        // 关闭监控
        monitor.shutdown();
        
        // 关闭所有数据库线程池
        dbTypeExecutors.forEach((dbType, executor) -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("线程池 {} 未能在30秒内正常关闭，强制关闭", dbType);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("等待线程池 {} 关闭时被中断", dbType);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
        
        // 等待监控线程关闭
        try {
            if (!monitor.awaitTermination(10, TimeUnit.SECONDS)) {
                monitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("线程池管理器已关闭");
    }
    
    /**
     * 获取核心线程池大小
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }
    
    private static int getIntConfig(String sysProp, String envVar, int defVal) {
        try {
            String v = System.getProperty(sysProp);
            if (v == null || v.isEmpty()) {
                v = System.getenv(envVar);
            }
            if (v == null || v.isEmpty()) return defVal;
            int parsed = Integer.parseInt(v.trim());
            return parsed > 0 ? parsed : defVal;
        } catch (Exception e) {
            return defVal;
        }
    }
    
    /**
     * 自定义线程工厂
     */
    private static class DbCliThreadFactory implements ThreadFactory {
        private final String dbType;
        private final AtomicInteger counter = new AtomicInteger(0);
        
        public DbCliThreadFactory(String dbType) {
            this.dbType = dbType;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "dbcli-" + dbType + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
    
    /**
     * 线程池统计信息
     */
    public static class ThreadPoolStats {
        private String dbType;
        private int activeCount;
        private int queueSize;
        private int poolSize;
        private int maxPoolSize;
        private long completedTaskCount;
        private long taskCount;
        
        // Getters and Setters
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        
        public int getActiveCount() { return activeCount; }
        public void setActiveCount(int activeCount) { this.activeCount = activeCount; }
        
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
        
        public int getPoolSize() { return poolSize; }
        public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
        
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        
        public long getCompletedTaskCount() { return completedTaskCount; }
        public void setCompletedTaskCount(long completedTaskCount) { this.completedTaskCount = completedTaskCount; }
        
        public long getTaskCount() { return taskCount; }
        public void setTaskCount(long taskCount) { this.taskCount = taskCount; }
        
        public double getUtilizationRate() {
            return maxPoolSize > 0 ? (double) activeCount / maxPoolSize : 0.0;
        }
    }
}