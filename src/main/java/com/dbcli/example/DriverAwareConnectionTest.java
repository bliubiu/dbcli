package com.dbcli.example;

import com.dbcli.database.DriverLoader;
import com.dbcli.util.EncryptionUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 使用项目驱动加载机制的连接测试
 */
public class DriverAwareConnectionTest {
    
    public static void main(String[] args) {
        System.out.println("=== 使用项目驱动加载机制的连接测试 ===");
        
        // 首先加载驱动
        System.out.println("1. 加载数据库驱动...");
        DriverLoader.loadAllDrivers();
        
        // 测试MySQL连接
        testMySQLConnection();
        
        // 测试DM连接
        testDMConnection();
        
        System.out.println("=== 测试完成 ===");
    }
    
    private static void testMySQLConnection() {
        System.out.println("\n2. 测试MySQL连接 (192.168.10.186:3307)");
        
        String host = EncryptionUtil.decrypt("ENC(zdOPy9lLWVrR3PCSrwbwB0bV5QPDDan9NPJjW7j22z4=)");
        String username = EncryptionUtil.decrypt("ENC(wsA7HXDKzTSuZ3kG6f6eIjLhfJeGKOhF4YJT8vRJu9U=)");
        String password = EncryptionUtil.decrypt("ENC(Lb6jBl8uNlD7h7Y0DWzxxc8syMph8XWVoaadcxnc0v4=)");
        
        System.out.println("主机: " + host);
        System.out.println("用户: " + username);
        System.out.println("密码: " + password.replaceAll(".", "*"));
        
        String url = "jdbc:mysql://" + host + ":3307/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=5000";
        System.out.println("连接URL: " + url);
        
        try {
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✅ MySQL连接成功！");
            
            // 测试简单查询
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT VERSION() as version");
            if (rs.next()) {
                System.out.println("   MySQL版本: " + rs.getString("version"));
            }
            rs.close();
            stmt.close();
            conn.close();
            
        } catch (SQLException e) {
            System.out.println("❌ MySQL连接失败: " + e.getMessage());
            System.out.println("   错误代码: " + e.getErrorCode());
            System.out.println("   SQL状态: " + e.getSQLState());
            
            // 分析具体错误
            if (e.getMessage().contains("Connection refused")) {
                System.out.println("   💡 建议: 检查MySQL服务是否启动，端口3307是否开放");
            } else if (e.getMessage().contains("Access denied")) {
                System.out.println("   💡 建议: 检查用户名密码是否正确");
            } else if (e.getMessage().contains("Unknown database")) {
                System.out.println("   💡 建议: 检查数据库名称是否正确");
            }
        }
    }
    
    private static void testDMConnection() {
        System.out.println("\n3. 测试DM连接 (192.168.10.185:5336)");
        
        String host = EncryptionUtil.decrypt("ENC(be9OedNgSzDxZKQVgQ/d5aiz2zPoFJx2r+JZzrlpbV0=)");
        String username = EncryptionUtil.decrypt("ENC(JvoLglZi+T5ImFffdlYHu53A249pLWD5vj+TvoKFRxs=)");
        String password = EncryptionUtil.decrypt("ENC(V/C00CeXQIgM1fqUG8etNPGZXngqgXCEwWVpeRNj6GY=)");
        
        System.out.println("主机: " + host);
        System.out.println("用户: " + username);
        System.out.println("密码: " + password.replaceAll(".", "*"));
        
        String url = "jdbc:dm://" + host + ":5336/dm?connectTimeout=5000&socketTimeout=5000";
        System.out.println("连接URL: " + url);
        
        try {
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✅ DM连接成功！");
            
            // 测试简单查询
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT BANNER FROM V$VERSION WHERE ROWNUM = 1");
            if (rs.next()) {
                System.out.println("   DM版本: " + rs.getString("BANNER"));
            }
            rs.close();
            stmt.close();
            conn.close();
            
        } catch (SQLException e) {
            System.out.println("❌ DM连接失败: " + e.getMessage());
            System.out.println("   错误代码: " + e.getErrorCode());
            System.out.println("   SQL状态: " + e.getSQLState());
            
            // 分析具体错误
            if (e.getMessage().contains("Connection refused") || e.getMessage().contains("connect timed out")) {
                System.out.println("   💡 建议: 检查DM服务是否启动，端口5336是否开放，网络是否可达");
            } else if (e.getMessage().contains("invalid username/password")) {
                System.out.println("   💡 建议: 检查用户名密码是否正确");
            }
        }
    }
}