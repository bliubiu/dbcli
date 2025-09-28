package com.dbcli.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志管理器 - 提供统一的日志记录接口
 */
public class LogManager {
    
    // 专用日志记录器
    private static final Logger DB_CONNECTION_ERROR_LOGGER = LoggerFactory.getLogger("DB_CONNECTION_ERROR");
    private static final Logger PERFORMANCE_LOGGER = LoggerFactory.getLogger("PERFORMANCE");
    
    // 性能统计
    private static final ConcurrentHashMap<String, AtomicLong> performanceCounters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> performanceTimers = new ConcurrentHashMap<>();
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // 初始化MDC默认值，避免日志中出现空的占位符
    static {
        initMdcDefaults();
    }
    
    /**
     * 初始化MDC默认值
     */
    private static void initMdcDefaults() {
        if (MDC.get("traceId") == null) {
            MDC.put("traceId", "");
        }
        if (MDC.get("spanId") == null) {
            MDC.put("spanId", "");
        }
        if (MDC.get("operation") == null) {
            MDC.put("operation", "");
        }
        if (MDC.get("dbType") == null) {
            MDC.put("dbType", "");
        }
        if (MDC.get("systemName") == null) {
            MDC.put("systemName", "");
        }
        if (MDC.get("metricName") == null) {
            MDC.put("metricName", "");
        }
    }
    
    /**
     * 记录数据库连接失败
     */
    public static void logConnectionFailure(String systemName, String nodeName, String host, String error) {
        String maskedHost = DataMaskUtil.maskIpAddress(host);
        String connectionId = String.format("%s-%s-%s", systemName, nodeName, maskedHost);
        
        // 记录到连接失败文件
        DB_CONNECTION_ERROR_LOGGER.info(connectionId);
        
        // 记录详细错误信息到主日志
        Logger logger = LoggerFactory.getLogger(LogManager.class);
        logger.error("数据库连接失败 - 系统: {}, 节点: {}, 主机: {}, 错误: {}", 
                    systemName, nodeName, maskedHost, error);
    }
    
    /**
     * 记录数据库连接成功
     */
    public static void logConnectionSuccess(String systemName, String nodeName, String host, long connectTimeMs) {
        String maskedHost = DataMaskUtil.maskIpAddress(host);
        Logger logger = LoggerFactory.getLogger("com.dbcli.database");
        
        logger.info("数据库连接成功 - 系统: {}, 节点: {}, 主机: {}, 耗时: {}ms", 
                   systemName, nodeName, maskedHost, connectTimeMs);
        
        // 记录性能统计
        recordPerformance("db_connection_success", connectTimeMs);
    }
    
    /**
     * 记录SQL执行开始
     */
    public static void logSqlExecutionStart(String systemName, String metricName, String sql) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.executor");
        
        // 设置MDC上下文（与logback.xml键一致）
        MDC.put("systemName", systemName != null ? systemName : "");
        MDC.put("metricName", metricName != null ? metricName : "");
        MDC.put("operation", "exec_sql");
        
        logger.info("开始执行SQL - 系统: {}, 指标: {}", systemName, metricName);
        logger.debug("SQL语句: {}", sql);
        
