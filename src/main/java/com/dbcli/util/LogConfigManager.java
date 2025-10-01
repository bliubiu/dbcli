package com.dbcli.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

/**
 * 日志配置管理器 - 支持动态调整日志配置
 */
public class LogConfigManager {
    
    private static final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    
    /**
     * 动态设置日志级别
     */
    public static void setLogLevel(String loggerName, String level) {
        Logger logger = loggerContext.getLogger(loggerName);
        Level logLevel = Level.toLevel(level.toUpperCase());
        logger.setLevel(logLevel);
        
        org.slf4j.Logger rootLogger = LoggerFactory.getLogger(LogConfigManager.class);
        rootLogger.info("日志级别已调整 - Logger: {}, Level: {}", loggerName, level);
    }
    
    /**
     * 设置根日志级别
     */
    public static void setRootLogLevel(String level) {
        setLogLevel(Logger.ROOT_LOGGER_NAME, level);
    }
    
    /**
     * 设置应用日志级别
     */
    public static void setAppLogLevel(String level) {
        setLogLevel("com.dbcli", level);
    }
    
    /**
     * 动态添加文件日志输出
     */
    public static void addFileAppender(String appenderName, String logFilePath, String pattern) {
        try {
            // 确保日志目录存在
            File logFile = new File(logFilePath);
            File logDir = logFile.getParentFile();
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 创建文件输出器
            RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
            fileAppender.setContext(loggerContext);
            fileAppender.setName(appenderName);
            fileAppender.setFile(logFilePath);
            
            // 配置滚动策略
            SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
            rollingPolicy.setContext(loggerContext);
            rollingPolicy.setParent(fileAppender);
            rollingPolicy.setFileNamePattern(logFilePath.replace(".log", ".%d{yyyy-MM-dd}.%i.log"));
            rollingPolicy.setMaxFileSize(FileSize.valueOf("100MB"));
            rollingPolicy.setMaxHistory(30);
            rollingPolicy.setTotalSizeCap(FileSize.valueOf("10GB"));
            rollingPolicy.start();
            
            fileAppender.setRollingPolicy(rollingPolicy);
            
            // 配置编码器
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);
            encoder.setPattern(pattern != null ? pattern : "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            encoder.setCharset(java.nio.charset.StandardCharsets.UTF_8);
            encoder.start();
            
            fileAppender.setEncoder(encoder);
            fileAppender.start();
            
            // 添加到根日志记录器
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(fileAppender);
            
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.info("动态添加文件日志输出 - 名称: {}, 路径: {}", appenderName, logFilePath);
            
        } catch (Exception e) {
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.error("添加文件日志输出失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 动态添加控制台日志输出
     */
    public static void addConsoleAppender(String appenderName, String pattern) {
        try {
            ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
            consoleAppender.setContext(loggerContext);
            consoleAppender.setName(appenderName);
            
            // 配置编码器
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);
            encoder.setPattern(pattern != null ? pattern : "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            encoder.setCharset(java.nio.charset.StandardCharsets.UTF_8);
            encoder.start();
            
            consoleAppender.setEncoder(encoder);
            consoleAppender.start();
            
            // 添加到根日志记录器
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(consoleAppender);
            
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.info("动态添加控制台日志输出 - 名称: {}", appenderName);
            
        } catch (Exception e) {
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.error("添加控制台日志输出失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 根据命令行参数配置日志
     */
    public static void configureLogging(String logLevel, String logFile, boolean verbose, boolean quiet) {
        try {
            // 设置日志级别
            if (logLevel != null) {
                setRootLogLevel(logLevel);
                setAppLogLevel(logLevel);
            } else if (verbose) {
                setRootLogLevel("DEBUG");
                setAppLogLevel("DEBUG");
            } else if (quiet) {
                setRootLogLevel("WARN");
                setAppLogLevel("WARN");
            }
            
            // 添加自定义日志文件
            if (logFile != null && !logFile.trim().isEmpty()) {
                String absolutePath = Paths.get(logFile).toAbsolutePath().toString();
                addFileAppender("CUSTOM_FILE", absolutePath, null);
            }
            
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.info("日志配置完成 - 级别: {}, 文件: {}, 详细: {}, 安静: {}", 
                       logLevel, logFile, verbose, quiet);
            
        } catch (Exception e) {
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.error("配置日志失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取当前日志级别
     */
    public static String getCurrentLogLevel(String loggerName) {
        Logger logger = loggerContext.getLogger(loggerName);
        Level level = logger.getLevel();
        return level != null ? level.toString() : "INHERITED";
    }
    
    /**
     * 获取根日志级别
     */
    public static String getRootLogLevel() {
        return getCurrentLogLevel(Logger.ROOT_LOGGER_NAME);
    }
    
    /**
     * 列出所有日志记录器
     */
    public static void listLoggers() {
        org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
        
        logger.info("当前日志记录器配置:");
        logger.info("  根日志级别: {}", getRootLogLevel());
        logger.info("  应用日志级别: {}", getCurrentLogLevel("com.dbcli"));
        logger.info("  数据库日志级别: {}", getCurrentLogLevel("com.dbcli.database"));
        logger.info("  执行器日志级别: {}", getCurrentLogLevel("com.dbcli.executor"));
        logger.info("  服务日志级别: {}", getCurrentLogLevel("com.dbcli.service"));
    }
    
    /**
     * 重置日志配置为默认值
     */
    public static void resetToDefault() {
        setRootLogLevel("INFO");
        setAppLogLevel("DEBUG");
        
        org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
        logger.info("日志配置已重置为默认值");
    }
    
    /**
     * 刷新日志配置
     */
    public static void refresh() {
        try {
            // 查找logback配置文件
            java.net.URL configUrl = LogConfigManager.class.getClassLoader().getResource("logback.xml");
            if (configUrl != null) {
                // 不要重置上下文，而是重新配置
                LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                ch.qos.logback.classic.joran.JoranConfigurator configurator = new ch.qos.logback.classic.joran.JoranConfigurator();
                configurator.setContext(loggerContext);
                configurator.doConfigure(configUrl);
                
                org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
                logger.info("日志配置已刷新");
            } else {
                System.err.println("找不到logback.xml配置文件");
            }
            
        } catch (Exception e) {
            System.err.println("刷新日志配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建专用日志目录结构
     */
    public static void createLogDirectories(String baseLogDir) {
        try {
            String[] logDirs = {
                baseLogDir,
                baseLogDir + "/connection",
                baseLogDir + "/execution", 
                baseLogDir + "/performance",
                baseLogDir + "/error",
                baseLogDir + "/archive"
            };
            
            for (String dir : logDirs) {
                File logDir = new File(dir);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
            }
            
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.info("日志目录结构创建完成: {}", baseLogDir);
            
        } catch (Exception e) {
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogConfigManager.class);
            logger.error("创建日志目录失败: {}", e.getMessage(), e);
        }
    }
}