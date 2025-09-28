package com.dbcli.monitor;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer指标收集器
 * 提供标准化的指标监控和导出功能
 */
public class MicrometerMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(MicrometerMetricsCollector.class);
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Gauge> gauges = new ConcurrentHashMap<>();
    
    // 业务指标
    private final Counter totalQueries;
    private final Counter successfulQueries;
    private final Counter failedQueries;
    private final Timer queryExecutionTime;
    private final Timer connectionAcquisitionTime;
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong activeQueries = new AtomicLong(0);
    
    public MicrometerMetricsCollector() {
        this(createDefaultRegistry());
    }
    
    public MicrometerMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 注册JVM指标
        registerJvmMetrics();
        
        // 初始化业务指标
        this.totalQueries = Counter.builder("dbcli.queries.total")
            .description("Total number of database queries executed")
            .register(meterRegistry);
            
        this.successfulQueries = Counter.builder("dbcli.queries.successful")
            .description("Number of successful database queries")
            .register(meterRegistry);
            
        this.failedQueries = Counter.builder("dbcli.queries.failed")
            .description("Number of failed database queries")
            .register(meterRegistry);
            
        this.queryExecutionTime = Timer.builder("dbcli.query.execution.time")
            .description("Database query execution time")
            .register(meterRegistry);
            
        this.connectionAcquisitionTime = Timer.builder("dbcli.connection.acquisition.time")
            .description("Database connection acquisition time")
            .register(meterRegistry);
            
        // 注册活跃连接数指标
        Gauge.builder("dbcli.connections.active", activeConnections, obj -> (double) obj.get())
            .description("Number of active database connections")
            .register(meterRegistry);
            
        // 注册活跃查询数指标
        Gauge.builder("dbcli.queries.active", activeQueries, obj -> (double) obj.get())
            .description("Number of active database queries")
            .register(meterRegistry);
            
        logger.info("Micrometer指标收集器已初始化");
    }
    
    /**
     * 创建默认的指标注册表
     */
    private static MeterRegistry createDefaultRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
    
    /**
     * 注册JVM指标
     */
    private void registerJvmMetrics() {
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmGcMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);
    }
    
    /**
     * 记录查询开始
     */
    public Timer.Sample startQuery(String dbType, String systemName, String metricName) {
        Counter.builder("dbcli.queries.total")
            .tags("db_type", dbType, "system", systemName, "metric", metricName)
            .register(meterRegistry)
            .increment();
        
        activeQueries.incrementAndGet();
        
        return Timer.start(meterRegistry);
    }
    
    /**
     * 记录查询完成
     */
    public void recordQueryCompletion(Timer.Sample sample, String dbType, String systemName, 
                                    String metricName, boolean success, Throwable error) {
        // 记录执行时间
        sample.stop(Timer.builder("dbcli.query.execution.time")
            .tags(
                "db_type", dbType,
                "system", systemName,
                "metric", metricName,
                "success", String.valueOf(success)
            )
            .register(meterRegistry));
        
        // 记录成功/失败计数
        if (success) {
            Counter.builder("dbcli.queries.successful")
                .tags("db_type", dbType, "system", systemName, "metric", metricName)
                .register(meterRegistry)
                .increment();
        } else {
            Counter.builder("dbcli.queries.failed")
                .tags("db_type", dbType, "system", systemName, "metric", metricName, 
                      "error_type", error != null ? error.getClass().getSimpleName() : "unknown")
                .register(meterRegistry)
                .increment();
        }
        
        activeQueries.decrementAndGet();
    }
    
    /**
     * 记录连接获取时间
     */
    public void recordConnectionAcquisition(Duration duration, String dbType, String systemName, boolean success) {
        Timer.builder("dbcli.connection.acquisition.time")
            .tags(
                "db_type", dbType,
                "system", systemName,
                "success", String.valueOf(success)
            )
            .register(meterRegistry)
            .record(duration);
    }
    
    /**
     * 增加活跃连接数
     */
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }
    
    /**
     * 减少活跃连接数
     */
    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }
    
    /**
     * 记录自定义计数器
     */
    public void incrementCounter(String name, String description, Tags tags) {
        String key = name + tags.toString();
        Counter counter = counters.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(meterRegistry)
        );
        counter.increment();
    }
    
    /**
     * 记录自定义计时器
     */
    public Timer.Sample startTimer(String name, String description, Tags tags) {
        String key = name + tags.toString();
        Timer timer = timers.computeIfAbsent(key, k ->
            Timer.builder(name)
                .description(description)
                .tags(tags)
                .register(meterRegistry)
        );
        return Timer.start(meterRegistry);
    }
    
    /**
     * 停止计时器
     */
    public void stopTimer(Timer.Sample sample, String name, Tags tags) {
        String key = name + tags.toString();
        Timer timer = timers.get(key);
        if (timer != null) {
            sample.stop(timer);
        }
    }
    
    /**
     * 注册自定义Gauge
     */
    public <T> void registerGauge(String name, String description, T obj, 
                                 java.util.function.ToDoubleFunction<T> valueFunction, Tags tags) {
        Gauge.builder(name, obj, valueFunction)
            .description(description)
            .tags(tags)
            .register(meterRegistry);
    }
    
    /**
     * 获取指标注册表
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
    
    /**
     * 获取Prometheus格式的指标数据
     */
    public String getPrometheusMetrics() {
        if (meterRegistry instanceof PrometheusMeterRegistry) {
            return ((PrometheusMeterRegistry) meterRegistry).scrape();
        }
        return "Prometheus registry not available";
    }
    
    /**
     * 获取指标摘要
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
            (long) totalQueries.count(),
            (long) successfulQueries.count(),
            (long) failedQueries.count(),
            activeConnections.get(),
            activeQueries.get(),
            queryExecutionTime.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            connectionAcquisitionTime.mean(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }
    
    /**
     * 指标摘要
     */
    public static class MetricsSummary {
        private final long totalQueries;
        private final long successfulQueries;
        private final long failedQueries;
        private final long activeConnections;
        private final long activeQueries;
        private final double avgQueryTime;
        private final double avgConnectionTime;
        
        public MetricsSummary(long totalQueries, long successfulQueries, long failedQueries,
                            long activeConnections, long activeQueries, 
                            double avgQueryTime, double avgConnectionTime) {
            this.totalQueries = totalQueries;
            this.successfulQueries = successfulQueries;
            this.failedQueries = failedQueries;
            this.activeConnections = activeConnections;
            this.activeQueries = activeQueries;
            this.avgQueryTime = avgQueryTime;
            this.avgConnectionTime = avgConnectionTime;
        }
        
        // Getters
        public long getTotalQueries() { return totalQueries; }
        public long getSuccessfulQueries() { return successfulQueries; }
        public long getFailedQueries() { return failedQueries; }
        public long getActiveConnections() { return activeConnections; }
        public long getActiveQueries() { return activeQueries; }
        public double getAvgQueryTime() { return avgQueryTime; }
        public double getAvgConnectionTime() { return avgConnectionTime; }
        
        public double getSuccessRate() {
            return totalQueries > 0 ? (double) successfulQueries / totalQueries * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "总查询: %d, 成功: %d (%.1f%%), 失败: %d, 活跃连接: %d, 活跃查询: %d, 平均查询时间: %.2fms, 平均连接时间: %.2fms",
                totalQueries, successfulQueries, getSuccessRate(), failedQueries,
                activeConnections, activeQueries, avgQueryTime, avgConnectionTime
            );
        }
    }
}