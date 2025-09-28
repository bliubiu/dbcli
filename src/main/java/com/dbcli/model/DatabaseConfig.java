package com.dbcli.model;

import java.util.List;

/**
 * 数据库系统配置模型
 */
public class DatabaseConfig {
    private boolean enable;
    private int port;
    private String username;
    private String password;
    private List<DatabaseNode> nodes;
    private String type;
    private String host;
    private ConnectionPool connectionPool;
    private Integer maxPoolSize;
    private Integer minIdle;
    private Long connectionTimeout;
    private Long idleTimeout;
    private Long maxLifetime;

    // 内部类：连接池配置
    public static class ConnectionPool {
        private int maxActive = 10;
        private int maxIdle = 5;
        private int minIdle = 1;
        private long maxWait = 30000;

        public int getMaxActive() { return maxActive; }
        public void setMaxActive(int maxActive) { this.maxActive = maxActive; }
        public int getMaxIdle() { return maxIdle; }
        public void setMaxIdle(int maxIdle) { this.maxIdle = maxIdle; }
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        public long getMaxWait() { return maxWait; }
        public void setMaxWait(long maxWait) { this.maxWait = maxWait; }
    }

    // Getters and Setters
    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<DatabaseNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<DatabaseNode> nodes) {
        this.nodes = nodes;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    public void setConnectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public Integer getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(Integer minIdle) {
        this.minIdle = minIdle;
    }

    public Long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Long getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(Long maxLifetime) {
        this.maxLifetime = maxLifetime;
    }
}
