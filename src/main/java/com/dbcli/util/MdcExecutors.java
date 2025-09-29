package com.dbcli.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * MDC 上下文异步传播工具
 * 提供对 Runnable/Callable/Supplier 的包装，确保在异步线程中恢复提交时的 MDC
 * 设计要点：
 * - 不向外抛出受检异常；Callable 场景将异常包装为 RuntimeException
 * - 提供 wrapSupplier 以避免与 Callable 重载产生歧义
 */
public final class MdcExecutors {
    private MdcExecutors() {}

    public static Runnable wrap(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> withContext(contextMap, () -> {
            task.run();
            return null;
        });
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> withContext(contextMap, () -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 兼容旧用法的 Supplier 包装（可能与 Callable 重载歧义，建议使用 wrapSupplier）
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        return wrapSupplier(supplier);
    }

    /**
     * 明确用于 Supplier 的包装，避免与 Callable 重载产生歧义
     */
    public static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> withContext(contextMap, supplier);
    }

    /**
     * 在给定 MDC 上下文中执行 supplier，不抛出受检异常
     */
    private static <T> T withContext(Map<String, String> contextMap, Supplier<T> supplier) {
        Map<String, String> original = MDC.getCopyOfContextMap();
        try {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }
            return supplier.get();
        } finally {
            if (original != null) {
                MDC.setContextMap(original);
            } else {
                MDC.clear();
            }
        }
    }
}