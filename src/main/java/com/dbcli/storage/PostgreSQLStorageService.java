package com.dbcli.storage;

import com.dbcli.model.MetricResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * PostgreSQL指标数据持久化存储服务
 * 用于将收集到的指标数据存储到PostgreSQL数据库中，以便进行历史数据分析
 */
public class PostgreSQLStorageService {
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLStorageService.class);
    
    private String jdbcUrl;
    private String username;
    private String password;
    private Connection connection;
    
    public PostgreSQLStorageService(String host, int port, String database, String username, String password) {
        this.jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        this.username = username;
        this.password = password;
    }
    
    /**
     * 初始化数据库连接和表结构
     */
    public void initialize() throws SQLException {
        connect();
        createTables();
        logger.info("PostgreSQL存储服务初始化完成");
    }
    
    /**
     * 建立数据库连接
     */
    private void connect() throws SQLException {
        try {
            // 加载PostgreSQL驱动
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("找不到PostgreSQL驱动", e);
        }
        
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("ssl", "false");
        
        connection = DriverManager.getConnection(jdbcUrl, props);
        logger.info("已连接到PostgreSQL数据库: {}", jdbcUrl);
    }
    
    /**
     * 创建必要的表结构
     */
    private void createTables() throws SQLException {
        // 创建指标结果表
        String createMetricsTableSQL = """
            CREATE TABLE IF NOT EXISTS metric_results (
                id SERIAL PRIMARY KEY,
                system_name VARCHAR(255),
                database_name VARCHAR(255),
                node_ip VARCHAR(45),
                metric_name VARCHAR(255),
                metric_description TEXT,
                metric_type VARCHAR(50),
                value TEXT,
                multi_values JSONB,
                execute_time TIMESTAMP,
                collect_time TIMESTAMP,
                success BOOLEAN,
                error_message TEXT,
                db_type VARCHAR(50),
                threshold_level VARCHAR(20),
                unit VARCHAR(50),
                node_role VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
            
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createMetricsTableSQL);
            logger.info("指标结果表创建/验证完成");
        }
        
        // 创建索引以提高查询性能
        String createIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_metric_results_system_db 
            ON metric_results(system_name, database_name, collect_time)
            """;
            
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createIndexSQL);
            logger.info("指标结果表索引创建/验证完成");
        }
    }
    
    /**
     * 保存单个指标结果
     */
    public void saveMetricResult(MetricResult result) throws SQLException {
        String sql = """
            INSERT INTO metric_results (
                system_name, database_name, node_ip, metric_name, metric_description,
                metric_type, value, multi_values, execute_time, collect_time,
                success, error_message, db_type, threshold_level, unit, node_role
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, result.getSystemName());
            pstmt.setString(2, result.getDatabaseName());
            pstmt.setString(3, result.getNodeIp());
            pstmt.setString(4, result.getMetricName());
            pstmt.setString(5, result.getMetricDescription());
            pstmt.setString(6, result.getMetricType());
            pstmt.setString(7, result.getValue() != null ? result.getValue().toString() : null);
            
            // 将multiValues转换为JSON字符串
            if (result.getMultiValues() != null && !result.getMultiValues().isEmpty()) {
                pstmt.setString(8, convertListMapToJson(result.getMultiValues()));
            } else {
                pstmt.setString(8, null);
            }
            
            pstmt.setTimestamp(9, result.getExecuteTime() != null ? Timestamp.valueOf(result.getExecuteTime()) : null);
            pstmt.setTimestamp(10, result.getCollectTime() != null ? Timestamp.valueOf(result.getCollectTime()) : Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setBoolean(11, result.isSuccess());
            pstmt.setString(12, result.getErrorMessage());
            pstmt.setString(13, result.getDbType());
            pstmt.setString(14, result.getThresholdLevel());
            pstmt.setString(15, result.getUnit());
            pstmt.setString(16, result.getNodeRole());
            
            pstmt.executeUpdate();
        }
    }
    
    /**
     * 批量保存指标结果
     */
    public void saveMetricResults(List<MetricResult> results) throws SQLException {
        String sql = """
            INSERT INTO metric_results (
                system_name, database_name, node_ip, metric_name, metric_description,
                metric_type, value, multi_values, execute_time, collect_time,
                success, error_message, db_type, threshold_level, unit, node_role
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            
            for (MetricResult result : results) {
                pstmt.setString(1, result.getSystemName());
                pstmt.setString(2, result.getDatabaseName());
                pstmt.setString(3, result.getNodeIp());
                pstmt.setString(4, result.getMetricName());
                pstmt.setString(5, result.getMetricDescription());
                pstmt.setString(6, result.getMetricType());
                pstmt.setString(7, result.getValue() != null ? result.getValue().toString() : null);
                
                // 将multiValues转换为JSON字符串
                if (result.getMultiValues() != null && !result.getMultiValues().isEmpty()) {
                    pstmt.setString(8, convertListMapToJson(result.getMultiValues()));
                } else {
                    pstmt.setString(8, null);
                }
                
                pstmt.setTimestamp(9, result.getExecuteTime() != null ? Timestamp.valueOf(result.getExecuteTime()) : null);
                pstmt.setTimestamp(10, result.getCollectTime() != null ? Timestamp.valueOf(result.getCollectTime()) : Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setBoolean(11, result.isSuccess());
                pstmt.setString(12, result.getErrorMessage());
                pstmt.setString(13, result.getDbType());
                pstmt.setString(14, result.getThresholdLevel());
                pstmt.setString(15, result.getUnit());
                pstmt.setString(16, result.getNodeRole());
                
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
            
            logger.info("批量保存了 {} 条指标结果", results.size());
        } catch (SQLException e) {
            connection.rollback();
            connection.setAutoCommit(true);
            throw e;
        }
    }
    
    /**
     * 将List<Map<String, Object>>转换为JSON字符串
     */
    private String convertListMapToJson(List<Map<String, Object>> listMap) {
        if (listMap == null || listMap.isEmpty()) {
            return null;
        }
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < listMap.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            
            Map<String, Object> map = listMap.get(i);
            json.append("{");
            
            int entryCount = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entryCount > 0) {
                    json.append(",");
                }
                
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value == null) {
                    json.append("null");
                } else if (value instanceof String) {
                    json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                } else {
                    json.append(value.toString());
                }
                
                entryCount++;
            }
            
            json.append("}");
        }
        json.append("]");
        
        return json.toString();
    }
    
    /**
     * 查询历史指标数据
     */
    public ResultSet queryHistoricalMetrics(String systemName, String databaseName, 
                                          String metricName, LocalDateTime startTime, LocalDateTime endTime) 
                                          throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT * FROM metric_results 
            WHERE collect_time >= ? AND collect_time <= ?
            """);
            
        if (systemName != null && !systemName.isEmpty()) {
            sql.append(" AND system_name = ?");
        }
        
        if (databaseName != null && !databaseName.isEmpty()) {
            sql.append(" AND database_name = ?");
        }
        
        if (metricName != null && !metricName.isEmpty()) {
            sql.append(" AND metric_name = ?");
        }
        
        sql.append(" ORDER BY collect_time DESC");
        
        PreparedStatement pstmt = connection.prepareStatement(sql.toString());
        int paramIndex = 1;
        
        pstmt.setTimestamp(paramIndex++, Timestamp.valueOf(startTime));
        pstmt.setTimestamp(paramIndex++, Timestamp.valueOf(endTime));
        
        if (systemName != null && !systemName.isEmpty()) {
            pstmt.setString(paramIndex++, systemName);
        }
        
        if (databaseName != null && !databaseName.isEmpty()) {
            pstmt.setString(paramIndex++, databaseName);
        }
        
        if (metricName != null && !metricName.isEmpty()) {
            pstmt.setString(paramIndex++, metricName);
        }
        
        return pstmt.executeQuery();
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("PostgreSQL数据库连接已关闭");
            } catch (SQLException e) {
                logger.warn("关闭数据库连接时出错: {}", e.getMessage());
            }
        }
    }
}