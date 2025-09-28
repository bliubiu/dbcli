package com.dbcli.database;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据库特定连接管理器
 * 为不同数据库类型提供优化的连接池配置和监控
 */
public class DatabaseSpecificConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSpecificConnectionManager.class);
    
    private final Map<String, HikariConfig> dbTypeConfigs;
    private final Map<String, HikariDataSource> dataSources;
    private final ConnectionPoolMonitor monitor;
    private final ScheduledExecutorService scheduler;
    
    public DatabaseSpecificConnectionManager() {
        this.dbTypeConfigs = createDbTypeConfigs();
        this.dataSources = new ConcurrentHashMap<>();
        this.monitor = new ConnectionPoolMonitor();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // 启动连接池监控
        startMonitoring();
    }
    
    /**
     * 为不同数据库类型创建优化配置
     */
    private Map<String, HikariConfig> createDbTypeConfigs() {
        Map<String, HikariConfig> configs = new HashMap<>();
        
        // Oracle配置 - 适合长连接和复杂查询
        HikariConfig oracleConfig = new HikariConfig();
        oracleConfig.setMaximumPoolSize(20);
        oracleConfig.setMinimumIdle(5);
        oracleConfig.setConnectionTimeout(30000);
        oracleConfig.setIdleTimeout(600000);
        oracleConfig.setMaxLifetime(1800000);
        oracleConfig.setLeakDetectionThreshold(60000);
        oracleConfig.setConnectionTestQuery("SELECT 1 FROM DUAL");
        configs.put("oracle", oracleConfig);
        
        // MySQL配置 - 适合高并发短连接
        HikariConfig mysqlConfig = new HikariConfig();
        mysqlConfig.setMaximumPoolSize(15);
        mysqlConfig.setMinimumIdle(3);
        mysqlConfig.setConnectionTimeout(20000);
        mysqlConfig.setIdleTimeout(300000);
        mysqlConfig.setMaxLifetime(1200000);
        mysqlConfig.setConnectionTestQuery("SELECT 1");
        mysqlConfig.addDataSourceProperty("cachePrepStmts", "true");
        mysqlConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        mysqlConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        mysqlConfig.addDataSourceProperty("useServerPrepStmts", "true");
        configs.put("mysql", mysqlConfig);
        
        // PostgreSQL配置 - 平衡性能和资源使用
        HikariConfig pgConfig = new HikariConfig();
        pgConfig.setMaximumPoolSize(12);
        pgConfig.setMinimumIdle(2);
        pgConfig.setConnectionTimeout(25000);
        pgConfig.setIdleTimeout(400000);
        pgConfig.setMaxLifetime(1500000);
        pgConfig.setConnectionTestQuery("SELECT 1");
        configs.put("postgresql", pgConfig);
        
        // 达梦数据库配置
        HikariConfig dmConfig = new HikariConfig();
        dmConfig.setMaximumPoolSize(10);
        dmConfig.setMinimumIdle(2);
        dmConfig.setConnectionTimeout(25000);
        dmConfig.setIdleTimeout(500000);
        dmConfig.setMaxLifetime(1600000);
        dmConfig.setConnectionTestQuery("SELECT 1 FROM DUAL");
        configs.put("dm", dmConfig);
        
        return configs;
    }
    
    /**
     * 获取数据库连接
     */
    public Connection getConnection(String dbType, String systemName, DatabaseNode node, DatabaseConfig config) 
            throws SQLException {
        String key = generateKey(dbType, systemName, node);
        HikariDataSource dataSource = dataSources.computeIfAbsent(key, 
            k -> createDataSource(dbType, systemName, node, config));
        
        if (dataSource == null) {
            throw new SQLException("无法创建数据源: " + key);
        }
        
        Connection conn = dataSource.getConnection();
        monitor.recordConnectionAcquisition(key);
        
        return new ConnectionWrapper(conn, () -> monitor.recordConnectionRelease(key));
    }
    
    /**
     * 创建数据源
     */
    private HikariDataSource createDataSource(String dbType, String systemName, 
                                             DatabaseNode node, DatabaseConfig config) {
        try {
            HikariConfig baseConfig = dbTypeConfigs.get(dbType.toLowerCase());
            if (baseConfig == null) {
                logger.warn("未找到数据库类型 {} 的专用配置，使用默认配置", dbType);
                baseConfig = createDefaultConfig();
            }
            
            // 复制基础配置
            HikariConfig hikariConfig = new HikariConfig();
            copyConfig(baseConfig, hikariConfig);
            
            // 设置连接信息
            String jdbcUrl = buildJdbcUrl(dbType, node, config);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            
            // 设置连接池名称
            hikariConfig.setPoolName(systemName + "-" + node.getHost() + "-" + dbType + "-pool");
            
            // 应用用户自定义配置覆盖
            applyUserConfig(hikariConfig, config);
            
            String maskedHost = com.dbcli.util.DataMaskUtil.maskIpAddress(node.getHost());
            logger.info("创建优化数据源: {} -> type={}, host={}, maxPool={}, minIdle={}", 
                systemName, dbType, maskedHost, 
                hikariConfig.getMaximumPoolSize(), hikariConfig.getMinimumIdle());
            
            return new HikariDataSource(hikariConfig);
            
        } catch (Exception e) {
            logger.error("创建数据源失败: {}-{}", systemName, dbType, e);
            return null;
        }
    }
    
    /**
     * 复制配置
     */
    private void copyConfig(HikariConfig source, HikariConfig target) {
        target.setMaximumPoolSize(source.getMaximumPoolSize());
        target.setMinimumIdle(source.getMinimumIdle());
        target.setConnectionTimeout(source.getConnectionTimeout());
        target.setIdleTimeout(source.getIdleTimeout());
        target.setMaxLifetime(source.getMaxLifetime());
        target.setLeakDetectionThreshold(source.getLeakDetectionThreshold());
        target.setConnectionTestQuery(source.getConnectionTestQuery());
        
        // 复制数据源属性
        source.getDataSourceProperties().forEach((key, value) -> {
            target.addDataSourceProperty(key.toString(), value.toString());
        });
    }
    
    /**
     * 应用用户自定义配置
     */
    private void applyUserConfig(HikariConfig hikariConfig, DatabaseConfig config) {
        if (config.getMaxPoolSize() != null) {
            hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        }
        if (config.getMinIdle() != null) {
            hikariConfig.setMinimumIdle(config.getMinIdle());
        }
        if (config.getConnectionTimeout() != null) {
            hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        }
        if (config.getIdleTimeout() != null) {
            hikariConfig.setIdleTimeout(config.getIdleTimeout());
        }
        if (config.getMaxLifetime() != null) {
            hikariConfig.setMaxLifetime(config.getMaxLifetime());
        }
    }
    
    /**
     * 创建默认配置
     */
    private HikariConfig createDefaultConfig() {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        config.setConnectionTestQuery("SELECT 1");
        return config;
    }
    
    /**
     * 构建JDBC URL
     */
    private String buildJdbcUrl(String dbType, DatabaseNode node, DatabaseConfig config) {
        String host = node.getHost();
        Integer nodePort = node.getPort();
        Integer configPort = config.getPort();
        int port = nodePort != null ? nodePort.intValue() : (configPort != null ? configPort.intValue() : 0);
        String svcName = node.getSvcName();
        String sidName = node.getSidName();

        switch (dbType.toLowerCase()) {
            case "oracle":
                if (svcName != null && !svcName.trim().isEmpty()) {
                    return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, svcName);
                } else if (sidName != null && !sidName.trim().isEmpty()) {
                    return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sidName);
                } else {
                    return String.format("jdbc:oracle:thin:@//%s:%d/", host, port);
                }
            case "mysql":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true&serverTimezone=Asia/Shanghai&connectTimeout=10000&socketTimeout=15000&tcpKeepAlive=true",
                        host, port, svcName != null ? svcName : "");
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/%s?connectTimeout=10&socketTimeout=15&tcpKeepAlive=true", 
                        host, port, svcName != null ? svcName : "postgres");
            case "dm":
                return String.format("jdbc:dm://%s:%d", host, port);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
    }
    
    /**
     * 生成连接键
     */
    private String generateKey(String dbType, String systemName, DatabaseNode node) {
        return String.format("%s-%s-%s-%s", dbType, systemName, node.getHost(), 
            node.getSvcName() != null ? node.getSvcName() : node.getSidName());
    }
    
    /**
     * 启动连接池监控
     */
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                monitor.checkPoolHealth(dataSources);
            } catch (Exception e) {
                logger.error("连接池监控异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 获取连接池统计信息
     */
    public Map<String, PoolMetrics> getPoolMetrics() {
        Map<String, PoolMetrics> metrics = new HashMap<>();
        
        dataSources.forEach((key, dataSource) -> {
            try {
                PoolMetrics poolMetrics = new PoolMetrics();
                poolMetrics.setPoolName(key);
                poolMetrics.setActiveConnections(dataSource.getHikariPoolMXBean().getActiveConnections());
                poolMetrics.setIdleConnections(dataSource.getHikariPoolMXBean().getIdleConnections());
                poolMetrics.setTotalConnections(dataSource.getHikariPoolMXBean().getTotalConnections());
                poolMetrics.setMaxPoolSize(dataSource.getMaximumPoolSize());
                poolMetrics.setThreadsAwaitingConnection(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
                
                metrics.put(key, poolMetrics);
            } catch (Exception e) {
                logger.debug("获取连接池指标失败: {}", key, e);
            }
        });
        
        return metrics;
    }
    
    /**
     * 关闭所有连接池
     */
    public void shutdown() {
        scheduler.shutdown();
        
        dataSources.forEach((key, dataSource) -> {
            try {
                dataSource.close();
                logger.info("关闭连接池: {}", key);
            } catch (Exception e) {
                logger.error("关闭连接池失败: {}", key, e);
            }
        });
        
        dataSources.clear();
        
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 连接池指标
     */
    public static class PoolMetrics {
        private String poolName;
        private int activeConnections;
        private int idleConnections;
        private int totalConnections;
        private int maxPoolSize;
        private int threadsAwaitingConnection;
        private int connectionLeaks;
        
        // Getters and Setters
        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
        
        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
        
        public int getIdleConnections() { return idleConnections; }
        public void setIdleConnections(int idleConnections) { this.idleConnections = idleConnections; }
        
        public int getTotalConnections() { return totalConnections; }
        public void setTotalConnections(int totalConnections) { this.totalConnections = totalConnections; }
        
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        
        public int getThreadsAwaitingConnection() { return threadsAwaitingConnection; }
        public void setThreadsAwaitingConnection(int threadsAwaitingConnection) { 
            this.threadsAwaitingConnection = threadsAwaitingConnection; 
        }
        
        public int getConnectionLeaks() { return connectionLeaks; }
        public void setConnectionLeaks(int connectionLeaks) { this.connectionLeaks = connectionLeaks; }
        
        public double getUtilizationRate() {
            return maxPoolSize > 0 ? (double) activeConnections / maxPoolSize : 0.0;
        }
    }
}