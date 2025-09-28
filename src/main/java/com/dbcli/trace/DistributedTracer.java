package com.dbcli.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式追踪器
 * 为复杂查询提供链路追踪能力
 */
public class DistributedTracer {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedTracer.class);
    
    // 追踪上下文存储
    private static final ThreadLocal<TraceContext> TRACE_CONTEXT = new ThreadLocal<>();
    
    // 全局追踪统计
    private static final ConcurrentHashMap<String, TraceStatistics> TRACE_STATS = new ConcurrentHashMap<>();
    
    // 追踪ID生成器
    private static final AtomicLong TRACE_ID_GENERATOR = new AtomicLong(0);
    
    /**
     * 开始新的追踪
     */
    public static TraceContext startTrace(String operationName) {
        String traceId = generateTraceId();
        String spanId = generateSpanId();
        
        TraceContext context = new TraceContext(traceId, spanId, operationName);
        TRACE_CONTEXT.set(context);
        
        // 设置MDC用于日志关联
        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        MDC.put("operation", operationName);
        
        logger.info("开始追踪: {} [traceId={}, spanId={}]", operationName, traceId, spanId);
        
        return context;
    }
    
    /**
     * 开始子追踪
     */
    public static TraceContext startChildTrace(String operationName) {
        TraceContext parentContext = TRACE_CONTEXT.get();
        if (parentContext == null) {
            return startTrace(operationName);
        }
        
        String spanId = generateSpanId();
        TraceContext childContext = new TraceContext(
            parentContext.getTraceId(), 
            spanId, 
            operationName, 
            parentContext.getSpanId()
        );
        
        TRACE_CONTEXT.set(childContext);
        
        // 更新MDC
        MDC.put("spanId", spanId);
        MDC.put("operation", operationName);
        MDC.put("parentSpanId", parentContext.getSpanId());
        
        logger.info("开始子追踪: {} [traceId={}, spanId={}, parentSpanId={}]", 
            operationName, childContext.getTraceId(), spanId, parentContext.getSpanId());
        
        return childContext;
    }
    
    /**
     * 添加追踪标签
     */
    public static void addTag(String key, String value) {
        TraceContext context = TRACE_CONTEXT.get();
        if (context != null) {
            context.addTag(key, value);
            MDC.put("tag." + key, value);
            logger.debug("添加追踪标签: {}={}", key, value);
        }
    }
    
    /**
     * 添加追踪事件
     */
    public static void addEvent(String event) {
        TraceContext context = TRACE_CONTEXT.get();
        if (context != null) {
            context.addEvent(event);
            logger.info("追踪事件: {}", event);
        }
    }
    
    /**
     * 记录异常
     */
    public static void recordException(Throwable throwable) {
        TraceContext context = TRACE_CONTEXT.get();
        if (context != null) {
            context.recordException(throwable);
            MDC.put("error", "true");
            MDC.put("errorType", throwable.getClass().getSimpleName());
            logger.error("追踪异常: {}", throwable.getMessage(), throwable);
        }
    }
    
    /**
     * 结束追踪
     */
    public static void finishTrace() {
        TraceContext context = TRACE_CONTEXT.get();
        if (context != null) {
            context.finish();
            
            // 更新统计信息
            updateStatistics(context);
            
            logger.info("结束追踪: {} [traceId={}, spanId={}, duration={}ms]", 
                context.getOperationName(), 
                context.getTraceId(), 
                context.getSpanId(),
                context.getDurationMs());
            
            // 清理上下文
            TRACE_CONTEXT.remove();
            MDC.clear();
        }
    }
    
    /**
     * 获取当前追踪上下文
     */
    public static TraceContext getCurrentContext() {
        return TRACE_CONTEXT.get();
    }
    
    /**
     * 获取追踪统计信息
     */
    public static TraceStatistics getStatistics(String operationName) {
        return TRACE_STATS.get(operationName);
    }
    
    /**
     * 获取所有追踪统计信息
     */
    public static ConcurrentHashMap<String, TraceStatistics> getAllStatistics() {
        return new ConcurrentHashMap<>(TRACE_STATS);
    }
    
    /**
     * 清理追踪统计信息
     */
    public static void clearStatistics() {
        TRACE_STATS.clear();
        logger.info("清理追踪统计信息");
    }
    
    // 私有方法
    
    private static String generateTraceId() {
        return "trace-" + System.currentTimeMillis() + "-" + TRACE_ID_GENERATOR.incrementAndGet();
    }
    
    private static String generateSpanId() {
        return "span-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private static void updateStatistics(TraceContext context) {
        String operationName = context.getOperationName();
        TRACE_STATS.compute(operationName, (key, stats) -> {
            if (stats == null) {
                stats = new TraceStatistics(operationName);
            }
            stats.recordTrace(context);
            return stats;
        });
    }
}