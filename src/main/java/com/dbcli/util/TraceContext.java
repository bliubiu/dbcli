package com.dbcli.util;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 分布式追踪上下文管理器
 * 使用MDC实现日志追踪功能
 */
public class TraceContext {
    
    // MDC键名常量
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String OPERATION = "operation";
    public static final String DB_TYPE = "dbType";
    public static final String SYSTEM_NAME = "systemName";
    public static final String METRIC_NAME = "metricName";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    
    /**
     * 开始新的追踪
     */
    public static String startTrace() {
        String traceId = generateTraceId();
        MDC.put(TRACE_ID, traceId);
        MDC.put(SPAN_ID, generateSpanId());
        return traceId;
    }
    
    /**
     * 开始新的追踪（带操作名称）
     */
    public static String startTrace(String operation) {
        String traceId = startTrace();
        MDC.put(OPERATION, operation);
        return traceId;
    }
    
    /**
     * 开始新的Span
     */
    public static String startSpan(String operation) {
        String spanId = generateSpanId();
        MDC.put(SPAN_ID, spanId);
        MDC.put(OPERATION, operation);
        return spanId;
    }
    
    /**
     * 设置数据库相关上下文
     */
    public static void setDatabaseContext(String dbType, String systemName) {
        MDC.put(DB_TYPE, dbType);
        MDC.put(SYSTEM_NAME, systemName);
    }
    
    /**
     * 设置指标相关上下文
     */
    public static void setMetricContext(String metricName) {
        MDC.put(METRIC_NAME, metricName);
    }
    
