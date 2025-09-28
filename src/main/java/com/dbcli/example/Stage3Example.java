package com.dbcli.example;

import com.dbcli.core.Stage3IntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第三阶段功能演示
 * 展示配置热重载和Web管理界面的使用
 */
public class Stage3Example {
    private static final Logger logger = LoggerFactory.getLogger(Stage3Example.class);
    
    public static void main(String[] args) {
        logger.info("🚀 开始第三阶段功能演示");
        
        try {
            // 创建第三阶段集成服务
            Stage3IntegrationService service = new Stage3IntegrationService(8080);
            
            // 初始化并启动服务
            service.initialize();
            service.start();
            
            // 显示服务状态
            Stage3IntegrationService.ServiceStatus status = service.getStatus();
            logger.info("📊 服务状态: {}", status);
            
            // 演示功能
            demonstrateFeatures(service);
            
            // 保持服务运行
            logger.info("🌐 Web管理界面已启动: http://localhost:{}", status.getWebPort());
            logger.info("🔄 配置热重载已启用，监控文件数: {}", status.getWatchedFileCount());
            logger.info("⏰ 服务将运行60秒进行演示...");
            
            // 运行60秒后停止
            Thread.sleep(60000);
            
            // 停止服务
            service.stop();
            logger.info("✅ 第三阶段功能演示完成");
            
        } catch (Exception e) {
            logger.error("❌ 第三阶段功能演示失败", e);
        }
    }
    
    /**
     * 演示第三阶段功能
     */
    private static void demonstrateFeatures(Stage3IntegrationService service) {
        logger.info("🎯 演示第三阶段核心功能:");
        
        // 1. 配置热重载演示
        logger.info("1️⃣ 配置热重载功能:");
        logger.info("   - 自动监控 configs/ 目录下的配置文件变化");
        logger.info("   - 自动监控 metrics/ 目录下的指标文件变化");
        logger.info("   - 文件变化时自动重新加载，无需重启应用");
        
        // 2. Web管理界面演示
        logger.info("2️⃣ Web管理界面功能:");
        logger.info("   - 实时系统状态监控");
        logger.info("   - 连接统计和性能指标展示");
        logger.info("   - 配置管理和重载操作");
        logger.info("   - 实时日志查看");
        
        // 3. 集成服务演示
        logger.info("3️⃣ 集成服务特性:");
        logger.info("   - 统一的服务生命周期管理");
        logger.info("   - 完整的状态监控和报告");
        logger.info("   - 优雅的启动和停止机制");
        
        // 显示访问信息
        Stage3IntegrationService.ServiceStatus status = service.getStatus();
        logger.info("📱 访问Web管理界面:");
        logger.info("   URL: http://localhost:{}", status.getWebPort());
        logger.info("   功能: 系统监控、配置管理、日志查看");
        
        logger.info("🔧 配置热重载测试:");
        logger.info("   1. 修改 configs/ 目录下的任意配置文件");
        logger.info("   2. 修改 metrics/ 目录下的任意指标文件");
        logger.info("   3. 观察日志输出，确认自动重载功能");
    }
}