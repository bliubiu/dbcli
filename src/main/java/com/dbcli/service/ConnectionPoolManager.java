package com.dbcli.service;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接池管理器
 */
public class ConnectionPoolManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolManager.class);
    
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong successfulConnections = new AtomicLong(0);
    private final AtomicLong failedConnections = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    public ConnectionPoolManager() {
        logger.info("连接池管理器已初始化");
    }
    
    /**
     * 初始化连接池
     */
    public void initializePools(Map<String, DatabaseConfig> databaseConfigs) {
        for (Map.Entry<String, DatabaseConfig> entry : databaseConfigs.entrySet()) {
            String systemName = entry.getKey();
            DatabaseConfig dbConfig = entry.getValue();
            
            if (dbConfig.isEnable()) {
                try {
                    createDataSource(systemName, dbConfig);
                } catch (Exception e) {
                    logger.error("初始化数据源失败: {}", systemName, e);
                }
            }
        }
    }
    
    /**
     * 创建数据源
     */
    public HikariDataSource createDataSource(String systemName, DatabaseConfig dbConfig) {
        if (dataSources.containsKey(systemName)) {
            return dataSources.get(systemName);
        }
        
        try {
            // 获取主节点
            DatabaseNode masterNode = getMasterNode(dbConfig);
            if (masterNode == null) {
                throw new RuntimeException("未找到主节点配置");
            }
            
            String jdbcUrl = buildJdbcUrl(dbConfig, masterNode);
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(dbConfig.getUsername());
            config.setPassword(dbConfig.getPassword());
            config.setDriverClassName(getDriverClassName(dbConfig.getType()));
            
            // 连接池配置
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setLeakDetectionThreshold(60000);
            
            HikariDataSource dataSource = new HikariDataSource(config);
            dataSources.put(systemName, dataSource);
            
            logger.info("数据源创建成功: {} -> {}", systemName, jdbcUrl);
            return dataSource;
            
        } catch (Exception e) {
            logger.error("创建数据源失败: {}", systemName, e);
            throw new RuntimeException("创建数据源失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取主节点
     */
    private DatabaseNode getMasterNode(DatabaseConfig dbConfig) {
        if (dbConfig.getNodes() == null || dbConfig.getNodes().isEmpty()) {
            return null;
        }
        
        // 优先查找master节点
        for (DatabaseNode node : dbConfig.getNodes()) {
            if ("master".equals(node.getRole())) {
                return node;
            }
        }
        
        // 如果没有master，返回第一个节点
        return dbConfig.getNodes().get(0);
    }
    
    /**
     * 构建JDBC URL
     */
    private String buildJdbcUrl(DatabaseConfig dbConfig, DatabaseNode node) {
        String host = node.getHost();
        int port = node.getPort() != null ? node.getPort() : dbConfig.getPort();
        
        switch (dbConfig.getType().toLowerCase()) {
            case "oracle":
                if (node.getSvcName() != null) {
                    return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, node.getSvcName());
                } else if (node.getSidName() != null) {
                    return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, node.getSidName());
                } else {
                    return String.format("jdbc:oracle:thin:@%s:%d:XE", host, port);
                }
            case "mysql":
                String svcName = node.getSvcName() != null ? node.getSvcName() : "mysql";
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", host, port, svcName);
            case "postgresql":
                String dbName = node.getSvcName() != null ? node.getSvcName() : "postgres";
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
            case "dm":
                return String.format("jdbc:dm://%s:%d", host, port);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbConfig.getType());
        }
    }
    
    /**
     * 获取连接
     */
    public Connection getConnection(String dataSourceKey) throws SQLException {
        HikariDataSource dataSource = dataSources.get(dataSourceKey);
        if (dataSource == null) {
            throw new SQLException("数据源不存在: " + dataSourceKey);
        }
        
        long startTime = System.currentTimeMillis();
        try {
            Connection connection = dataSource.getConnection();
            activeConnections.incrementAndGet();
            totalConnections.incrementAndGet();
            successfulConnections.incrementAndGet();
            
            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);
            
            return connection;
        } catch (SQLException e) {
            failedConnections.incrementAndGet();
            throw e;
        }
    }
    
    /**
     * 释放连接
     */
    public void releaseConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
                activeConnections.decrementAndGet();
            } catch (SQLException e) {
                logger.warn("释放连接时出错", e);
            }
        }
    }
    
    /**
     * 测试连接
     */
    public boolean testConnection(String dataSourceKey) {
        try {
            HikariDataSource dataSource = dataSources.get(dataSourceKey);
            if (dataSource == null) {
                logger.warn("数据源不存在: {}", dataSourceKey);
                return false;
            }
            
            try (Connection conn = dataSource.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    // 执行简单查询验证连接
                    try (var stmt = conn.createStatement()) {
                        stmt.executeQuery("SELECT 1").close();
                    }
                    logger.debug("连接测试成功: {}", dataSourceKey);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.warn("连接测试失败: {}", dataSourceKey, e);
        }
        return false;
    }
    
    /**
     * 测试数据库配置连接（不使用连接池）
     */
    public boolean testDatabaseConfig(DatabaseConfig dbConfig) {
        if (!dbConfig.isEnable()) {
            return false;
        }
        
        try {
            DatabaseNode masterNode = getMasterNode(dbConfig);
            if (masterNode == null) {
                return false;
            }
            
            String jdbcUrl = buildJdbcUrl(dbConfig, masterNode);
            String driverClass = getDriverClassName(dbConfig.getType());
            
            // 加载驱动
            Class.forName(driverClass);
            
            // 测试连接
            try (Connection conn = java.sql.DriverManager.getConnection(
                    jdbcUrl, dbConfig.getUsername(), dbConfig.getPassword())) {
                if (conn != null && !conn.isClosed()) {
                    // 执行简单查询验证连接
                    try (var stmt = conn.createStatement()) {
                        stmt.executeQuery("SELECT 1").close();
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("数据库配置连接测试失败: {} - {}", "unknown", e.getMessage());
        }
        return false;
    }
    
    /**
     * 获取统计信息
     */
    public ConnectionStats getStats() {
        ConnectionStats stats = new ConnectionStats();
        stats.activeConnections = activeConnections.get();
        stats.totalConnections = totalConnections.get();
        stats.successfulConnections = successfulConnections.get();
        stats.failedConnections = failedConnections.get();
        
        if (successfulConnections.get() > 0) {
            stats.avgResponseTime = totalResponseTime.get() / successfulConnections.get();
            stats.successRate = (double) successfulConnections.get() / totalConnections.get() * 100;
        }
        
        return stats;
    }
    
    /**
     * 关闭所有数据源
     */
    public void shutdown() {
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            try {
                entry.getValue().close();
                logger.info("数据源已关闭: {}", entry.getKey());
            } catch (Exception e) {
                logger.warn("关闭数据源时出错: {}", entry.getKey(), e);
            }
        }
        dataSources.clear();
    }
    
    private String getDriverClassName(String dbType) {
        switch (dbType.toLowerCase()) {
            case "oracle":
                return "oracle.jdbc.driver.OracleDriver";
            case "mysql":
                return "com.mysql.cj.jdbc.Driver";
            case "postgresql":
                return "org.postgresql.Driver";
            case "dm":
                return "dm.jdbc.driver.DmDriver";
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
    }
    
    /**
     * 连接统计信息
     */
    public static class ConnectionStats {
        public int activeConnections;
        public long totalConnections;
        public long successfulConnections;
        public long failedConnections;
        public long avgResponseTime;
        public double successRate;
        public double throughput;
        
        public ConnectionStats() {
            this.throughput = 0.0; // 可以根据需要计算
        }
    }
}