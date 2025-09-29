package com.dbcli.storage;

/**
 * 存储配置类
 * 用于配置指标数据持久化存储的相关参数
 */
public class StorageConfig {
    private boolean enabled = false;
    private String type = "postgresql"; // 默认使用PostgreSQL
    private String host = "localhost";
    private int port = 5432;
    private String database = "dbcli_metrics";
    private String username = "dbcli_user";
    private String password = "dbcli_password";
    private boolean batchMode = true; // 默认使用批量模式
    private int batchSize = 100; // 批量大小
    
    // Getters and Setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
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
    
    public boolean isBatchMode() {
        return batchMode;
    }
    
    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}