    /**
     * 设置用户相关上下文
     */
    public static void setUserContext(String userId, String sessionId) {
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }
        if (sessionId != null) {
            MDC.put(SESSION_ID, sessionId);
        }
    }
    
    /**
     * 获取当前追踪ID
     */
    public static String getCurrentTraceId() {
        return MDC.get(TRACE_ID);
    }
    
    /**
     * 获取当前SpanID
     */
    public static String getCurrentSpanId() {
        return MDC.get(SPAN_ID);
    }
    
    /**
     * 获取当前操作名称
     */
    public static String getCurrentOperation() {
        return MDC.get(OPERATION);
    }
    
    /**
     * 清除当前上下文
     */
    public static void clear() {
        MDC.clear();
    }
    
    /**
     * 清除特定键
     */
    public static void remove(String key) {
        MDC.remove(key);
    }
    
    /**
     * 在指定上下文中执行操作
     */
    public static <T> T executeWithContext(String operation, Supplier<T> supplier) {
        String originalOperation = MDC.get(OPERATION);
        String spanId = startSpan(operation);
        
        try {
            return supplier.get();
        } finally {
            if (originalOperation != null) {
                MDC.put(OPERATION, originalOperation);
            } else {
                MDC.remove(OPERATION);
            }
            MDC.put(SPAN_ID, generateSpanId()); // 恢复父Span
        }
    }
    
    /**
     * 在指定上下文中执行操作（Callable版本）
     */
    public static <T> T executeWithContext(String operation, Callable<T> callable) throws Exception {
        String originalOperation = MDC.get(OPERATION);
        String spanId = startSpan(operation);
        
        try {
            return callable.call();
        } finally {
            if (originalOperation != null) {
                MDC.put(OPERATION, originalOperation);
            } else {
                MDC.remove(OPERATION);
            }
            MDC.put(SPAN_ID, generateSpanId()); // 恢复父Span
        }
    }
    
    /**
     * 在指定上下文中执行操作（Runnable版本）
     */
    public static void executeWithContext(String operation, Runnable runnable) {
        String originalOperation = MDC.get(OPERATION);
        String spanId = startSpan(operation);
        
        try {
            runnable.run();
        } finally {
            if (originalOperation != null) {
                MDC.put(OPERATION, originalOperation);
            } else {
                MDC.remove(OPERATION);
            }
            MDC.put(SPAN_ID, generateSpanId()); // 恢复父Span
        }
    }
    
    /**
     * 在数据库上下文中执行操作
     */
    public static <T> T executeWithDatabaseContext(String dbType, String systemName, 
                                                  String operation, Supplier<T> supplier) {
        // 保存原始上下文
        String originalDbType = MDC.get(DB_TYPE);
        String originalSystemName = MDC.get(SYSTEM_NAME);
        
        try {
            setDatabaseContext(dbType, systemName);
            return executeWithContext(operation, supplier);
        } finally {
            // 恢复原始上下文
            if (originalDbType != null) {
                MDC.put(DB_TYPE, originalDbType);
            } else {
                MDC.remove(DB_TYPE);
            }
            
            if (originalSystemName != null) {
                MDC.put(SYSTEM_NAME, originalSystemName);
            } else {
                MDC.remove(SYSTEM_NAME);
            }
        }
    }
    
    /**
     * 在指标上下文中执行操作
     */
    public static <T> T executeWithMetricContext(String metricName, String operation, Supplier<T> supplier) {
        String originalMetricName = MDC.get(METRIC_NAME);
        
        try {
            setMetricContext(metricName);
            return executeWithContext(operation, supplier);
        } finally {
            if (originalMetricName != null) {
                MDC.put(METRIC_NAME, originalMetricName);
            } else {
                MDC.remove(METRIC_NAME);
            }
        }
    }
    
    /**
     * 生成追踪ID
     */
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * 生成SpanID
     */
    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
    
    /**
     * 获取当前上下文快照
     */
    public static ContextSnapshot getSnapshot() {
        return new ContextSnapshot(
            MDC.get(TRACE_ID),
            MDC.get(SPAN_ID),
            MDC.get(OPERATION),
            MDC.get(DB_TYPE),
            MDC.get(SYSTEM_NAME),
            MDC.get(METRIC_NAME),
            MDC.get(USER_ID),
            MDC.get(SESSION_ID)
        );
    }
    
    /**
     * 恢复上下文快照
     */
    public static void restoreSnapshot(ContextSnapshot snapshot) {
        clear();
        
        if (snapshot.traceId != null) MDC.put(TRACE_ID, snapshot.traceId);
        if (snapshot.spanId != null) MDC.put(SPAN_ID, snapshot.spanId);
        if (snapshot.operation != null) MDC.put(OPERATION, snapshot.operation);
        if (snapshot.dbType != null) MDC.put(DB_TYPE, snapshot.dbType);
        if (snapshot.systemName != null) MDC.put(SYSTEM_NAME, snapshot.systemName);
        if (snapshot.metricName != null) MDC.put(METRIC_NAME, snapshot.metricName);
        if (snapshot.userId != null) MDC.put(USER_ID, snapshot.userId);
        if (snapshot.sessionId != null) MDC.put(SESSION_ID, snapshot.sessionId);
    }
    
    /**
     * 上下文快照
     */
    public static class ContextSnapshot {
        private final String traceId;
        private final String spanId;
        private final String operation;
        private final String dbType;
        private final String systemName;
        private final String metricName;
        private final String userId;
        private final String sessionId;
        
        public ContextSnapshot(String traceId, String spanId, String operation, String dbType,
                             String systemName, String metricName, String userId, String sessionId) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.operation = operation;
            this.dbType = dbType;
            this.systemName = systemName;
            this.metricName = metricName;
            this.userId = userId;
            this.sessionId = sessionId;
        }
        
        // Getters
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public String getOperation() { return operation; }
        public String getDbType() { return dbType; }
        public String getSystemName() { return systemName; }
        public String getMetricName() { return metricName; }
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        
        @Override
        public String toString() {
            return String.format("ContextSnapshot{traceId='%s', spanId='%s', operation='%s', dbType='%s', systemName='%s', metricName='%s'}",
                traceId, spanId, operation, dbType, systemName, metricName);
        }
    }
}