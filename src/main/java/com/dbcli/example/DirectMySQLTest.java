package com.dbcli.example;

import com.dbcli.util.EncryptionUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 直接MySQL连接测试 - 强制加载驱动
 */
public class DirectMySQLTest {
    
    public static void main(String[] args) {
        System.out.println("=== 直接MySQL连接测试 ===");
        
        // 解密配置信息
        String host = EncryptionUtil.decrypt("ENC(zdOPy9lLWVrR3PCSrwbwB0bV5QPDDan9NPJjW7j22z4=)");
        String username = EncryptionUtil.decrypt("ENC(wsA7HXDKzTSuZ3kG6f6eIjLhfJeGKOhF4YJT8vRJu9U=)");
        String password = EncryptionUtil.decrypt("ENC(Lb6jBl8uNlD7h7Y0DWzxxc8syMph8XWVoaadcxnc0v4=)");
        
        System.out.println("目标服务器: " + host + ":3307");
        System.out.println("用户名: " + username);
        System.out.println("密码: " + password.replaceAll(".", "*"));
        
        // 尝试多种连接方式
        testWithForceDriverLoad(host, username, password);
        testWithDifferentUrls(host, username, password);
        testBasicConnectivity(host);
    }
    
    private static void testWithForceDriverLoad(String host, String username, String password) {
        System.out.println("\n1. 强制加载MySQL驱动测试");
        
        try {
            // 强制加载MySQL驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("✅ MySQL驱动加载成功");
            
            String url = "jdbc:mysql://" + host + ":3307/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=10000&socketTimeout=10000";
            System.out.println("连接URL: " + url);
            
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✅ MySQL连接成功！");
            
            // 测试查询
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT VERSION() as version, NOW() as current_time");
            if (rs.next()) {
                System.out.println("   MySQL版本: " + rs.getString("version"));
                System.out.println("   服务器时间: " + rs.getString("current_time"));
            }
            rs.close();
            stmt.close();
            conn.close();
            
        } catch (ClassNotFoundException e) {
            System.out.println("❌ MySQL驱动未找到: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("❌ MySQL连接失败: " + e.getMessage());
            System.out.println("   错误代码: " + e.getErrorCode());
            System.out.println("   SQL状态: " + e.getSQLState());
            
            // 详细错误分析
            analyzeConnectionError(e);
        }
    }
    
    private static void testWithDifferentUrls(String host, String username, String password) {
        System.out.println("\n2. 尝试不同的连接URL");
        
        String[] urls = {
            "jdbc:mysql://" + host + ":3307/mysql?useSSL=false&allowPublicKeyRetrieval=true",
            "jdbc:mysql://" + host + ":3307/information_schema?useSSL=false&allowPublicKeyRetrieval=true",
            "jdbc:mysql://" + host + ":3307/?useSSL=false&allowPublicKeyRetrieval=true",
            "jdbc:mysql://" + host + ":3307/mysql?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        };
        
        for (int i = 0; i < urls.length; i++) {
            System.out.println("尝试URL " + (i+1) + ": " + urls[i]);
            try {
                Connection conn = DriverManager.getConnection(urls[i], username, password);
                System.out.println("✅ 连接成功！");
                conn.close();
                return; // 找到可用连接就退出
            } catch (SQLException e) {
                System.out.println("❌ 连接失败: " + e.getMessage());
            }
        }
    }
    
    private static void testBasicConnectivity(String host) {
        System.out.println("\n3. 基础网络连通性测试");
        
        try {
            // 尝试创建Socket连接测试端口
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, 3307), 5000);
            System.out.println("✅ 网络连接正常 - " + host + ":3307 端口可达");
            socket.close();
        } catch (Exception e) {
            System.out.println("❌ 网络连接失败: " + e.getMessage());
            System.out.println("   建议检查:");
            System.out.println("   1. 服务器是否启动");
            System.out.println("   2. 端口3307是否开放");
            System.out.println("   3. 防火墙设置");
            System.out.println("   4. 网络连通性");
        }
    }
    
    private static void analyzeConnectionError(SQLException e) {
        String message = e.getMessage().toLowerCase();
        
        if (message.contains("connection refused")) {
            System.out.println("   💡 分析: 连接被拒绝 - MySQL服务可能未启动或端口未开放");
        } else if (message.contains("access denied")) {
            System.out.println("   💡 分析: 访问被拒绝 - 用户名或密码错误，或用户权限不足");
        } else if (message.contains("unknown database")) {
            System.out.println("   💡 分析: 数据库不存在 - 尝试连接到information_schema");
        } else if (message.contains("timeout")) {
            System.out.println("   💡 分析: 连接超时 - 网络问题或服务器响应慢");
        } else if (message.contains("communications link failure")) {
            System.out.println("   💡 分析: 通信链路失败 - 网络中断或服务器关闭");
        } else {
            System.out.println("   💡 分析: 其他连接问题，请检查服务器状态和网络配置");
        }
    }
}