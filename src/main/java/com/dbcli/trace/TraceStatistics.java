package com.dbcli.trace;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 追踪统计信息
 * 记录操作的性能统计数据
 */
public class TraceStatistics {
    
    private final String operationName;
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalDuration = new AtomicLong(0);
    private final AtomicReference<Long> minDuration = new AtomicReference<>(Long.MAX_VALUE);
    private final AtomicReference<Long> maxDuration = new AtomicReference<>(0L);
    private final long createdTime;
    
    public TraceStatistics(String operationName) {
        this.operationName = operationName;
        this.createdTime = System.currentTimeMillis();
    }
    
    /**
     * 记录一次追踪
     */
    public void recordTrace(TraceContext context) {
        long duration = context.getDurationMs();
        
        totalCount.incrementAndGet();
        totalDuration.addAndGet(duration);
        
        if (context.hasError()) {
            errorCount.incrementAndGet();
        } else {
            successCount.incrementAndGet();
        }
        
        // 更新最小持续时间
        minDuration.updateAndGet(current -> Math.min(current, duration));
        
        // 更新最大持续时间
        maxDuration.updateAndGet(current -> Math.max(current, duration));
    }
    
    /**
     * 获取操作名称
     */
    public String getOperationName() {
        return operationName;
    }
    
    /**
     * 获取总调用次数
     */
    public long getTotalCount() {
        return totalCount.get();
    }
    
    /**
     * 获取成功次数
     */
    public long getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * 获取错误次数
     */
    public long getErrorCount() {
        return errorCount.get();
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        long total = totalCount.get();
        return total > 0 ? (double) successCount.get() / total : 0.0;
    }
    
    /**
     * 获取错误率
     */
    public double getErrorRate() {
        long total = totalCount.get();
        return total > 0 ? (double) errorCount.get() / total : 0.0;
    }
    
    /**
     * 获取平均持续时间
     */
    public double getAverageDuration() {
        long total = totalCount.get();
        return total > 0 ? (double) totalDuration.get() / total : 0.0;
    }
    
    /**
     * 获取最小持续时间
     */
    public long getMinDuration() {
        Long min = minDuration.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }
    
    /**
     * 获取最大持续时间
     */
    public long getMaxDuration() {
        return maxDuration.get();
    }
    
    /**
     * 获取总持续时间
     */
    public long getTotalDuration() {
        return totalDuration.get();
    }
    
    /**
     * 获取创建时间
     */
    public long getCreatedTime() {
        return createdTime;
    }
    
    /**
     * 获取运行时长（毫秒）
     */
    public long getUptimeMs() {
        return System.currentTimeMillis() - createdTime;
    }
    
    /**
     * 获取QPS（每秒查询数）
     */
    public double getQps() {
        long uptimeSeconds = getUptimeMs() / 1000;
        return uptimeSeconds > 0 ? (double) totalCount.get() / uptimeSeconds : 0.0;
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        totalCount.set(0);
        successCount.set(0);
        errorCount.set(0);
        totalDuration.set(0);
        minDuration.set(Long.MAX_VALUE);
        maxDuration.set(0L);
    }
    
    @Override
    public String toString() {
        return String.format(
            "TraceStatistics{operation='%s', total=%d, success=%d, error=%d, " +
            "successRate=%.2f%%, avgDuration=%.2fms, minDuration=%dms, maxDuration=%dms, qps=%.2f}",
            operationName, getTotalCount(), getSuccessCount(), getErrorCount(),
            getSuccessRate() * 100, getAverageDuration(), getMinDuration(), getMaxDuration(), getQps()
        );
    }
}