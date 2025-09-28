package com.dbcli.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存优化器
 * 负责监控和优化内存使用，特别是在大数据量场景下
 */
public class MemoryOptimizer {
    private static final Logger logger = LoggerFactory.getLogger(MemoryOptimizer.class);
    
    private final MemoryMXBean memoryBean;
    private final ScheduledExecutorService scheduler;
    private volatile boolean monitoring = false;
    
    // 内存阈值配置
    private static final double HEAP_WARNING_THRESHOLD = 0.8;  // 80%
    private static final double HEAP_CRITICAL_THRESHOLD = 0.9; // 90%
    private static final long MIN_FREE_MEMORY = 100 * 1024 * 1024; // 100MB
    
    public MemoryOptimizer() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "memory-optimizer");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 启动内存监控
     */
    public void startMonitoring() {
        if (monitoring) {
            return;
        }
        
        monitoring = true;
        scheduler.scheduleAtFixedRate(this::checkMemoryUsage, 0, 30, TimeUnit.SECONDS);
        logger.info("内存监控已启动");
    }
    
    /**
     * 停止内存监控
     */
    public void stopMonitoring() {
        monitoring = false;
        scheduler.shutdown();
        logger.info("内存监控已停止");
    }
    
    /**
     * 检查内存使用情况
     */
    private void checkMemoryUsage() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
            
            if (usageRatio >= HEAP_CRITICAL_THRESHOLD) {
                logger.warn("内存使用率达到临界值: {:.1f}%, 正在执行紧急清理", usageRatio * 100);
                performEmergencyCleanup();
            } else if (usageRatio >= HEAP_WARNING_THRESHOLD) {
                logger.warn("内存使用率较高: {:.1f}%, 建议执行垃圾回收", usageRatio * 100);
                suggestGarbageCollection();
            }
            
            // 记录内存使用情况
            logMemoryUsage(heapUsage);
            
        } catch (Exception e) {
            logger.error("检查内存使用情况时发生错误", e);
        }
    }
    
    /**
     * 执行紧急内存清理
     */
    private void performEmergencyCleanup() {
        logger.info("开始执行紧急内存清理...");
        
        // 强制垃圾回收
        System.gc();
        System.runFinalization();
        
        // 等待一段时间让GC完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 再次检查内存使用情况
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        logger.info("紧急清理完成，当前内存使用率: {:.1f}%", usageRatio * 100);
    }
    
    /**
     * 建议执行垃圾回收
     */
    private void suggestGarbageCollection() {
        System.gc();
    }
    
    /**
     * 记录内存使用情况
     */
    private void logMemoryUsage(MemoryUsage heapUsage) {
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        long free = max - used;
        double usageRatio = (double) used / max;
        
        logger.debug("内存使用情况 - 已用: {}MB, 最大: {}MB, 空闲: {}MB, 使用率: {:.1f}%",
                used / (1024 * 1024),
                max / (1024 * 1024),
                free / (1024 * 1024),
                usageRatio * 100);
    }
    
    /**
     * 检查是否有足够的内存执行操作
     */
    public boolean hasEnoughMemory(long requiredMemory) {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long availableMemory = heapUsage.getMax() - heapUsage.getUsed();
        
        return availableMemory >= requiredMemory + MIN_FREE_MEMORY;
    }
    
    /**
     * 获取可用内存大小
     */
    public long getAvailableMemory() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getMax() - heapUsage.getUsed();
    }
    
    /**
     * 获取内存使用率
     */
    public double getMemoryUsageRatio() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }
    
    /**
     * 计算建议的批处理大小
     * 基于当前内存使用情况动态调整
     */
    public int calculateOptimalBatchSize(int defaultBatchSize, long estimatedMemoryPerItem) {
        long availableMemory = getAvailableMemory();
        double memoryUsageRatio = getMemoryUsageRatio();
        
        // 如果内存使用率高，减少批处理大小
        if (memoryUsageRatio > HEAP_WARNING_THRESHOLD) {
            int reducedBatchSize = (int) (defaultBatchSize * (1 - memoryUsageRatio));
            logger.debug("由于内存使用率较高({:.1f}%)，将批处理大小从{}调整为{}",
                    memoryUsageRatio * 100, defaultBatchSize, reducedBatchSize);
            return Math.max(reducedBatchSize, 10); // 最小批处理大小为10
        }
        
        // 基于可用内存计算最大批处理大小
        if (estimatedMemoryPerItem > 0) {
            long maxItemsBasedOnMemory = (availableMemory - MIN_FREE_MEMORY) / estimatedMemoryPerItem;
            int memoryBasedBatchSize = (int) Math.min(maxItemsBasedOnMemory, defaultBatchSize);
            
            if (memoryBasedBatchSize < defaultBatchSize) {
                logger.debug("基于可用内存({} MB)，将批处理大小调整为{}",
                        availableMemory / (1024 * 1024), memoryBasedBatchSize);
                return Math.max(memoryBasedBatchSize, 10);
            }
        }
        
        return defaultBatchSize;
    }
    
    /**
     * 内存使用情况统计
     */
    public static class MemoryStats {
        private final long usedMemory;
        private final long maxMemory;
        private final long freeMemory;
        private final double usageRatio;
        
        public MemoryStats(long usedMemory, long maxMemory, long freeMemory, double usageRatio) {
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
            this.freeMemory = freeMemory;
            this.usageRatio = usageRatio;
        }
        
        public long getUsedMemory() { return usedMemory; }
        public long getMaxMemory() { return maxMemory; }
        public long getFreeMemory() { return freeMemory; }
        public double getUsageRatio() { return usageRatio; }
        
        @Override
        public String toString() {
            return String.format("MemoryStats{used=%dMB, max=%dMB, free=%dMB, usage=%.1f%%}",
                    usedMemory / (1024 * 1024),
                    maxMemory / (1024 * 1024),
                    freeMemory / (1024 * 1024),
                    usageRatio * 100);
        }
    }
    
    /**
     * 获取当前内存统计信息
     */
    public MemoryStats getMemoryStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        long free = max - used;
        double usageRatio = (double) used / max;
        
        return new MemoryStats(used, max, free, usageRatio);
    }
}