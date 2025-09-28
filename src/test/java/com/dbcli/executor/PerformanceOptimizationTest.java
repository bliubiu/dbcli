package com.dbcli.executor;

import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能优化功能测试
 */
public class PerformanceOptimizationTest {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceOptimizationTest.class);
    
    private SimpleAsyncExecutor asyncExecutor;
    private TimeoutCancellationManager timeoutManager;
    private AdaptiveThreadPoolManager threadPoolManager;
    
    @BeforeEach
    void setUp() {
        asyncExecutor = new SimpleAsyncExecutor();
        timeoutManager = new TimeoutCancellationManager();
        threadPoolManager = new AdaptiveThreadPoolManager(4);
    }
    
    @AfterEach
    void tearDown() {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
        }
        if (timeoutManager != null) {
            timeoutManager.shutdown();
        }
        if (threadPoolManager != null) {
            threadPoolManager.shutdown();
        }
    }
    
    @Test
    void testSimpleAsyncExecutor() throws Exception {
        logger.info("测试简单异步执行器");
        
        // 创建测试配置
        MetricConfig config = createTestConfig("test_metric");
        
        // 创建测试任务
        Supplier<MetricResult> task = () -> {
            try {
                Thread.sleep(100); // 模拟执行时间
                MetricResult result = new MetricResult();
                result.setMetricName("test_metric");
                result.setValue("100");
                result.setTimestamp(System.currentTimeMillis());
                result.setSuccess(true);
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        };
        
        // 执行异步任务
        CompletableFuture<MetricResult> future = asyncExecutor.executeAsync(config, task);
        MetricResult result = future.get(5, TimeUnit.SECONDS);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("test_metric", result.getMetricName());
        assertEquals("100", result.getValue());
        
        // 验证性能统计
        SimpleAsyncExecutor.PerformanceStats stats = asyncExecutor.getPerformanceStats();
        assertTrue(stats.getTotalTasks() > 0);
        assertTrue(stats.getSuccessTasks() > 0);
        assertEquals(0, stats.getFailedTasks());
    }
    
    @Test
    void testBatchAsyncExecution() throws Exception {
        logger.info("测试批量异步执行");
        
        // 创建多个测试配置和任务
        List<MetricConfig> configs = new ArrayList<>();
        List<Supplier<MetricResult>> tasks = new ArrayList<>();
        
        for (int i = 1; i <= 3; i++) {
            configs.add(createTestConfig("test_metric_" + i));
            final int value = i;
            tasks.add(() -> {
                try {
                    Thread.sleep(50 * value); // 不同的执行时间
                    MetricResult result = new MetricResult();
                    result.setMetricName("test_metric_" + value);
                    result.setValue(String.valueOf(value * 10));
                    result.setTimestamp(System.currentTimeMillis());
                    result.setSuccess(true);
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Task interrupted", e);
                }
            });
        }
        
        // 执行批量任务
        CompletableFuture<List<MetricResult>> batchFuture = asyncExecutor.executeAllAsync(configs, tasks);
        List<MetricResult> results = batchFuture.get(10, TimeUnit.SECONDS);
        
        // 验证结果
        assertNotNull(results);
        assertEquals(3, results.size());
        
        for (int i = 0; i < results.size(); i++) {
            MetricResult result = results.get(i);
            assertTrue(result.isSuccess());
            assertTrue(result.getMetricName().startsWith("test_metric_"));
        }
    }
    
    @Test
    void testTimeoutCancellation() throws Exception {
        logger.info("测试超时取消机制");
        
        // 创建长时间运行的任务
        CompletableFuture<String> longTask = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(5000); // 5秒任务
                return "Long task completed";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        });
        
        // 注册任务并设置1秒超时
        String taskId = timeoutManager.registerTask(longTask, 1000, "Long running test");
        
        // 等待任务完成或超时
        try {
            String result = longTask.get(2, TimeUnit.SECONDS);
            fail("任务应该已经超时");
        } catch (Exception e) {
            // 预期的超时异常
            logger.info("任务按预期超时: {}", e.getMessage());
        }
        
        // 验证统计信息
        TimeoutCancellationManager.TimeoutStats stats = timeoutManager.getStats();
        assertTrue(stats.getTotalTimeouts() > 0 || stats.getTotalCancellations() > 0);
    }
    
    @Test
    void testAdaptiveThreadPool() throws Exception {
        logger.info("测试自适应线程池");
        
        String dbType = "mysql";
        
        // 提交多个任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int taskNum = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(200); // 模拟任务执行
                    logger.debug("任务 {} 完成", taskNum);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, threadPoolManager.getExecutor(dbType));
            
            futures.add(future);
        }
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(10, TimeUnit.SECONDS);
        
        // 验证线程池统计
        java.util.Map<String, AdaptiveThreadPoolManager.ThreadPoolStats> stats = threadPoolManager.getThreadPoolStats();
        assertTrue(stats.containsKey(dbType));
        
        AdaptiveThreadPoolManager.ThreadPoolStats poolStats = stats.get(dbType);
        assertTrue(poolStats.getCompletedTaskCount() >= 5);
    }
    
    @Test
    void testCircuitBreaker() throws Exception {
        logger.info("测试熔断器");
        
        CircuitBreaker circuitBreaker = new CircuitBreaker("test-circuit", 2, 1000, 2000);
        
        // 模拟连续失败
        for (int i = 0; i < 3; i++) {
            try {
                CompletableFuture<String> task = circuitBreaker.execute(() -> {
                    return CompletableFuture.failedFuture(new RuntimeException("模拟失败"));
                });
                task.get();
            } catch (Exception e) {
                logger.debug("预期的失败: {}", e.getMessage());
            }
        }
        
        // 验证熔断器已开启
        assertTrue(circuitBreaker.isOpen());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertTrue(circuitBreaker.getFailureCount() >= 2);
    }
    
    @Test
    void testTaskCancellation() throws Exception {
        logger.info("测试任务取消");
        
        MetricConfig config = createTestConfig("cancellable_task");
        
        // 创建可取消的长时间任务
        Supplier<MetricResult> longTask = () -> {
            try {
                Thread.sleep(10000); // 10秒任务
                MetricResult result = new MetricResult();
                result.setMetricName("cancellable_task");
                result.setValue("completed");
                result.setTimestamp(System.currentTimeMillis());
                result.setSuccess(true);
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task cancelled", e);
            }
        };
        
        // 启动任务
        CompletableFuture<MetricResult> future = asyncExecutor.executeAsync(config, longTask);
        
        // 等待一小段时间后取消
        Thread.sleep(100);
        
        // 取消所有任务
        int cancelledCount = asyncExecutor.cancelAllTasks();
        assertTrue(cancelledCount > 0);
        
        // 验证任务被取消
        try {
            future.get(1, TimeUnit.SECONDS);
            fail("任务应该已经被取消");
        } catch (Exception e) {
            logger.info("任务按预期被取消: {}", e.getMessage());
        }
    }
    
    /**
     * 创建测试配置
     */
    private MetricConfig createTestConfig(String name) {
        MetricConfig config = new MetricConfig();
        config.setName(name);
        config.setSql("SELECT 1 as value");
        config.setDescription("测试配置: " + name);
        return config;
    }
}