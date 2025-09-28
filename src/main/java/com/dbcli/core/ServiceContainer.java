package com.dbcli.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 轻量级服务容器
 * 提供依赖注入和服务生命周期管理
 */
public class ServiceContainer {
    private static final Logger logger = LoggerFactory.getLogger(ServiceContainer.class);
    
    private final Map<Class<?>, Object> singletonServices = new ConcurrentHashMap<>();
    private final Map<Class<?>, Supplier<?>> serviceFactories = new ConcurrentHashMap<>();
    private final Map<String, Object> namedServices = new ConcurrentHashMap<>();
    
    /**
     * 注册单例服务
     */
    public <T> void registerSingleton(Class<T> type, T instance) {
        if (instance == null) {
            throw new IllegalArgumentException("服务实例不能为空");
        }
        singletonServices.put(type, instance);
        logger.debug("注册单例服务: {}", type.getSimpleName());
    }
    
    /**
     * 注册服务工厂
     */
    public <T> void registerFactory(Class<T> type, Supplier<T> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("服务工厂不能为空");
        }
        serviceFactories.put(type, factory);
        logger.debug("注册服务工厂: {}", type.getSimpleName());
    }
    
    /**
     * 注册命名服务
     */
    public <T> void registerNamed(String name, T instance) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }
        if (instance == null) {
            throw new IllegalArgumentException("服务实例不能为空");
        }
        namedServices.put(name, instance);
        logger.debug("注册命名服务: {} -> {}", name, instance.getClass().getSimpleName());
    }
    
    /**
     * 获取服务实例（别名方法）
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        return (T) singletonServices.get(serviceClass);
    }
    
    /**
     * 获取或创建服务实例
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(Class<T> type, Supplier<T> factory) {
        if (type == null) {
            throw new IllegalArgumentException("服务类型不能为空");
        }
        
        // 首先查找已存在的单例服务
        T instance = (T) singletonServices.get(type);
        if (instance != null) {
            return instance;
        }
        
        // 查找已注册的服务工厂
        Supplier<T> registeredFactory = (Supplier<T>) serviceFactories.get(type);
        if (registeredFactory != null) {
            instance = registeredFactory.get();
            if (instance != null) {
                singletonServices.put(type, instance);
                return instance;
            }
        }
        
        // 使用提供的工厂创建实例
        if (factory != null) {
            instance = factory.get();
            if (instance != null) {
                singletonServices.put(type, instance);
                logger.debug("创建并缓存服务: {}", type.getSimpleName());
                return instance;
            }
        }
        
        throw new IllegalStateException("无法创建服务: " + type.getName());
    }
    
    /**
     * 获取服务实例
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("服务类型不能为空");
        }
        
        // 首先查找单例服务
        T instance = (T) singletonServices.get(type);
        if (instance != null) {
            return instance;
        }
        
        // 查找服务工厂
        Supplier<T> factory = (Supplier<T>) serviceFactories.get(type);
        if (factory != null) {
            instance = factory.get();
            if (instance != null) {
                // 将工厂创建的实例缓存为单例
                singletonServices.put(type, instance);
                return instance;
            }
        }
        
        throw new IllegalStateException("未找到服务: " + type.getName());
    }
    
    /**
     * 获取命名服务
     */
    @SuppressWarnings("unchecked")
    public <T> T getNamed(String name, Class<T> type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }
        
        Object instance = namedServices.get(name);
        if (instance == null) {
            throw new IllegalStateException("未找到命名服务: " + name);
        }
        
        if (!type.isInstance(instance)) {
            throw new IllegalStateException(String.format("服务类型不匹配: 期望 %s, 实际 %s", 
                type.getName(), instance.getClass().getName()));
        }
        
        return (T) instance;
    }
    
    /**
     * 检查是否包含服务
     */
    public boolean contains(Class<?> type) {
        return singletonServices.containsKey(type) || serviceFactories.containsKey(type);
    }
    
    /**
     * 检查是否包含命名服务
     */
    public boolean containsNamed(String name) {
        return namedServices.containsKey(name);
    }
    
    /**
     * 检查是否包含指定名称的服务（兼容测试代码）
     */
    public boolean hasService(String serviceName) {
        return namedServices.containsKey(serviceName);
    }
    
    /**
     * 获取所有已注册的服务类型
     */
    public java.util.Set<Class<?>> getRegisteredTypes() {
        java.util.Set<Class<?>> types = new java.util.HashSet<>();
        types.addAll(singletonServices.keySet());
        types.addAll(serviceFactories.keySet());
        return types;
    }
    
    /**
     * 获取所有命名服务名称
     */
    public java.util.Set<String> getNamedServiceNames() {
        return new java.util.HashSet<>(namedServices.keySet());
    }
    
    /**
     * 关闭服务容器
     */
    public void shutdown() {
        clear();
    }
    
    /**
     * 清理所有服务
     */
    public void clear() {
        // 尝试关闭实现了AutoCloseable的服务
        singletonServices.values().forEach(this::tryClose);
        namedServices.values().forEach(this::tryClose);
        
        singletonServices.clear();
        serviceFactories.clear();
        namedServices.clear();
        
        logger.info("服务容器已清理");
    }
    
    /**
     * 尝试关闭服务
     */
    private void tryClose(Object service) {
        if (service instanceof AutoCloseable) {
            try {
                ((AutoCloseable) service).close();
                logger.debug("关闭服务: {}", service.getClass().getSimpleName());
            } catch (Exception e) {
                logger.warn("关闭服务失败: {}", service.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 获取服务统计信息
     */
    public ServiceStats getStats() {
        ServiceStats stats = new ServiceStats();
        stats.setSingletonCount(singletonServices.size());
        stats.setFactoryCount(serviceFactories.size());
        stats.setNamedServiceCount(namedServices.size());
        return stats;
    }
    
    /**
     * 服务统计信息
     */
    public static class ServiceStats {
        private int singletonCount;
        private int factoryCount;
        private int namedServiceCount;
        
        public int getSingletonCount() { return singletonCount; }
        public void setSingletonCount(int singletonCount) { this.singletonCount = singletonCount; }
        
        public int getFactoryCount() { return factoryCount; }
        public void setFactoryCount(int factoryCount) { this.factoryCount = factoryCount; }
        
        public int getNamedServiceCount() { return namedServiceCount; }
        public void setNamedServiceCount(int namedServiceCount) { this.namedServiceCount = namedServiceCount; }
        
        public int getTotalCount() {
            return singletonCount + factoryCount + namedServiceCount;
        }
        
        @Override
        public String toString() {
            return String.format("ServiceStats{单例: %d, 工厂: %d, 命名: %d, 总计: %d}", 
                singletonCount, factoryCount, namedServiceCount, getTotalCount());
        }
    }
}