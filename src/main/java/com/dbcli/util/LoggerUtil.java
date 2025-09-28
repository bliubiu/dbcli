package com.dbcli.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日志工具类
 */
public class LoggerUtil {
    
    public static void initLogger() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // 创建日志目录
        File logDir = new File("log");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        // 配置控制台输出
        configureConsoleAppender(context);
        
        // 配置文件输出
        configureFileAppender(context, Level.INFO);
        configureFileAppender(context, Level.ERROR);
        configureFileAppender(context, Level.DEBUG);
    }
    
    private static void configureConsoleAppender(LoggerContext context) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("console");
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();
        
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();
        
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(consoleAppender);
        rootLogger.setLevel(Level.INFO);
    }
    
    private static void configureFileAppender(LoggerContext context, Level level) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("log/dbcli_%s_%s.log", level.toString(), timestamp);
        
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName("file-" + level.toString().toLowerCase());
        fileAppender.setFile(filename);
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();
        
        fileAppender.setEncoder(encoder);
        fileAppender.start();
        
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(fileAppender);
    }
    
    /**
     * 脱敏处理IP地址
     */
    public static String maskIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return "*.*."+parts[2]+"."+parts[3];
        }
        return ip;
    }
    
    /**
     * 脱敏处理密码
     */
    public static String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        return "****";
    }
}