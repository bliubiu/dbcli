package com.dbcli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志测试类 - 验证日志配置是否正常工作
 */
public class LogTest {
    private static final Logger logger = LoggerFactory.getLogger(LogTest.class);
    private static final Logger dbLogger = LoggerFactory.getLogger("com.dbcli.database");
    private static final Logger errorLogger = LoggerFactory.getLogger("DB_CONNECTION_ERROR");
    
    public static void main(String[] args) {
        logger.info("开始日志测试...");
        
        // 测试主日志
        logger.info("这是一条INFO级别的主日志");
        logger.warn("这是一条WARN级别的主日志");
        logger.error("这是一条ERROR级别的主日志");
        
        // 测试数据库连接日志
        dbLogger.info("这是一条数据库连接日志");
        dbLogger.warn("这是一条数据库连接警告日志");
        
        // 测试连接错误日志
        errorLogger.info("测试连接失败记录");
        
        // 测试性能日志
        Logger perfLogger = LoggerFactory.getLogger("PERFORMANCE");
        perfLogger.info("性能测试日志 - 操作耗时: 100ms");
        
        logger.info("日志测试完成，请检查logs目录下的日志文件");
        
        // 打印日志文件位置
        logger.info("预期的日志文件:");
        logger.info("- logs/dbcli.log (主日志)");
        logger.info("- logs/dbcli_error.log (错误日志)");
        logger.info("- logs/db_connection.log (数据库连接日志)");
        logger.info("- logs/db_conn.err (连接失败记录)");
        logger.info("- logs/performance.log (性能日志)");
    }
}