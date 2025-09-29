package com.dbcli.web;

import com.dbcli.config.AppConfig;
import com.dbcli.web.util.ResponseUtil;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 增强版Web管理服务器
 * 包含完整的数据库连接测试、配置加密、报告生成和实时日志功能
 */
public class EnhancedWebServer {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedWebServer.class);
    
    private HttpServer server;
    private final int port;
    private final AppConfig config;
    private volatile boolean running = false;
    private ThreadPoolExecutor executor;
    
    public EnhancedWebServer(AppConfig config) {
        this.port = config.getWebPort() > 0 ? config.getWebPort() : 8080;
        this.config = config;
    }
    
    public void start() throws IOException {
        if (running) {
            logger.warn("Web管理服务器已经在运行中");
            return;
        }
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 统一注册路由
        RouteRegistrar.register(server, config, port);
        
        // 可控线程池，便于优雅停机
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.start();
        
        running = true;
        logger.info("增强版Web管理服务器已启动，访问地址: http://localhost:{}", port);
    }
    
    public void stop() {
        if (server != null && running) {
            // 停止接收新请求，并给正在处理的请求 2 秒时间
            server.stop(2);
            // 优雅关闭线程池
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            running = false;
            logger.info("Web管理服务器已停止");
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getPort() {
        return port;
    }
    
    public void waitForShutdown() {
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    

}