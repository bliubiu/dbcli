package com.dbcli.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 熔断器实现
 * 防止连续失败导致的系统雪崩
 */
public class CircuitBreaker {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    
    private final String name;
    private final int failureThreshold;
    private final long timeoutMs;
    private final long retryTimeoutMs;
    
    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    
    public enum State {
        CLOSED,    // 正常状态，允许请求通过
        OPEN,      // 熔断状态，拒绝请求
        HALF_OPEN  // 半开状态，允许少量请求测试服务是否恢复
    }
    
    public CircuitBreaker(String name, int failureThreshold, long timeoutMs, long retryTimeoutMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
        this.retryTimeoutMs = retryTimeoutMs;
    }
    
    /**
     * 执行受熔断器保护的操作
     */
    public <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> operation) {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime > retryTimeoutMs) {
                state = State.HALF_OPEN;
                logger.info("熔断器 {} 进入半开状态", name);
            } else {
                return CompletableFuture.failedFuture(
                    new CircuitBreakerOpenException("熔断器 " + name + " 处于开启状态"));
            }
        }
        
        return operation.get()
            .whenComplete((result, throwable) -> {
                if (throwable == null) {
                    onSuccess();
                } else {
                    onFailure();
                }
            });
    }
    
    /**
     * 成功回调
     */
    private void onSuccess() {
        failureCount.set(0);
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            logger.info("熔断器 {} 恢复到关闭状态", name);
        }
    }
    
    /**
     * 失败回调
     */
    private void onFailure() {
        int failures = failureCount.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        
        if (failures >= failureThreshold) {
            state = State.OPEN;
            logger.warn("熔断器 {} 开启，连续失败次数: {}", name, failures);
        }
    }
    
    /**
     * 检查熔断器是否开启
     */
    public boolean isOpen() {
        return state == State.OPEN;
    }
    
    /**
     * 获取当前状态
     */
    public State getState() {
        return state;
    }
    
    /**
     * 获取失败次数
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * 获取熔断器名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 重置熔断器状态
     */
    public void reset() {
        failureCount.set(0);
        state = State.CLOSED;
        logger.info("熔断器 {} 已重置", name);
    }
    
    /**
     * 熔断器开启异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}