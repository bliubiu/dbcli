package com.dbcli.example;

import com.dbcli.monitor.MicrometerMetricsCollector;
import com.dbcli.util.TraceContext;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可观测性功能使用示例
 * 演示如何使用Micrometer指标和MDC日志追踪
 */
public class ObservabilityExample {
    private static final Logger logger = LoggerFactory.getLogger(ObservabilityExample.class);
    
    private final MicrometerMetricsCollector metricsCollector;
    
    public ObservabilityExample() {
        this.metricsCollector = new MicrometerMetricsCollector();
    }
    
    /**
     * 演示基本的追踪和指标收集
     */
    public void demonstrateBasicObservability() {
        // 开始追踪
        String traceId = TraceContext.startTrace("demo_operation");
        logger.info("开始演示可观测性功能");
        
        try {
            // 设置数据库上下文
            TraceContext.setDatabaseContext("MySQL", "test_system");
            TraceContext.setMetricContext("demo_metric");
            
            // 开始计时
            Timer.Sample sample = metricsCollector.startQuery("MySQL", "test_system", "demo_metric");
            
            // 模拟业务操作
            simulateBusinessOperation();
            
            // 记录成功
            metricsCollector.recordQueryCompletion(sample, "MySQL", "test_system", 
                "demo_metric", true, null);
            
            logger.info("演示操作完成");
            
        } catch (Exception e) {
            logger.error("演示操作失败", e);
        } finally {
            TraceContext.clear();
        }
    }
    
    /**
     * 演示嵌套追踪
     */
    public void demonstrateNestedTracing() {
        String traceId = TraceContext.startTrace("parent_operation");
        logger.info("开始父操作");
        
        try {
            // 第一个子操作
            TraceContext.executeWithContext("child_operation_1", (java.util.function.Supplier<Void>) () -> {
                logger.info("执行子操作1");
                simulateWork(100);
                return null;
            });
            
            // 第二个子操作
            TraceContext.executeWithContext("child_operation_2", (java.util.function.Supplier<Void>) () -> {
                logger.info("执行子操作2");
                simulateWork(200);
                return null;
            });
            
            logger.info("父操作完成");
            
        } finally {
            TraceContext.clear();
        }
    }
    
    /**
     * 演示数据库上下文追踪
     */
    public void demonstrateDatabaseContextTracing() {
        String traceId = TraceContext.startTrace("database_operations");
        logger.info("开始数据库操作演示");
        
        try {
            // MySQL操作
            TraceContext.executeWithDatabaseContext("MySQL", "prod_system", "mysql_query", () -> {
                logger.info("执行MySQL查询");
                simulateWork(150);
                return null;
            });
            
            // Oracle操作
            TraceContext.executeWithDatabaseContext("Oracle", "prod_system", "oracle_query", () -> {
                logger.info("执行Oracle查询");
                simulateWork(300);
                return null;
            });
            
            logger.info("数据库操作演示完成");
            
        } finally {
            TraceContext.clear();
        }
    }
    
    /**
     * 演示自定义指标
     */
    public void demonstrateCustomMetrics() {
        String traceId = TraceContext.startTrace("custom_metrics");
        logger.info("开始自定义指标演示");
        
        try {
            // 自定义计数器
            metricsCollector.incrementCounter("demo.custom.counter", 
                "演示自定义计数器", 
                Tags.of("type", "demo", "environment", "test"));
            
            // 自定义计时器
            Timer.Sample timerSample = metricsCollector.startTimer("demo.custom.timer", 
                "演示自定义计时器", 
                Tags.of("operation", "custom_work"));
            
            simulateWork(250);
            
            metricsCollector.stopTimer(timerSample, "demo.custom.timer", 
                Tags.of("operation", "custom_work"));
            
            // 自定义Gauge
            metricsCollector.registerGauge("demo.custom.gauge", 
                "演示自定义Gauge", 
                this, 
                obj -> Math.random() * 100,
                Tags.of("type", "random"));
            
            logger.info("自定义指标演示完成");
            
        } finally {
            TraceContext.clear();
        }
    }
    
    /**
     * 获取指标摘要
     */
    public void printMetricsSummary() {
        MicrometerMetricsCollector.MetricsSummary summary = metricsCollector.getSummary();
        logger.info("指标摘要: {}", summary);
        
        // 输出Prometheus格式指标
        String prometheusMetrics = metricsCollector.getPrometheusMetrics();
        logger.info("Prometheus指标:\n{}", prometheusMetrics);
    }
    
    /**
     * 模拟业务操作
     */
    private void simulateBusinessOperation() {
        TraceContext.executeWithContext("data_processing", () -> {
            logger.info("处理数据");
            simulateWork(100);
        });
        
        TraceContext.executeWithContext("result_validation", () -> {
            logger.info("验证结果");
            simulateWork(50);
        });
    }
    
    /**
     * 模拟工作负载
     */
    private void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 主方法 - 运行演示
     */
    public static void main(String[] args) {
        ObservabilityExample example = new ObservabilityExample();
        
        logger.info("=== 可观测性功能演示 ===");
        
        // 基本功能演示
        example.demonstrateBasicObservability();
        
        // 嵌套追踪演示
        example.demonstrateNestedTracing();
        
        // 数据库上下文演示
        example.demonstrateDatabaseContextTracing();
        
        // 自定义指标演示
        example.demonstrateCustomMetrics();
        
        // 输出指标摘要
        example.printMetricsSummary();
        
        logger.info("=== 演示完成 ===");
    }
}