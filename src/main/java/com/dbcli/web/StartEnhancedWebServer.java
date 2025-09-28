package com.dbcli.web;

import com.dbcli.config.AppConfig;

public class StartEnhancedWebServer {
    public static void main(String[] args) {
        try {
            // 创建默认的应用配置
            AppConfig config = new AppConfig();
            
            // 创建并启动增强版Web服务器
            EnhancedWebServer server = new EnhancedWebServer(config);
            server.start();
            
            System.out.println("Enhanced Web Server started on port " + server.getPort());
            System.out.println("Access the web interface at: http://localhost:" + server.getPort());
            
            // 保持服务器运行
            server.waitForShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}