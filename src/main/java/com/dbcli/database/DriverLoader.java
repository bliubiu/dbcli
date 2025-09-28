package com.dbcli.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库驱动加载器
 */
public class DriverLoader {
    private static final Logger logger = LoggerFactory.getLogger(DriverLoader.class);
    
    private static final String LIB_DIR = "lib";
    private static boolean driversLoaded = false;
    /**
     * 持久化外部类加载器，避免关闭后懒加载依赖类失败（如 EZConnectResolver）
     */
    private static URLClassLoader externalClassLoader = null;
    
    /**
     * 加载所有数据库驱动
     */
    public static synchronized void loadAllDrivers() {
        if (driversLoaded) {
            return;
        }
        
        logger.info("开始加载数据库驱动...");
        
        // 加载内置驱动（通过Maven依赖）
        loadBuiltinDrivers();
        
        // 加载外部驱动（lib目录）
        loadExternalDrivers();
        
        driversLoaded = true;
        logger.info("数据库驱动加载完成");
    }
    
    /**
     * 加载内置驱动
     */
    private static void loadBuiltinDrivers() {
        String[] builtinDrivers = {
            "com.mysql.cj.jdbc.Driver",           // MySQL
            "org.postgresql.Driver",              // PostgreSQL
            "org.h2.Driver"                       // H2
        };
        
        for (String driverClass : builtinDrivers) {
            try {
                Class.forName(driverClass);
                logger.info("加载内置驱动成功: {}", driverClass);
            } catch (ClassNotFoundException e) {
                logger.warn("加载内置驱动失败: {} - {}", driverClass, e.getMessage());
            }
        }
    }
    
    /**
     * 加载外部驱动
     */
    private static void loadExternalDrivers() {
        File libDir = new File(LIB_DIR);
        if (!libDir.exists() || !libDir.isDirectory()) {
            logger.warn("外部驱动目录不存在: {}", LIB_DIR);
            return;
        }
        
        File[] jarFiles = libDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            logger.info("未找到外部驱动JAR文件");
            return;
        }
        
        List<URL> jarUrls = new ArrayList<>();
        for (File jarFile : jarFiles) {
            try {
                jarUrls.add(jarFile.toURI().toURL());
                logger.info("发现外部驱动JAR: {}", jarFile.getName());
            } catch (Exception e) {
                logger.error("处理JAR文件失败: {}", jarFile.getName(), e);
            }
        }
        
        if (jarUrls.isEmpty()) {
            return;
        }
        
        // 创建类加载器加载外部JAR（保持生命周期，不要关闭）
        try {
            if (externalClassLoader == null) {
                externalClassLoader = new URLClassLoader(
                        jarUrls.toArray(new URL[0]),
                        Thread.currentThread().getContextClassLoader()
                );
                // 确保后续SPI/DriverManager也能通过上下文类加载器解析到外部驱动
                Thread.currentThread().setContextClassLoader(externalClassLoader);
            }
    
            // 尝试加载已知的外部驱动类
            String[] externalDrivers = {
                "oracle.jdbc.OracleDriver",          // Oracle
                "oracle.jdbc.driver.OracleDriver",   // Oracle (旧版本)
                "dm.jdbc.driver.DmDriver"            // 达梦
            };
    
            for (String driverClass : externalDrivers) {
                try {
                    // 优先使用外部类加载器加载
                    Class<?> driverClazz = Class.forName(driverClass, true, externalClassLoader);
                    Driver driver = (Driver) driverClazz.getDeclaredConstructor().newInstance();
                    DriverManager.registerDriver(new DriverWrapper(driver));
                    logger.info("加载外部驱动成功: {}", driverClass);
                } catch (ClassNotFoundException e) {
                    logger.debug("外部驱动类未找到: {}", driverClass);
                } catch (Exception e) {
                    logger.error("加载外部驱动失败: {} - {}", driverClass, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("创建外部驱动类加载器失败", e);
        }
    }
    
    /**
     * 检查驱动是否可用
     */
    public static boolean isDriverAvailable(String dbType) {
        String driverClass = getDriverClassName(dbType);
        if (driverClass == null) {
            return false;
        }
        
        try {
            Class.forName(driverClass);
            return true;
        } catch (ClassNotFoundException e) {
            // 尝试使用外部类加载器探测
            if (externalClassLoader != null) {
                try {
                    Class.forName(driverClass, false, externalClassLoader);
                    return true;
                } catch (ClassNotFoundException ignored) {
                }
            }
            logger.debug("驱动不可用: {} - {}", driverClass, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取驱动类名
     */
    private static String getDriverClassName(String dbType) {
        switch (dbType.toLowerCase()) {
            case "oracle":
                return "oracle.jdbc.OracleDriver";
            case "mysql":
                return "com.mysql.cj.jdbc.Driver";
            case "postgresql":
                return "org.postgresql.Driver";
            case "dm":
                return "dm.jdbc.driver.DmDriver";
            case "h2":
                return "org.h2.Driver";
            default:
                return null;
        }
    }
    
    /**
     * 获取所有可用的数据库类型
     */
    public static List<String> getAvailableDbTypes() {
        List<String> availableTypes = new ArrayList<>();
        String[] allTypes = {"oracle", "mysql", "postgresql", "dm", "h2"};
        
        for (String dbType : allTypes) {
            if (isDriverAvailable(dbType)) {
                availableTypes.add(dbType);
            }
        }
        
        return availableTypes;
    }
    
    /**
     * 打印驱动加载状态
     */
    public static void printDriverStatus() {
        logger.info("=== 数据库驱动状态 ===");
        
        String[] dbTypes = {"oracle", "mysql", "postgresql", "dm", "h2"};
        for (String dbType : dbTypes) {
            boolean available = isDriverAvailable(dbType);
            String status = available ? "可用" : "不可用";
            String driverClass = getDriverClassName(dbType);
            
            logger.info("{}: {} ({})", dbType.toUpperCase(), status, driverClass);
        }
        
        // 列出已注册的驱动
        logger.info("已注册的JDBC驱动:");
        java.util.Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            logger.info("  - {} (版本: {}.{})", 
                       driver.getClass().getName(),
                       driver.getMajorVersion(),
                       driver.getMinorVersion());
        }
    }
    
    /**
     * 驱动包装器，用于外部加载的驱动
     */
    private static class DriverWrapper implements Driver {
        private final Driver driver;
        
        public DriverWrapper(Driver driver) {
            this.driver = driver;
        }
        
        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) 
                throws java.sql.SQLException {
            return driver.connect(url, info);
        }
        
        @Override
        public boolean acceptsURL(String url) throws java.sql.SQLException {
            return driver.acceptsURL(url);
        }
        
        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) 
                throws java.sql.SQLException {
            return driver.getPropertyInfo(url, info);
        }
        
        @Override
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }
        
        @Override
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }
        
        @Override
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return driver.getParentLogger();
        }
    }
}