package com.dbcli.example;

import com.dbcli.database.DriverLoader;
import java.sql.DriverManager;
import java.sql.Driver;

/**
 * 驱动加载测试
 */
public class DriverLoadTest {
    
    public static void main(String[] args) {
        System.out.println("=== 驱动加载测试 ===");
        
        // 1. 测试当前classpath中的驱动
        testCurrentClasspathDrivers();
        
        // 2. 强制加载所有驱动
        System.out.println("\n=== 强制加载所有驱动 ===");
        DriverLoader.loadAllDrivers();
        
        // 3. 打印驱动状态
        System.out.println("\n=== 驱动状态检查 ===");
        DriverLoader.printDriverStatus();
        
        // 4. 测试MySQL驱动可用性
        System.out.println("\n=== MySQL驱动可用性测试 ===");
        boolean mysqlAvailable = DriverLoader.isDriverAvailable("mysql");
        System.out.println("MySQL驱动可用: " + mysqlAvailable);
        
        // 5. 列出所有已注册的驱动
        System.out.println("\n=== 已注册的JDBC驱动 ===");
        java.util.Enumeration<Driver> drivers = DriverManager.getDrivers();
        int count = 0;
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            count++;
            System.out.println(count + ". " + driver.getClass().getName() + 
                             " (版本: " + driver.getMajorVersion() + "." + driver.getMinorVersion() + ")");
        }
        
        if (count == 0) {
            System.out.println("❌ 没有找到任何已注册的JDBC驱动！");
        }
    }
    
    private static void testCurrentClasspathDrivers() {
        System.out.println("测试当前classpath中的驱动:");
        
        String[] drivers = {
            "com.mysql.cj.jdbc.Driver",
            "oracle.jdbc.OracleDriver", 
            "org.postgresql.Driver",
            "dm.jdbc.driver.DmDriver"
        };
        
        for (String driverClass : drivers) {
            try {
                Class.forName(driverClass);
                System.out.println("✅ " + driverClass + " - 可用");
            } catch (ClassNotFoundException e) {
                System.out.println("❌ " + driverClass + " - 不可用");
            }
        }
    }
}