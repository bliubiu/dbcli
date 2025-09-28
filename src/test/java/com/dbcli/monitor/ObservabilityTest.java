package com.dbcli.monitor;

import com.dbcli.util.TraceContext;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 可观测性功能测试
 */
public class ObservabilityTest {
    
    private MicrometerMetricsCollector metricsCollector;
    
    @BeforeEach
    void setUp() {
        metricsCollector = new MicrometerMetricsCollector();
        TraceContext.clear(); // 清除之前的上下文
    }
    
    @Test
    void testTraceContextBasicFunctionality() {
        // 测试基本追踪功能
        String traceId = TraceContext.startTrace("test_operation");
        
        assertNotNull(traceId);
        assertEquals(traceId, TraceContext.getCurrentTraceId());
        assertNotNull(TraceContext.getCurrentSpanId());
        assertEquals("test_operation", TraceContext.getCurrentOperation());
        
        TraceContext.clear();
        assertNull(TraceContext.getCurrentTraceId());
    }
    
    @Test
    void testDatabaseContextSetting() {
        TraceContext.startTrace("db_test");
        TraceContext.setDatabaseContext("MySQL", "test_system");
        TraceContext.setMetricContext("test_metric");
        
        assertEquals("MySQL", MDC.get(TraceContext.DB_TYPE));
        assertEquals("test_system", MDC.get(TraceContext.SYSTEM_NAME));
        assertEquals("test_metric", MDC.get(TraceContext.METRIC_NAME));
        
        TraceContext.clear();
    }
    
    @Test
    void testContextSnapshot() {
        TraceContext.startTrace("snapshot_test");
        TraceContext.setDatabaseContext("Oracle", "prod_system");
        TraceContext.setMetricContext("snapshot_metric");
        
        TraceContext.ContextSnapshot snapshot = TraceContext.getSnapshot();
        
        assertNotNull(snapshot.getTraceId());
        assertEquals("Oracle", snapshot.getDbType());
        assertEquals("prod_system", snapshot.getSystemName());
        assertEquals("snapshot_metric", snapshot.getMetricName());
        
        // 清除上下文
        TraceContext.clear();
        assertNull(TraceContext.getCurrentTraceId());
        
        // 恢复快照
        TraceContext.restoreSnapshot(snapshot);
        assertEquals(snapshot.getTraceId(), TraceContext.getCurrentTraceId());
        assertEquals("Oracle", MDC.get(TraceContext.DB_TYPE));
        
        TraceContext.clear();
    }
    
    @Test
    void testExecuteWithContext() {
        TraceContext.startTrace("parent_operation");
        
        // 使用 Supplier 版本的 executeWithContext
        java.util.function.Supplier<String> supplier = () -> {
            assertEquals("child_operation", TraceContext.getCurrentOperation());
            return "success";
        };
        
        String result = TraceContext.executeWithContext("child_operation", supplier);
        
        assertEquals("success", result);
        assertEquals("parent_operation", TraceContext.getCurrentOperation());
        
        TraceContext.clear();
    }
    
    @Test
    void testMicrometerMetricsCollection() {
        // 测试查询指标记录
        Timer.Sample sample = metricsCollector.startQuery("MySQL", "test_system", "test_metric");
        
        // 模拟一些工作
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        metricsCollector.recordQueryCompletion(sample, "MySQL", "test_system", 
            "test_metric", true, null);
        
        // 验证指标摘要
        MicrometerMetricsCollector.MetricsSummary summary = metricsCollector.getSummary();
        assertEquals(1, summary.getTotalQueries());
        assertEquals(1, summary.getSuccessfulQueries());
        assertEquals(0, summary.getFailedQueries());
        assertEquals(100.0, summary.getSuccessRate(), 0.1);
    }
    
    @Test
    void testCustomMetrics() {
        // 测试自定义计数器
        metricsCollector.incrementCounter("test.counter", "测试计数器", 
            Tags.of("type", "test"));
        
        // 测试自定义计时器
        Timer.Sample timerSample = metricsCollector.startTimer("test.timer", "测试计时器", 
            Tags.of("operation", "test"));
        
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        metricsCollector.stopTimer(timerSample, "test.timer", 
            Tags.of("operation", "test"));
        
        // 测试自定义Gauge
        metricsCollector.registerGauge("test.gauge", "测试Gauge", 
            this, obj -> 42.0, Tags.of("type", "test"));
        
        // 验证指标注册表不为空
        assertNotNull(metricsCollector.getMeterRegistry());
        assertTrue(metricsCollector.getMeterRegistry().getMeters().size() > 0);
    }
    
    @Test
    void testConnectionMetrics() {
        // 测试连接指标
        metricsCollector.incrementActiveConnections();
        metricsCollector.incrementActiveConnections();
        
        MicrometerMetricsCollector.MetricsSummary summary = metricsCollector.getSummary();
        assertEquals(2, summary.getActiveConnections());
        
        metricsCollector.decrementActiveConnections();
        summary = metricsCollector.getSummary();
        assertEquals(1, summary.getActiveConnections());
    }
    
    @Test
    void testPrometheusMetricsExport() {
        // 记录一些指标
        Timer.Sample sample = metricsCollector.startQuery("PostgreSQL", "test_db", "test_query");
        metricsCollector.recordQueryCompletion(sample, "PostgreSQL", "test_db", 
            "test_query", true, null);
        
        // 获取Prometheus格式的指标
        String prometheusMetrics = metricsCollector.getPrometheusMetrics();
        
        assertNotNull(prometheusMetrics);
        assertFalse(prometheusMetrics.isEmpty());
        assertTrue(prometheusMetrics.contains("dbcli_queries_total"));
    }
    
    @Test
    void testFailedQueryMetrics() {
        Timer.Sample sample = metricsCollector.startQuery("MySQL", "test_system", "failing_metric");
        
        Exception testException = new RuntimeException("测试异常");
        metricsCollector.recordQueryCompletion(sample, "MySQL", "test_system", 
            "failing_metric", false, testException);
        
        MicrometerMetricsCollector.MetricsSummary summary = metricsCollector.getSummary();
        assertEquals(1, summary.getTotalQueries());
        assertEquals(0, summary.getSuccessfulQueries());
        assertEquals(1, summary.getFailedQueries());
        assertEquals(0.0, summary.getSuccessRate(), 0.1);
    }
}