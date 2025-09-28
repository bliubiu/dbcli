package com.dbcli.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * 增强的异常处理器
 * 提供精细化的异常分类、重试策略和错误恢复机制
 */
public class EnhancedExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedExceptionHandler.class);
    
    // 异常统计
    private final ConcurrentHashMap<String, AtomicInteger> exceptionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastOccurrenceTime = new ConcurrentHashMap<>();
    
    // 异常分类器
    private final ExceptionClassifier classifier = new ExceptionClassifier();
    
    /**
     * 处理异常并返回处理结果
     */
    public ExceptionHandlingResult handleException(Exception exception, String context) {
        String exceptionKey = generateExceptionKey(exception, context);
        
        // 记录异常统计
        recordException(exceptionKey, exception);
        
        // 分类异常
        ExceptionCategory category = classifier.classify(exception);
        
        // 根据分类决定处理策略
        ExceptionHandlingResult result = createHandlingResult(exception, category, context);
        
        // 记录日志
        logException(exception, category, context, result);
        
        return result;
    }
    
    /**
     * 记录异常统计信息
     */
    private void recordException(String exceptionKey, Exception exception) {
        exceptionCounts.computeIfAbsent(exceptionKey, k -> new AtomicInteger(0)).incrementAndGet();
        lastOccurrenceTime.put(exceptionKey, System.currentTimeMillis());
    }
    
    /**
     * 生成异常键
     */
    private String generateExceptionKey(Exception exception, String context) {
        return context + ":" + exception.getClass().getSimpleName();
    }
    
    /**
     * 创建异常处理结果
     */
    private ExceptionHandlingResult createHandlingResult(Exception exception, 
                                                        ExceptionCategory category, 
                                                        String context) {
        ExceptionHandlingResult.Builder builder = ExceptionHandlingResult.builder()
                .exception(exception)
                .category(category)
                .context(context);
        
        switch (category) {
            case TRANSIENT:
                return builder
                        .shouldRetry(true)
                        .retryDelay(calculateRetryDelay(exception, context))
                        .maxRetries(getMaxRetries(exception))
                        .recoverable(true)
                        .build();
                        
            case TIMEOUT:
                return builder
                        .shouldRetry(true)
                        .retryDelay(5000) // 5秒延迟
                        .maxRetries(3)
                        .recoverable(true)
                        .build();
                        
            case CONNECTION:
                return builder
                        .shouldRetry(true)
                        .retryDelay(10000) // 10秒延迟
                        .maxRetries(5)
                        .recoverable(true)
                        .requiresCircuitBreaker(true)
                        .build();
                        
            case CONFIGURATION:
                return builder
                        .shouldRetry(false)
                        .recoverable(false)
                        .requiresManualIntervention(true)
                        .build();
                        
            case SECURITY:
                return builder
                        .shouldRetry(false)
                        .recoverable(false)
                        .requiresManualIntervention(true)
                        .criticalError(true)
                        .build();
                        
            case RESOURCE:
                return builder
                        .shouldRetry(true)
                        .retryDelay(30000) // 30秒延迟
                        .maxRetries(3)
                        .recoverable(true)
                        .requiresResourceCleanup(true)
                        .build();
                        
            default:
                return builder
                        .shouldRetry(false)
                        .recoverable(false)
                        .build();
        }
    }
    
    /**
     * 计算重试延迟时间
     */
    private long calculateRetryDelay(Exception exception, String context) {
        String exceptionKey = generateExceptionKey(exception, context);
        int count = exceptionCounts.getOrDefault(exceptionKey, new AtomicInteger(0)).get();
        
        // 指数退避策略
        return Math.min(1000 * (long) Math.pow(2, count - 1), 60000); // 最大60秒
    }
    
    /**
     * 获取最大重试次数
     */
    private int getMaxRetries(Exception exception) {
        if (exception instanceof SQLTimeoutException) {
            return 3;
        } else if (exception instanceof SQLTransientException) {
            return 5;
        } else {
            return 3;
        }
    }
    
    /**
     * 记录异常日志
     */
    private void logException(Exception exception, ExceptionCategory category, 
                             String context, ExceptionHandlingResult result) {
        String exceptionKey = generateExceptionKey(exception, context);
        int count = exceptionCounts.get(exceptionKey).get();
        
        if (result.isCriticalError()) {
            logger.error("严重错误 [{}] 在上下文 [{}] 中发生，异常类型: {}, 发生次数: {}", 
                        exception.getMessage(), context, category, count, exception);
        } else if (result.shouldRetry()) {
            logger.warn("可重试异常 [{}] 在上下文 [{}] 中发生，异常类型: {}, 发生次数: {}, 将在{}ms后重试", 
                       exception.getMessage(), context, category, count, result.getRetryDelay());
        } else {
            logger.error("不可恢复异常 [{}] 在上下文 [{}] 中发生，异常类型: {}, 发生次数: {}", 
                        exception.getMessage(), context, category, count, exception);
        }
    }
    
    /**
     * 获取异常统计信息
     */
    public ExceptionStatistics getStatistics() {
        return new ExceptionStatistics(
                new ConcurrentHashMap<>(exceptionCounts),
                new ConcurrentHashMap<>(lastOccurrenceTime)
        );
    }
    
    /**
     * 清理过期的异常统计
     */
    public void cleanupExpiredStatistics(long maxAgeMillis) {
        long currentTime = System.currentTimeMillis();
        
        lastOccurrenceTime.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > maxAgeMillis) {
                exceptionCounts.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 异常分类器
     */
    private static class ExceptionClassifier {
        
        public ExceptionCategory classify(Exception exception) {
            // SQL异常分类
            if (exception instanceof SQLException) {
                return classifySQLException((SQLException) exception);
            }
            
            // 网络异常
            if (isNetworkException(exception)) {
                return ExceptionCategory.CONNECTION;
            }
            
            // 内存异常
            if (exception.getClass().getName().contains("OutOfMemoryError")) {
                return ExceptionCategory.RESOURCE;
            }
            
            // 安全异常
            if (isSecurityException(exception)) {
                return ExceptionCategory.SECURITY;
            }
            
            // 配置异常
            if (isConfigurationException(exception)) {
                return ExceptionCategory.CONFIGURATION;
            }
            
            // 默认为未知异常
            return ExceptionCategory.UNKNOWN;
        }
        
        private ExceptionCategory classifySQLException(SQLException sqlException) {
            String sqlState = sqlException.getSQLState();
            int errorCode = sqlException.getErrorCode();
            
            // 超时异常
            if (sqlException instanceof SQLTimeoutException) {
                return ExceptionCategory.TIMEOUT;
            }
            
            // 临时异常
            if (sqlException instanceof SQLTransientException) {
                return ExceptionCategory.TRANSIENT;
            }
            
            // 连接异常
            if (isConnectionError(sqlState, errorCode)) {
                return ExceptionCategory.CONNECTION;
            }
            
            // 权限异常
            if (isPermissionError(sqlState, errorCode)) {
                return ExceptionCategory.SECURITY;
            }
            
            // 语法错误
            if (isSyntaxError(sqlState, errorCode)) {
                return ExceptionCategory.CONFIGURATION;
            }
            
            return ExceptionCategory.DATABASE;
        }
        
        private boolean isConnectionError(String sqlState, int errorCode) {
            return sqlState != null && (
                    sqlState.startsWith("08") || // Connection exception
                    sqlState.startsWith("S1") || // Communication link failure
                    errorCode == 1042 || // MySQL: Can't get hostname
                    errorCode == 2003 || // MySQL: Can't connect to server
                    errorCode == 17002    // Oracle: IO Error
            );
        }
        
        private boolean isPermissionError(String sqlState, int errorCode) {
            return sqlState != null && (
                    sqlState.startsWith("28") || // Invalid authorization
                    errorCode == 1045 || // MySQL: Access denied
                    errorCode == 1044    // MySQL: Access denied for database
            );
        }
        
        private boolean isSyntaxError(String sqlState, int errorCode) {
            return sqlState != null && (
                    sqlState.startsWith("42") || // Syntax error
                    errorCode == 1064    // MySQL: Syntax error
            );
        }
        
        private boolean isNetworkException(Exception exception) {
            String className = exception.getClass().getName();
            return className.contains("ConnectException") ||
                   className.contains("SocketException") ||
                   className.contains("UnknownHostException") ||
                   className.contains("NoRouteToHostException");
        }
        
        private boolean isSecurityException(Exception exception) {
            String className = exception.getClass().getName();
            return className.contains("SecurityException") ||
                   className.contains("AccessControlException") ||
                   className.contains("AuthenticationException");
        }
        
        private boolean isConfigurationException(Exception exception) {
            String className = exception.getClass().getName();
            String message = exception.getMessage();
            
            return className.contains("ConfigurationException") ||
                   className.contains("IllegalArgumentException") ||
                   (message != null && (
                           message.contains("configuration") ||
                           message.contains("property") ||
                           message.contains("invalid")
                   ));
        }
    }
    
    /**
     * 异常类别枚举
     */
    public enum ExceptionCategory {
        TRANSIENT,      // 临时异常，可重试
        TIMEOUT,        // 超时异常
        CONNECTION,     // 连接异常
        DATABASE,       // 数据库异常
        CONFIGURATION,  // 配置异常
        SECURITY,       // 安全异常
        RESOURCE,       // 资源异常
        UNKNOWN         // 未知异常
    }
    
    /**
     * 异常统计信息
     */
    public static class ExceptionStatistics {
        private final ConcurrentHashMap<String, AtomicInteger> exceptionCounts;
        private final ConcurrentHashMap<String, Long> lastOccurrenceTime;
        
        public ExceptionStatistics(ConcurrentHashMap<String, AtomicInteger> exceptionCounts,
                                 ConcurrentHashMap<String, Long> lastOccurrenceTime) {
            this.exceptionCounts = exceptionCounts;
            this.lastOccurrenceTime = lastOccurrenceTime;
        }
        
        public int getTotalExceptions() {
            return exceptionCounts.values().stream()
                    .mapToInt(AtomicInteger::get)
                    .sum();
        }
        
        public ConcurrentHashMap<String, AtomicInteger> getExceptionCounts() {
            return exceptionCounts;
        }
        
        public ConcurrentHashMap<String, Long> getLastOccurrenceTime() {
            return lastOccurrenceTime;
        }
    }
}