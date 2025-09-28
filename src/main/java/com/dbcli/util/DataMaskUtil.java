package com.dbcli.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 数据脱敏工具类
 */
public class DataMaskUtil {
    private static final Logger logger = LoggerFactory.getLogger(DataMaskUtil.class);
    
    // IP地址正则表达式
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    
    /**
     * 脱敏密码 - 用*号替换
     */
    public static String maskPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return password;
        }
        
        // 如果已经是加密格式，不需要脱敏
        if (EncryptionUtil.isEncrypted(password)) {
            return password;
        }
        
        // 保留前1位和后1位，中间用*替换
        if (password.length() <= 2) {
            return "**";
        } else if (password.length() <= 4) {
            return password.charAt(0) + "**" + password.charAt(password.length() - 1);
        } else {
            StringBuilder masked = new StringBuilder();
            masked.append(password.charAt(0));
            for (int i = 1; i < password.length() - 1; i++) {
                masked.append('*');
            }
            masked.append(password.charAt(password.length() - 1));
            return masked.toString();
        }
    }
    
    /**
     * 脱敏IP地址 - 保留后两段
     */
    public static String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return ipAddress;
        }
        
        // 验证是否为有效的IP地址
        if (!IP_PATTERN.matcher(ipAddress.trim()).matches()) {
            return ipAddress;
        }
        
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return ipAddress;
        }
        
        // 保留后两段，前两段用*替换
        return "***.***."+parts[2]+"."+parts[3];
    }
    
    /**
     * 脱敏用户名 - 保留前后各1位
     */
    public static String maskUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return username;
        }
        
        if (username.length() <= 2) {
            return "*".repeat(username.length());
        } else if (username.length() <= 4) {
            return username.charAt(0) + "*".repeat(username.length() - 2) + username.charAt(username.length() - 1);
        } else {
            return username.charAt(0) + "*".repeat(username.length() - 2) + username.charAt(username.length() - 1);
        }
    }
    
    /**
     * 脱敏数据库名称 - 保留前3位
     */
    public static String maskDatabaseName(String dbName) {
        if (dbName == null || dbName.trim().isEmpty()) {
            return dbName;
        }
        
        if (dbName.length() <= 3) {
            return dbName;
        } else {
            return dbName.substring(0, 3) + "*".repeat(dbName.length() - 3);
        }
    }
    
    /**
     * 脱敏主机名 - 保留域名部分
     */
    public static String maskHostname(String hostname) {
        if (hostname == null || hostname.trim().isEmpty()) {
            return hostname;
        }
        
        // 如果是IP地址，使用IP脱敏方法
        if (IP_PATTERN.matcher(hostname.trim()).matches()) {
            return maskIpAddress(hostname);
        }
        
        // 如果是域名，保留域名后缀
        if (hostname.contains(".")) {
            String[] parts = hostname.split("\\.");
            if (parts.length >= 2) {
                StringBuilder masked = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) masked.append(".");
                    masked.append("***");
                }
                masked.append(".").append(parts[parts.length - 1]);
                return masked.toString();
            }
        }
        
        // 普通主机名脱敏
        if (hostname.length() <= 3) {
            return "*".repeat(hostname.length());
        } else {
            return hostname.substring(0, 2) + "*".repeat(hostname.length() - 2);
        }
    }
    
    /**
     * 通用脱敏方法 - 根据字段类型自动选择脱敏策略
     */
    public static String maskSensitiveData(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        
        // 只有敏感字段才进行脱敏
        if (!isSensitiveField(fieldName)) {
            return value;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        
        if (lowerFieldName.contains("password") || lowerFieldName.contains("pwd")) {
            return maskPassword(value);
        } else if (lowerFieldName.contains("host") || lowerFieldName.contains("ip")) {
            return maskHostname(value);
        } else if (lowerFieldName.contains("user") || lowerFieldName.contains("username")) {
            return maskUsername(value);
        } else if (lowerFieldName.equals("database") || lowerFieldName.equals("db")) {
            return maskDatabaseName(value);
        }
        
        return value;
    }
    
    /**
     * 脱敏JDBC连接字符串
     */
    public static String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return jdbcUrl;
        }
        
        try {
            // 脱敏用户名和密码
            String masked = jdbcUrl;
            
            // 脱敏用户名 (user=xxx)
            masked = masked.replaceAll("(?i)(user=)[^&;]+", "$1***");
            
            // 脱敏密码 (password=xxx)
            masked = masked.replaceAll("(?i)(password=)[^&;]+", "$1***");
            
            // 脱敏IP地址
            masked = masked.replaceAll("\\b(?:\\d{1,3}\\.){2}(\\d{1,3}\\.\\d{1,3})\\b", "***.***.$1");
            
            return masked;
        } catch (Exception e) {
            logger.warn("JDBC URL脱敏失败: {}", e.getMessage());
            return "jdbc:***://***:***/***";
        }
    }
    
    /**
     * 检查字段是否需要脱敏
     */
    public static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        return lowerFieldName.contains("password") || 
               lowerFieldName.contains("pwd") ||
               lowerFieldName.contains("host") ||
               lowerFieldName.equals("ip") ||  // 只匹配完全等于"ip"的字段，避免匹配"description"
               lowerFieldName.contains("username") ||
               lowerFieldName.equals("user") ||  // 只匹配完全等于"user"的字段
               lowerFieldName.equals("database") ||  // 只匹配完全等于"database"的字段
               lowerFieldName.equals("db");  // 只匹配完全等于"db"的字段
    }
}
