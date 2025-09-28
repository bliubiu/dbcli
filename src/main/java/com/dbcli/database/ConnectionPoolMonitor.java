package com.dbcli.database;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接池监控器
 * 监控连接池健康状态并提供告警
 */
public class ConnectionPoolMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitor.class);
    
    private final Map<String, PoolStats> poolStats = new ConcurrentHashMap<>();
    
    /**
     * 记录连接获取
     */
    public void recordConnectionAcquisition(String poolKey) {
        poolStats.computeIfAbsent(poolKey, k -> new PoolStats()).acquisitionCount.incrementAndGet();
    }
    
    /**
     * 记录连接释放
     */
    public void recordConnectionRelease(String poolKey) {
        poolStats.computeIfAbsent(poolKey, k -> new PoolStats()).releaseCount.incrementAndGet();
    }
    
    /**
     * 检查连接池健康状态
     */
    public void checkPoolHealth(Map<String, HikariDataSource> dataSources) {
        dataSources.forEach((poolKey, dataSource) -> {
            try {
                checkSinglePoolHealth(poolKey, dataSource);
            } catch (Exception e) {
                logger.error("检查连接池健康状态失败: {}", poolKey, e);
            }
        });
    }
    
    /**
     * 检查单个连接池健康状态
     */
    private void checkSinglePoolHealth(String poolKey, HikariDataSource dataSource) {
        try {
            int activeConnections = dataSource.getHikariPoolMXBean().getActiveConnections();
            int totalConnections = dataSource.getHikariPoolMXBean().getTotalConnections();
            int maxPoolSize = dataSource.getMaximumPoolSize();
            int threadsAwaiting = dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
            
            double utilizationRate = (double) activeConnections / maxPoolSize;
            
            // 连接池使用率告警
            if (utilizationRate > 0.9) {
                logger.warn("连接池使用率过高: {} (使用率: {:.1f}%, 活跃连接: {}/{}, 等待线程: {})", 
                    poolKey, utilizationRate * 100, activeConnections, maxPoolSize, threadsAwaiting);
            } else if (utilizationRate > 0.8) {
                logger.info("连接池使用率较高: {} (使用率: {:.1f}%, 活跃连接: {}/{}, 等待线程: {})", 
                    poolKey, utilizationRate * 100, activeConnections, maxPoolSize, threadsAwaiting);
            }
            
            // 等待线程告警
            if (threadsAwaiting > 5) {
                logger.warn("连接池等待线程过多: {} (等待线程: {}, 可能存在连接泄漏或池容量不足)", 
                    poolKey, threadsAwaiting);
            }
            
            // 连接泄漏检测
            PoolStats stats = poolStats.get(poolKey);
            if (stats != null) {
                long acquisitions = stats.acquisitionCount.get();
                long releases = stats.releaseCount.get();
                long potentialLeaks = acquisitions - releases;
                
                if (potentialLeaks > 10) {
                    logger.warn("检测到潜在连接泄漏: {} (获取: {}, 释放: {}, 差值: {})", 
                        poolKey, acquisitions, releases, potentialLeaks);
                }
            }
            
            // 记录详细统计信息（DEBUG级别）
            if (logger.isDebugEnabled()) {
                logger.debug("连接池状态: {} - 活跃: {}, 空闲: {}, 总计: {}, 最大: {}, 等待: {}, 使用率: {:.1f}%",
                    poolKey, activeConnections, 
                    dataSource.getHikariPoolMXBean().getIdleConnections(),
                    totalConnections, maxPoolSize, threadsAwaiting, utilizationRate * 100);
            }
            
        } catch (Exception e) {
            logger.error("获取连接池统计信息失败: {}", poolKey, e);
        }
    }
    
    /**
     * 获取连接池统计摘要
     */
    public String getHealthSummary(Map<String, HikariDataSource> dataSources) {
        StringBuilder summary = new StringBuilder();
        summary.append("连接池健康摘要:\n");
        
        dataSources.forEach((poolKey, dataSource) -> {
            try {
                int active = dataSource.getHikariPoolMXBean().getActiveConnections();
                int total = dataSource.getHikariPoolMXBean().getTotalConnections();
                int max = dataSource.getMaximumPoolSize();
                double utilization = (double) active / max * 100;
                
                summary.append(String.format("  %s: %d/%d (%.1f%%) 活跃连接\n", 
                    poolKey, active, max, utilization));
                    
            } catch (Exception e) {
                summary.append(String.format("  %s: 状态获取失败 - %s\n", poolKey, e.getMessage()));
            }
        });
        
        return summary.toString();
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        poolStats.clear();
        logger.info("连接池统计信息已重置");
    }
    
    /**
     * 连接池统计信息
     */
    private static class PoolStats {
        final AtomicLong acquisitionCount = new AtomicLong(0);
        final AtomicLong releaseCount = new AtomicLong(0);
    }
}