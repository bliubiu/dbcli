package com.dbcli.trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪上下文
 * 存储单次追踪的所有信息
 */
public class TraceContext {
    
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String operationName;
    private final long startTime;
    private long endTime;
    private final Map<String, String> tags;
    private final List<String> events;
    private final List<Exception> exceptions;
    
    public TraceContext(String traceId, String spanId, String operationName) {
        this(traceId, spanId, operationName, null);
    }
    
    public TraceContext(String traceId, String spanId, String operationName, String parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.operationName = operationName;
        this.parentSpanId = parentSpanId;
        this.startTime = System.currentTimeMillis();
        this.tags = new HashMap<>();
        this.events = new ArrayList<>();
        this.exceptions = new ArrayList<>();
    }
    
    /**
     * 添加标签
     */
    public void addTag(String key, String value) {
        tags.put(key, value);
    }
    
    /**
     * 添加事件
     */
    public void addEvent(String event) {
        events.add(System.currentTimeMillis() + ": " + event);
    }
    
    /**
     * 记录异常
     */
    public void recordException(Throwable throwable) {
        exceptions.add(new Exception(throwable.getMessage(), throwable));
        addTag("error", "true");
        addTag("error.type", throwable.getClass().getSimpleName());
        addTag("error.message", throwable.getMessage());
    }
    
    /**
     * 结束追踪
     */
    public void finish() {
        this.endTime = System.currentTimeMillis();
    }
    
    /**
     * 获取持续时间（毫秒）
     */
    public long getDurationMs() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return end - startTime;
    }
    
    /**
     * 是否有错误
     */
    public boolean hasError() {
        return !exceptions.isEmpty();
    }
    
    /**
     * 是否已完成
     */
    public boolean isFinished() {
        return endTime > 0;
    }
    
    // Getters
    
    public String getTraceId() {
        return traceId;
    }
    
    public String getSpanId() {
        return spanId;
    }
    
    public String getParentSpanId() {
        return parentSpanId;
    }
    
    public String getOperationName() {
        return operationName;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }
    
    public List<String> getEvents() {
        return new ArrayList<>(events);
    }
    
    public List<Exception> getExceptions() {
        return new ArrayList<>(exceptions);
    }
    
    @Override
    public String toString() {
        return String.format("TraceContext{traceId='%s', spanId='%s', operation='%s', duration=%dms, hasError=%s}", 
            traceId, spanId, operationName, getDurationMs(), hasError());
    }
}