        // 记录执行开始时间
        String timerKey = String.format("%s-%s-%d", systemName, metricName, Thread.currentThread().getId());
        performanceTimers.put(timerKey, System.currentTimeMillis());
    }
    
    /**
     * 记录SQL执行成功
     */
    public static void logSqlExecutionSuccess(String systemName, String metricName, int resultCount, long executionTimeMs) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.executor");
        
        logger.info("SQL执行成功 - 系统: {}, 指标: {}, 结果数: {}, 耗时: {}ms", 
                   systemName, metricName, resultCount, executionTimeMs);
        
        // 清理MDC
        MDC.remove("systemName");
        MDC.remove("metricName");
        
        // 记录性能统计
        recordPerformance("sql_execution_success", executionTimeMs);
        incrementCounter("sql_success_count");
    }
    
    /**
     * 记录SQL执行失败
     */
    public static void logSqlExecutionFailure(String systemName, String metricName, String error, long executionTimeMs) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.executor");
        
        logger.error("SQL执行失败 - 系统: {}, 指标: {}, 错误: {}, 耗时: {}ms", 
                    systemName, metricName, error, executionTimeMs);
        
        // 清理MDC
        MDC.remove("systemName");
        MDC.remove("metricName");
        
        // 记录性能统计
        recordPerformance("sql_execution_failure", executionTimeMs);
        incrementCounter("sql_failure_count");
    }
    
    /**
     * 记录报告生成开始
     */
    public static void logReportGenerationStart(String reportType, String outputPath) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.service.ExcelReportGenerator");
        
        logger.info("开始生成{}报告 - 输出路径: {}", reportType, outputPath);
        
        // 记录生成开始时间
        String timerKey = String.format("report_%s_%d", reportType, Thread.currentThread().getId());
        performanceTimers.put(timerKey, System.currentTimeMillis());
    }
    
    /**
     * 记录报告生成成功
     */
    public static void logReportGenerationSuccess(String reportType, String outputPath, int recordCount, long generationTimeMs) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.service.ExcelReportGenerator");
        
        logger.info("{}报告生成成功 - 文件: {}, 记录数: {}, 耗时: {}ms", 
                   reportType, outputPath, recordCount, generationTimeMs);
        
        // 记录性能统计
        recordPerformance("report_generation_success", generationTimeMs);
        incrementCounter("report_success_count");
    }
    
    /**
     * 记录报告生成失败
     */
    public static void logReportGenerationFailure(String reportType, String error, long generationTimeMs) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.service.ExcelReportGenerator");
        
        logger.error("{}报告生成失败 - 错误: {}, 耗时: {}ms", reportType, error, generationTimeMs);
        
        // 记录性能统计
        recordPerformance("report_generation_failure", generationTimeMs);
        incrementCounter("report_failure_count");
    }
    
    /**
     * 记录性能指标
     */
    public static void recordPerformance(String operation, long timeMs) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String prevOp = MDC.get("operation");
        boolean hadPrev = prevOp != null && !prevOp.isEmpty();
        if (operation != null && !operation.isEmpty()) {
            MDC.put("operation", operation);
        }
        try {
            PERFORMANCE_LOGGER.info("PERF [{}] {} = {}ms", timestamp, operation, timeMs);
        } finally {
            if (hadPrev) {
                MDC.put("operation", prevOp);
            } else {
                MDC.remove("operation");
            }
        }
    }
    
    /**
     * 增加计数器
     */
    public static void incrementCounter(String counterName) {
        performanceCounters.computeIfAbsent(counterName, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 获取计数器值
     */
    public static long getCounterValue(String counterName) {
        AtomicLong counter = performanceCounters.get(counterName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 开始计时
     */
    public static void startTimer(String timerName) {
        performanceTimers.put(timerName, System.currentTimeMillis());
    }
    
    /**
     * 结束计时并记录
     */
    public static long endTimer(String timerName) {
        Long startTime = performanceTimers.remove(timerName);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            recordPerformance(timerName, duration);
            return duration;
        }
        return 0;
    }
    
    /**
     * 记录应用启动信息
     */
    public static void logApplicationStart(String version, String[] args) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.DbCliApplication");
        LogManager.initTraceIfAbsent();
        LogManager.setOperation("startup");
        
        logger.info("=".repeat(80));
        logger.info("数据库命令行工具 dbcli 启动");
        logger.info("版本: {}", version);
        logger.info("启动参数: {}", String.join(" ", args));
        logger.info("启动时间: {}", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        logger.info("=".repeat(80));
        
        startTimer("application_total_time");
    }
    
    /**
     * 记录应用结束信息
     */
    public static void logApplicationEnd(boolean success) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.DbCliApplication");
        setOperation("shutdown");
        try {
            long totalTime = endTimer("application_total_time");
            
            logger.info("=".repeat(80));
            logger.info("数据库命令行工具 dbcli 执行完成");
            logger.info("执行状态: {}", success ? "成功" : "失败");
            logger.info("总耗时: {}ms", totalTime);
            logger.info("结束时间: {}", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            
            // 输出统计信息
            logExecutionStatistics();
            
            logger.info("=".repeat(80));
        } finally {
            clearOperation();
        }
    }
    
    /**
     * 记录执行统计信息
     */
    public static void logExecutionStatistics() {
        Logger logger = LoggerFactory.getLogger("com.dbcli.DbCliApplication");
        
        logger.info("执行统计:");
        logger.info("  SQL成功执行: {} 次", getCounterValue("sql_success_count"));
        logger.info("  SQL执行失败: {} 次", getCounterValue("sql_failure_count"));
        logger.info("  报告生成成功: {} 次", getCounterValue("report_success_count"));
        logger.info("  报告生成失败: {} 次", getCounterValue("report_failure_count"));
        logger.info("  数据库连接成功: {} 次", getCounterValue("db_connection_success"));
        logger.info("  数据库连接失败: {} 次", getCounterValue("db_connection_failure"));
    }
    
    /**
     * 记录配置加载信息
     */
    public static void logConfigurationLoaded(String configType, String configPath, int itemCount) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.config");
        
        logger.info("配置加载成功 - 类型: {}, 路径: {}, 项目数: {}", configType, configPath, itemCount);
    }
    
    /**
     * 记录配置加载失败
     */
    public static void logConfigurationLoadFailure(String configType, String configPath, String error) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.config");
        
        logger.error("配置加载失败 - 类型: {}, 路径: {}, 错误: {}", configType, configPath, error);
    }
    
    /**
     * 记录模板生成信息
     */
    public static void logTemplateGeneration(String templateType, String outputPath) {
        Logger logger = LoggerFactory.getLogger("com.dbcli.service.TemplateService");
        
        logger.info("模板生成成功 - 类型: {}, 输出路径: {}", templateType, outputPath);
    }
    
    /**
     * 关闭日志管理器
     */
    public static void shutdown() {
        cleanup();
    }

    // ==== MDC helpers ====
    public static void initTraceIfAbsent() {
        if (MDC.get("traceId") == null || MDC.get("traceId").isEmpty()) {
            String tid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("traceId", tid);
        }
        if (MDC.get("spanId") == null || MDC.get("spanId").isEmpty()) {
            MDC.put("spanId", "root");
        }
    }

    public static void setOperation(String op) {
        if (op != null) MDC.put("operation", op != null ? op : "");
    }

    public static void clearOperation() {
        MDC.remove("operation");
    }

    public static void setDbContext(String dbType, String systemName, String metricName) {
        MDC.put("dbType", dbType != null ? dbType : "");
        MDC.put("systemName", systemName != null ? systemName : "");
        MDC.put("metricName", metricName != null ? metricName : "");
    }

    public static void clearDbContext() {
        MDC.remove("dbType");
        MDC.remove("systemName");
        MDC.remove("metricName");
    }
    
    /**
     * 清理资源
     */
    public static void cleanup() {
        performanceCounters.clear();
        performanceTimers.clear();
        MDC.clear();
    }
}