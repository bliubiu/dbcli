package com.dbcli.database;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接工厂
 */
public class ConnectionFactory {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionFactory.class);

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    static {
        // 静态初始化时加载所有驱动
        DriverLoader.loadAllDrivers();
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection(String systemName, DatabaseNode node, DatabaseConfig config, String dbType)
            throws SQLException {
        String key = buildConnectionKey(systemName, node);

        HikariDataSource dataSource = dataSources.computeIfAbsent(key, k -> {
            try {
                return createDataSource(systemName, node, config, dbType);
            } catch (Exception e) {
                logger.error("创建数据源失败: {}", key, e);
                return null;
            }
        });

        if (dataSource == null) {
            throw new SQLException("无法创建数据源: " + key);
        }

        return dataSource.getConnection();
    }

    /**
     * 创建数据源
     */
    private HikariDataSource createDataSource(String systemName, DatabaseNode node,
                                              DatabaseConfig config, String dbType) {
        HikariConfig hikariConfig = new HikariConfig();

        // 设置JDBC URL
        String jdbcUrl = buildJdbcUrl(dbType, node, config);
        hikariConfig.setJdbcUrl(jdbcUrl);

        // 设置用户名密码
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // 不显式设置驱动类，避免 ClassLoader 冲突。让 DriverManager 基于 jdbcUrl 自动解析驱动。

        // 连接池名称（容错：host 可能为空）
        String poolNameHost = (node.getHost() != null ? node.getHost() : "unknown");
        hikariConfig.setPoolName(systemName + "-" + poolNameHost + "-pool");

        // 根据数据库类型进行精细化配置
        switch (dbType.toLowerCase()) {
            case "oracle":
                hikariConfig.setConnectionTestQuery("SELECT 1 FROM DUAL");
                break;
            case "mysql":
                hikariConfig.setConnectionTestQuery("SELECT 1");
                hikariConfig.addDataSourceProperty("useUnicode", "true");
                hikariConfig.addDataSourceProperty("characterEncoding", "UTF-8");
                hikariConfig.addDataSourceProperty("serverTimezone", "Asia/Shanghai");
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                break;
            case "postgresql":
                hikariConfig.setConnectionTestQuery("SELECT 1");
                break;
            case "dm":
                hikariConfig.setConnectionTestQuery("SELECT 1 FROM DUAL");
                break;
            case "h2":
                hikariConfig.setConnectionTestQuery("SELECT 1");
                break;
            default:
                // 默认通用配置
                hikariConfig.setConnectionTestQuery("SELECT 1");
        }

        // 从配置对象加载连接池参数，如果未配置则使用HikariCP的默认值
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
        hikariConfig.setLeakDetectionThreshold(60000); // 1分钟泄漏检测

        String maskedHost = node.getHost() != null ? com.dbcli.util.DataMaskUtil.maskIpAddress(node.getHost()) : "unknown";
        Integer nodePort = node.getPort();
        Integer configPort = config.getPort();
        int displayPort = nodePort != null ? nodePort.intValue() : (configPort != null ? configPort.intValue() : 0);
        
        logger.info("创建数据源: {} -> type={}, host={}, port={}, svc={}, sid={}, role={}",
                systemName,
                dbType,
                maskedHost,
                displayPort,
                node.getSvcName(),
                node.getSidName(),
                node.getRole());

        return new HikariDataSource(hikariConfig);
    }

    /**
     * 构建JDBC URL
     * - Oracle: 优先 svc_name 使用 EZConnect //host:port/service；否则使用 SID 形式 :SID
     */
    private String buildJdbcUrl(String dbType, DatabaseNode node, DatabaseConfig config) {
        String host = node.getHost();
        Integer nodePort = node.getPort();
        Integer configPort = config.getPort();
        int port = nodePort != null ? nodePort.intValue() : (configPort != null ? configPort.intValue() : 0);
        if (port <= 0) {
            throw new IllegalArgumentException("无效端口: " + port);
        }
        String svcName = node.getSvcName();
        String sidName = node.getSidName();

        switch (dbType.toLowerCase()) {
            case "oracle":
                if (svcName != null && !svcName.trim().isEmpty()) {
                    // Service Name
                    return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, svcName);
                } else if (sidName != null && !sidName.trim().isEmpty()) {
                    // SID
                    return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sidName);
                } else {
                    // 兜底：仍按 EZConnect，svcName 可能来自系统级配置或为空
                    logger.warn("Oracle 节点未提供 svc_name/sid_name，使用 EZConnect 且服务名为空: {}:{}", host, port);
                    return String.format("jdbc:oracle:thin:@//%s:%d/", host, port);
                }
            case "mysql":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true&serverTimezone=Asia/Shanghai&connectTimeout=10000&socketTimeout=15000&tcpKeepAlive=true",
                        host, port, svcName);
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/%s?connectTimeout=10&socketTimeout=15&tcpKeepAlive=true", host, port, svcName);
            case "dm":
                return String.format("jdbc:dm://%s:%d", host, port);
            case "h2":
                return String.format("jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", svcName);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
    }

    /**
     * 构建连接字符串（测试用公共方法）
     */
    public String buildConnectionString(String dbType, DatabaseConfig config, DatabaseNode node) {
        String host = node.getHost();
        Integer nodePort = node.getPort();
        Integer configPort = config.getPort();
        int port = nodePort != null ? nodePort.intValue() : (configPort != null ? configPort.intValue() : 0);
        if (port <= 0) {
            throw new IllegalArgumentException("无效端口: " + port);
        }
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
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true&serverTimezone=Asia/Shanghai",
                        host, port, svcName);
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, svcName);
            case "dm":
                return String.format("jdbc:dm://%s:%d", host, port);
            case "h2":
                return String.format("jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", svcName);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
    }

    /**
     * 获取连接测试查询
     */
    private String getTestQuery(String dbType) {
        switch (dbType.toLowerCase()) {
            case "oracle":
            case "dm":
                return "SELECT 1 FROM DUAL";
            case "mysql":
            case "postgresql":
                return "SELECT 1";
            default:
                return "SELECT 1";
        }
    }

    /**
     * 构建连接键
     */
    private String buildConnectionKey(String systemName, DatabaseNode node) {
        return systemName + "-" + node.getHost() + "-" + (node.getSvcName() != null ? node.getSvcName() : node.getSidName());
    }

    /**
     * 测试连接
     */
    public boolean testConnection(String systemName, DatabaseNode node, DatabaseConfig config, String dbType) {
        // 测试场景下采用直连，确保3秒内返回结果，避免被连接池配置牵制
        try {
            String jdbcUrl = buildJdbcUrl(dbType, node, config);
            Properties props = new Properties();
            props.setProperty("user", config.getUsername() != null ? config.getUsername() : "");
            props.setProperty("password", config.getPassword() != null ? config.getPassword() : "");
            // 尝试传递超时（不同驱动支持差异，仍以 DriverManager 登录超时为主）
            props.setProperty("loginTimeout", "3");
            // 设置全局登录超时（秒）
            DriverManager.setLoginTimeout(3);

            try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
                return conn.isValid(3);
            }
        } catch (Exception e) {
            logger.debug("连接测试失败: {}-{} - {}", systemName, node != null ? node.getHost() : "unknown", e.getMessage());
            return false;
        }
    }

    /**
     * 关闭所有数据源
     */
    public void closeAll() {
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            try {
                entry.getValue().close();
                logger.info("关闭数据源: {}", entry.getKey());
            } catch (Exception e) {
                logger.error("关闭数据源失败: {}", entry.getKey(), e);
            }
        }
        dataSources.clear();
    }

    /**
     * 获取数据源统计信息
     */
    public void printDataSourceStats() {
        logger.info("当前活跃数据源数量: {}", dataSources.size());
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            HikariDataSource ds = entry.getValue();
            logger.info("数据源 {}: 活跃连接={}, 空闲连接={}, 总连接={}",
                    entry.getKey(),
                    ds.getHikariPoolMXBean().getActiveConnections(),
                    ds.getHikariPoolMXBean().getIdleConnections(),
                    ds.getHikariPoolMXBean().getTotalConnections());
        }
    }
}