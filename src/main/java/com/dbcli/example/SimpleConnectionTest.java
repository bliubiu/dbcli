package com.dbcli.example;

import com.dbcli.util.EncryptionUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 简单的数据库连接测试
 */
public class SimpleConnectionTest {
    
    public static void main(String[] args) {
        System.out.println("=== 简单数据库连接测试 ===");
        
        // 测试MySQL连接
        testMySQLConnection();
        
        // 测试DM连接
        testDMConnection();
        
        System.out.println("=== 测试完成 ===");
    }
    
    private static void testMySQLConnection() {
        System.out.println("\n1. 测试MySQL连接 (192.168.10.186:3307)");
        
        String host = EncryptionUtil.decrypt("ENC(zdOPy9lLWVrR3PCSrwbwB0bV5QPDDan9NPJjW7j22z4=)");
        String username = EncryptionUtil.decrypt("ENC(wsA7HXDKzTSuZ3kG6f6eIjLhfJeGKOhF4YJT8vRJu9U=)");
        String password = EncryptionUtil.decrypt("ENC(Lb6jBl8uNlD7h7Y0DWzxxc8syMph8XWVoaadcxnc0v4=)");
        
        System.out.println("主机: " + host);
        System.out.println("用户: " + username);
        System.out.println("密码: " + password.replaceAll(".", "*"));
        
        String url = "jdbc:mysql://" + host + ":3307/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        System.out.println("连接URL: " + url);
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✅ MySQL连接成功！");
            conn.close();
        } catch (ClassNotFoundException e) {
            System.out.println("❌ MySQL驱动未找到: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("❌ MySQL连接失败: " + e.getMessage());
            System.out.println("   错误代码: " + e.getErrorCode());
            System.out.println("   SQL状态: " + e.getSQLState());
        }
    }
    
    private static void testDMConnection() {
        System.out.println("\n2. 测试DM连接 (192.168.10.185:5336)");
        
        String host = EncryptionUtil.decrypt("ENC(be9OedNgSzDxZKQVgQ/d5aiz2zPoFJx2r+JZzrlpbV0=)");
        String username = EncryptionUtil.decrypt("ENC(JvoLglZi+T5ImFffdlYHu53A249pLWD5vj+TvoKFRxs=)");
        String password = EncryptionUtil.decrypt("ENC(V/C00CeXQIgM1fqUG8etNPGZXngqgXCEwWVpeRNj6GY=)");
        
        System.out.println("主机: " + host);
        System.out.println("用户: " + username);
        System.out.println("密码: " + password.replaceAll(".", "*"));
        
        String url = "jdbc:dm://" + host + ":5336/dm";
        System.out.println("连接URL: " + url);
        
        try {
            Class.forName("dm.jdbc.driver.DmDriver");
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✅ DM连接成功！");
            conn.close();
        } catch (ClassNotFoundException e) {
            System.out.println("❌ DM驱动未找到: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("❌ DM连接失败: " + e.getMessage());
            System.out.println("   错误代码: " + e.getErrorCode());
            System.out.println("   SQL状态: " + e.getSQLState());
        }
    }
}