package com.dbcli.storage;

import com.dbcli.model.MetricResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 指标存储管理器
 * 负责管理指标数据的持久化存储，支持多种存储后端
 */
public class MetricsStorageManager {
    private static final Logger logger = LoggerFactory.getLogger(MetricsStorageManager.class);
    
    private StorageConfig config;
    private PostgreSQLStorageService postgreSQLStorageService;
    private List<MetricResult> batchBuffer;
    
    public MetricsStorageManager(StorageConfig config) {
        this.config = config;
        this.batchBuffer = new ArrayList<>();
        
        if (config.isEnabled()) {
            initializeStorage();
        }
    }
    
    /**
     * 初始化存储服务
     */
    private void initializeStorage() {
        try {
            switch (config.getType().toLowerCase()) {
                case "postgresql":
                    postgreSQLStorageService = new PostgreSQLStorageService(
                        config.getHost(),
                        config.getPort(),
                        config.getDatabase(),
                        config.getUsername(),
                        config.getPassword()
                    );
                    postgreSQLStorageService.initialize();
                    logger.info("PostgreSQL存储服务初始化完成");
                    break;
                default:
                    logger.warn("不支持的存储类型: {}", config.getType());
                    break;
            }
        } catch (SQLException e) {
            logger.error("初始化存储服务失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 保存单个指标结果
     */
    public void saveMetricResult(MetricResult result) {
        if (!config.isEnabled()) {
            return;
        }
        
        if (config.isBatchMode()) {
            // 批量模式，添加到缓冲区
            batchBuffer.add(result);
            
            // 如果缓冲区达到批量大小，则执行批量保存
            if (batchBuffer.size() >= config.getBatchSize()) {
                flush();
            }
        } else {
            // 非批量模式，立即保存
            saveMetricResultImmediately(result);
        }
    }
    
    /**
     * 立即保存单个指标结果
     */
    private void saveMetricResultImmediately(MetricResult result) {
        try {
            switch (config.getType().toLowerCase()) {
                case "postgresql":
                    if (postgreSQLStorageService != null) {
                        postgreSQLStorageService.saveMetricResult(result);
                    }
                    break;
                default:
                    logger.warn("不支持的存储类型: {}", config.getType());
                    break;
            }
        } catch (SQLException e) {
            logger.error("保存指标结果失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 批量保存指标结果
     */
    public void saveMetricResults(List<MetricResult> results) {
        if (!config.isEnabled()) {
            return;
        }
        
        if (config.isBatchMode()) {
            // 批量模式，添加到缓冲区
            batchBuffer.addAll(results);
            
            // 如果缓冲区达到批量大小，则执行批量保存
            if (batchBuffer.size() >= config.getBatchSize()) {
                flush();
            }
        } else {
            // 非批量模式，立即保存
            saveMetricResultsImmediately(results);
        }
    }
    
    /**
     * 立即批量保存指标结果
     */
    private void saveMetricResultsImmediately(List<MetricResult> results) {
        try {
            switch (config.getType().toLowerCase()) {
                case "postgresql":
                    if (postgreSQLStorageService != null) {
                        postgreSQLStorageService.saveMetricResults(results);
                    }
                    break;
                default:
                    logger.warn("不支持的存储类型: {}", config.getType());
                    break;
            }
        } catch (SQLException e) {
            logger.error("批量保存指标结果失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 刷新缓冲区，将所有待保存的数据写入存储
     */
    public void flush() {
        if (!config.isEnabled() || batchBuffer.isEmpty()) {
            return;
        }
        
        try {
            switch (config.getType().toLowerCase()) {
                case "postgresql":
                    if (postgreSQLStorageService != null) {
                        postgreSQLStorageService.saveMetricResults(batchBuffer);
                    }
                    break;
                default:
                    logger.warn("不支持的存储类型: {}", config.getType());
                    break;
            }
            
            // 清空缓冲区
            batchBuffer.clear();
        } catch (SQLException e) {
            logger.error("刷新缓冲区失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 关闭存储管理器，释放资源
     */
    public void close() {
        // 刷新缓冲区
        flush();
        
        // 关闭存储服务
        if (postgreSQLStorageService != null) {
            postgreSQLStorageService.close();
        }
        
        logger.info("指标存储管理器已关闭");
    }
    
    /**
     * 获取存储配置
     */
    public StorageConfig getConfig() {
        return config;
    }
    
    /**
     * 设置存储配置
     */
    public void setConfig(StorageConfig config) {
        this.config = config;
    }
}