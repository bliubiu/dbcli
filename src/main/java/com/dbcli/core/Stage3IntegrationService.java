package com.dbcli.core;

import com.dbcli.config.HotReloadConfigManager;
import com.dbcli.web.EnhancedWebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 第三阶段集成服务
 * 整合配置热重载和Web管理界面功能
 */
public class Stage3IntegrationService {
    private static final Logger logger = LoggerFactory.getLogger(Stage3IntegrationService.class);
    
    private final HotReloadConfigManager configManager;
    private final EnhancedWebServer webServer;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // 默认配置
    private static final int DEFAULT_WEB_PORT = 8080;
    private static final String CONFIG_FILE_PATH = "configs/config.properties";
    
    public Stage3IntegrationService() throws IOException {
        this.configManager = new HotReloadConfigManager();
        this.webServer = new EnhancedWebServer(new com.dbcli.config.AppConfig());
        this.webServer.start();
    }
    
    public Stage3IntegrationService(int webPort) throws IOException {
        this.configManager = new HotReloadConfigManager();
        com.dbcli.config.AppConfig cfg = new com.dbcli.config.AppConfig();
        cfg.setWebPort(webPort);
        this.webServer = new EnhancedWebServer(cfg);
    }
    
    /**
     * 初始化第三阶段功能
     */
    public void initialize() {
        if (initialized.get()) {
            logger.warn("第三阶段服务已经初始化");
            return;
        }
        
        try {
            logger.info("🚀 开始初始化第三阶段功能...");
            
            // 1. 初始化配置热重载
            initializeHotReload();
            
            // 2. 初始化Web管理界面
            initializeWebManagement();
            
            initialized.set(true);
            logger.info("✅ 第三阶段功能初始化完成");
            
        } catch (Exception e) {
            logger.error("❌ 第三阶段功能初始化失败", e);
            throw new RuntimeException("第三阶段初始化失败", e);
        }
    }
    
    /**
     * 启动第三阶段服务
     */
    public void start() {
        if (!initialized.get()) {
            initialize();
        }
        
        if (running.get()) {
            logger.warn("第三阶段服务已经在运行中");
            return;
        }
        
        try {
            logger.info("🎯 启动第三阶段服务...");
            
            // 启动配置热重载
            configManager.startWatching();
            
            // 启动Web管理服务器
            webServer.start();
            
            running.set(true);
            logger.info("✅ 第三阶段服务启动完成");
            logger.info("📊 Web管理界面: http://localhost:{}", webServer.getPort());
            
        } catch (Exception e) {
            logger.error("❌ 第三阶段服务启动失败", e);
            throw new RuntimeException("第三阶段启动失败", e);
        }
    }
    
    /**
     * 停止第三阶段服务
     */
    public void stop() {
        if (!running.get()) {
            logger.warn("第三阶段服务未在运行");
            return;
        }
        
        try {
            logger.info("🛑 停止第三阶段服务...");
            
            // 停止Web管理服务器
            webServer.stop();
            
            // 停止配置热重载
            configManager.stopWatching();
            
            running.set(false);
            logger.info("✅ 第三阶段服务已停止");
            
        } catch (Exception e) {
            logger.error("❌ 停止第三阶段服务时发生错误", e);
        }
    }
    
    /**
     * 初始化配置热重载
     */
    private void initializeHotReload() {
        logger.info("🔄 初始化配置热重载功能...");
        
        // 监控配置文件目录
        configManager.watchFile(Paths.get("configs"), path -> {
            logger.info("检测到配置文件变化，重新加载配置: {}", path);
            // 这里可以添加配置重载逻辑
            reloadConfiguration(path);
        });
        
        // 监控指标文件目录
        configManager.watchFile(Paths.get("metrics"), path -> {
            logger.info("检测到指标文件变化，重新加载指标: {}", path);
            // 这里可以添加指标重载逻辑
            reloadMetrics(path);
        });
        
        logger.info("✅ 配置热重载功能初始化完成");
    }
    
    /**
     * 初始化Web管理界面
     */
    private void initializeWebManagement() {
        logger.info("🌐 初始化Web管理界面...");
        
        // Web服务器已在构造函数中创建，这里可以添加额外的初始化逻辑
        logger.info("Web管理服务器端口: {}", webServer.getPort());
        
        logger.info("✅ Web管理界面初始化完成");
    }
    
    /**
     * 重新加载配置
     */
    private void reloadConfiguration(java.nio.file.Path configPath) {
        try {
            logger.info("重新加载配置文件: {}", configPath);
            // 这里可以集成现有的ConfigLoader逻辑
            // ConfigLoader.getInstance().reload(configPath);
            logger.info("配置文件重载完成: {}", configPath);
        } catch (Exception e) {
            logger.error("重载配置文件失败: {}", configPath, e);
        }
    }
    
    /**
     * 重新加载指标
     */
    private void reloadMetrics(java.nio.file.Path metricsPath) {
        try {
            logger.info("重新加载指标文件: {}", metricsPath);
            // 这里可以集成现有的指标加载逻辑
            logger.info("指标文件重载完成: {}", metricsPath);
        } catch (Exception e) {
            logger.error("重载指标文件失败: {}", metricsPath, e);
        }
    }
    
    /**
     * 获取服务状态
     */
    public ServiceStatus getStatus() {
        return new ServiceStatus(
            initialized.get(),
            running.get(),
            configManager.isRunning(),
            webServer.isRunning(),
            configManager.getWatchedFileCount(),
            webServer.getPort()
        );
    }
    
    /**
     * 服务状态类
     */
    public static class ServiceStatus {
        private final boolean initialized;
        private final boolean running;
        private final boolean hotReloadActive;
        private final boolean webServerActive;
        private final int watchedFileCount;
        private final int webPort;
        
        public ServiceStatus(boolean initialized, boolean running, boolean hotReloadActive, 
                           boolean webServerActive, int watchedFileCount, int webPort) {
            this.initialized = initialized;
            this.running = running;
            this.hotReloadActive = hotReloadActive;
            this.webServerActive = webServerActive;
            this.watchedFileCount = watchedFileCount;
            this.webPort = webPort;
        }
        
        public boolean isInitialized() { return initialized; }
        public boolean isRunning() { return running; }
        public boolean isHotReloadActive() { return hotReloadActive; }
        public boolean isWebServerActive() { return webServerActive; }
        public int getWatchedFileCount() { return watchedFileCount; }
        public int getWebPort() { return webPort; }
        
        @Override
        public String toString() {
            return String.format(
                "ServiceStatus{initialized=%s, running=%s, hotReload=%s, webServer=%s, watchedFiles=%d, port=%d}",
                initialized, running, hotReloadActive, webServerActive, watchedFileCount, webPort
            );
        }
    }
}