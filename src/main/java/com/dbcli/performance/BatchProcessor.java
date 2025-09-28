package com.dbcli.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 批处理优化器
 * 用于处理大数据量场景下的分批处理，优化内存使用和性能
 */
public class BatchProcessor<T> {
    private static final Logger logger = LoggerFactory.getLogger(BatchProcessor.class);
    
    private final MemoryOptimizer memoryOptimizer;
    private final ExecutorService executorService;
    private final int defaultBatchSize;
    private final long estimatedMemoryPerItem;
    
    public BatchProcessor(int defaultBatchSize, long estimatedMemoryPerItem) {
        this.defaultBatchSize = defaultBatchSize;
        this.estimatedMemoryPerItem = estimatedMemoryPerItem;
        this.memoryOptimizer = new MemoryOptimizer();
        this.executorService = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 4),
            r -> {
                Thread t = new Thread(r, "batch-processor");
                t.setDaemon(true);
                return t;
            }
        );
        
        // 启动内存监控
        memoryOptimizer.startMonitoring();
    }
    
    /**
     * 处理数据列表，自动分批并优化内存使用
     */
    public <R> List<R> processBatches(List<T> data, Function<List<T>, List<R>> processor) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }
        
        logger.info("开始批处理，总数据量: {}", data.size());
        
        List<R> results = new ArrayList<>();
        int optimalBatchSize = memoryOptimizer.calculateOptimalBatchSize(
            defaultBatchSize, estimatedMemoryPerItem);
        
        List<Future<List<R>>> futures = new ArrayList<>();
        
        // 分批处理
        for (int i = 0; i < data.size(); i += optimalBatchSize) {
            int endIndex = Math.min(i + optimalBatchSize, data.size());
            List<T> batch = data.subList(i, endIndex);
            
            // 检查内存是否足够
            long requiredMemory = batch.size() * estimatedMemoryPerItem;
            if (!memoryOptimizer.hasEnoughMemory(requiredMemory)) {
                logger.warn("内存不足，等待垃圾回收...");
                System.gc();
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // 重新检查内存
                if (!memoryOptimizer.hasEnoughMemory(requiredMemory)) {
                    logger.error("内存不足，无法处理批次，跳过");
                    continue;
                }
            }
            
            // 提交批处理任务
            final int startIndex = i;
            Future<List<R>> future = executorService.submit(() -> {
                try {
                    logger.debug("处理批次: {}-{}", startIndex, endIndex - 1);
                    return processor.apply(batch);
                } catch (Exception e) {
                    logger.error("批处理失败: {}-{}", startIndex, endIndex - 1, e);
                    return new ArrayList<>();
                }
            });
            
            futures.add(future);
        }
        
        // 收集结果
        for (Future<List<R>> future : futures) {
            try {
                List<R> batchResult = future.get(30, TimeUnit.SECONDS);
                results.addAll(batchResult);
            } catch (TimeoutException e) {
                logger.error("批处理超时", e);
                future.cancel(true);
            } catch (Exception e) {
                logger.error("获取批处理结果失败", e);
            }
        }
        
        logger.info("批处理完成，处理结果数量: {}", results.size());
        return results;
    }
    
    /**
     * 异步处理数据列表
     */
    public void processBatchesAsync(List<T> data, Consumer<List<T>> processor, 
                                   Consumer<Exception> errorHandler) {
        if (data == null || data.isEmpty()) {
            return;
        }
        
        logger.info("开始异步批处理，总数据量: {}", data.size());
        
        int optimalBatchSize = memoryOptimizer.calculateOptimalBatchSize(
            defaultBatchSize, estimatedMemoryPerItem);
        
        // 分批异步处理
        for (int i = 0; i < data.size(); i += optimalBatchSize) {
            int endIndex = Math.min(i + optimalBatchSize, data.size());
            List<T> batch = data.subList(i, endIndex);
            final int batchIndex = i;
            
            executorService.submit(() -> {
                try {
                    // 检查内存
                    long requiredMemory = batch.size() * estimatedMemoryPerItem;
                    if (!memoryOptimizer.hasEnoughMemory(requiredMemory)) {
                        logger.warn("批次 {}-{} 内存不足，延迟处理", batchIndex, endIndex - 1);
                        Thread.sleep(2000);
                    }
                    
                    logger.debug("异步处理批次: {}-{}", batchIndex, endIndex - 1);
                    processor.accept(batch);
                    
                } catch (Exception e) {
                    logger.error("异步批处理失败: {}-{}", batchIndex, endIndex - 1, e);
                    if (errorHandler != null) {
                        errorHandler.accept(e);
                    }
                }
            });
        }
    }
    
    /**
     * 流式处理大数据量
     */
    public void processStream(List<T> data, Consumer<T> itemProcessor, 
                             int streamBatchSize) {
        if (data == null || data.isEmpty()) {
            return;
        }
        
        logger.info("开始流式处理，总数据量: {}", data.size());
        
        int processedCount = 0;
        List<T> currentBatch = new ArrayList<>();
        
        for (T item : data) {
            currentBatch.add(item);
            
            if (currentBatch.size() >= streamBatchSize) {
                processBatch(currentBatch, itemProcessor);
                processedCount += currentBatch.size();
                currentBatch.clear();
                
                // 检查内存使用情况
                if (processedCount % (streamBatchSize * 10) == 0) {
                    double memoryUsage = memoryOptimizer.getMemoryUsageRatio();
                    if (memoryUsage > 0.8) {
                        logger.info("内存使用率较高({:.1f}%)，暂停处理进行垃圾回收", 
                                  memoryUsage * 100);
                        System.gc();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
        
        // 处理剩余数据
        if (!currentBatch.isEmpty()) {
            processBatch(currentBatch, itemProcessor);
            processedCount += currentBatch.size();
        }
        
        logger.info("流式处理完成，总处理数量: {}", processedCount);
    }
    
    /**
     * 处理单个批次
     */
    private void processBatch(List<T> batch, Consumer<T> itemProcessor) {
        for (T item : batch) {
            try {
                itemProcessor.accept(item);
            } catch (Exception e) {
                logger.error("处理单个项目失败", e);
            }
        }
    }
    
    /**
     * 获取当前内存统计信息
     */
    public MemoryOptimizer.MemoryStats getMemoryStats() {
        return memoryOptimizer.getMemoryStats();
    }
    
    /**
     * 关闭批处理器
     */
    public void shutdown() {
        logger.info("正在关闭批处理器...");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        memoryOptimizer.stopMonitoring();
        logger.info("批处理器已关闭");
    }
    
    /**
     * 批处理配置构建器
     */
    public static class Builder<T> {
        private int defaultBatchSize = 1000;
        private long estimatedMemoryPerItem = 1024; // 1KB per item
        
        public Builder<T> batchSize(int batchSize) {
            this.defaultBatchSize = batchSize;
            return this;
        }
        
        public Builder<T> estimatedMemoryPerItem(long memoryPerItem) {
            this.estimatedMemoryPerItem = memoryPerItem;
            return this;
        }
        
        public BatchProcessor<T> build() {
            return new BatchProcessor<>(defaultBatchSize, estimatedMemoryPerItem);
        }
    }
    
    /**
     * 创建批处理器构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
}