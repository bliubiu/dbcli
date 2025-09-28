package com.dbcli.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 配置热重载管理器
 * 支持配置文件的实时监控和自动重载
 */
public class HotReloadConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(HotReloadConfigManager.class);
    
    private final WatchService watchService;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<Path, Consumer<Path>> watchedFiles;
    private volatile boolean running = false;
    
    public HotReloadConfigManager() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newScheduledThreadPool(2);
        this.watchedFiles = new ConcurrentHashMap<>();
    }
    
    /**
     * 开始监控配置文件变化
     */
    public void startWatching() {
        if (running) {
            logger.warn("配置热重载已经在运行中");
            return;
        }
        
        running = true;
        executor.submit(this::watchLoop);
        logger.info("配置热重载监控已启动");
    }
    
    /**
     * 停止监控
     */
    public void stopWatching() {
        running = false;
        try {
            watchService.close();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            logger.info("配置热重载监控已停止");
        } catch (Exception e) {
            logger.error("停止配置监控时发生错误", e);
        }
    }
    
    /**
     * 添加要监控的配置文件
     */
    public void watchFile(Path filePath, Consumer<Path> reloadCallback) {
        try {
            Path parentDir = filePath.getParent();
            if (parentDir != null) {
                parentDir.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
                
                watchedFiles.put(filePath, reloadCallback);
                logger.info("开始监控配置文件: {}", filePath);
            }
        } catch (IOException e) {
            logger.error("注册文件监控失败: {}", filePath, e);
        }
    }
    
    /**
     * 监控循环
     */
    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    Path dir = (Path) key.watchable();
                    Path fullPath = dir.resolve(fileName);
                    
                    // 检查是否是我们监控的文件
                    Consumer<Path> callback = watchedFiles.get(fullPath);
                    if (callback != null) {
                        logger.info("检测到配置文件变化: {}", fullPath);
                        
                        // 延迟执行重载，避免文件写入过程中的冲突
                        executor.schedule(() -> {
                            try {
                                callback.accept(fullPath);
                                logger.info("配置文件重载完成: {}", fullPath);
                            } catch (Exception e) {
                                logger.error("重载配置文件失败: {}", fullPath, e);
                            }
                        }, 500, TimeUnit.MILLISECONDS);
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("配置监控循环发生错误", e);
            }
        }
    }
    
    /**
     * 获取监控状态
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 获取监控的文件数量
     */
    public int getWatchedFileCount() {
        return watchedFiles.size();
    }
}