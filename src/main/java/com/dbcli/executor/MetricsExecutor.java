package com.dbcli.executor;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标执行器接口
 * 定义指标收集和执行的核心方法
 */
public interface MetricsExecutor {
    
    /**
     * 执行指定的指标收集
     * 
     * @param databaseConfigs 数据库配置映射
     * @param metricConfigs 指标配置列表
     * @param timeoutSeconds 超时时间（秒）
     * @return 指标执行结果列表
     */
    List<MetricResult> executeMetrics(Map<String, DatabaseConfig> databaseConfigs,
                                     List<MetricConfig> metricConfigs, 
                                     int timeoutSeconds);
    
    /**
     * 执行所有指标收集
     * 
     * @param configPath 数据库配置路径
     * @param metricsPath 指标配置路径
     * @return 指标执行结果列表
     */
    List<MetricResult> executeAllMetrics(String configPath, String metricsPath);
    
    /**
     * 设置失败的加密主机列表
     * 
     * @param hosts 失败主机集合
     */
    void setFailedEncryptedHosts(Set<String> hosts);
    
    /**
     * 获取执行统计信息
     * 
     * @return 执行统计信息
     */
    ExecutionStats getExecutionStats();
    
    /**
     * 关闭执行器，释放资源
     */
    void shutdown();
    
    /**
     * 执行统计信息
     */
    class ExecutionStats {
        private int totalTasks;
        private int completedTasks;
        private int failedTasks;
        private long totalExecutionTime;
        private long averageExecutionTime;
        
        public int getTotalTasks() { return totalTasks; }
        public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }
        
        public int getCompletedTasks() { return completedTasks; }
        public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
        
        public int getFailedTasks() { return failedTasks; }
        public void setFailedTasks(int failedTasks) { this.failedTasks = failedTasks; }
        
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public void setTotalExecutionTime(long totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }
        
        public long getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(long averageExecutionTime) { this.averageExecutionTime = averageExecutionTime; }
        
        public double getSuccessRate() {
            return totalTasks > 0 ? (double) completedTasks / totalTasks : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ExecutionStats{总任务: %d, 完成: %d, 失败: %d, 成功率: %.2f%%, 平均耗时: %dms}", 
                totalTasks, completedTasks, failedTasks, getSuccessRate() * 100, averageExecutionTime);
        }
    }
}