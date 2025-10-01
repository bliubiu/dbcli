package com.dbcli;

import com.dbcli.cli.CommandLineProcessor;
import com.dbcli.config.AppConfig;
import com.dbcli.core.DbCliRunner;
import com.dbcli.service.TemplateService;
import com.dbcli.util.LogConfigManager;
import com.dbcli.util.LogManager;
import com.dbcli.web.EnhancedWebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 多数据库指标收集工具主入口
 * 
 * 支持功能：
 * - 多数据库连接：Oracle、MySQL、PostgreSQL、达梦
 * - 并行SQL查询执行和指标收集
 * - Excel/HTML格式报告生成
 * - SM4加密存储敏感信息
 * - 数据脱敏处理
 * - 连接测试和管理
 * - 配置模板生成
 * - 分级日志记录
 */
public class DbCliApplication {
    private static final Logger logger = LoggerFactory.getLogger(DbCliApplication.class);
    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        boolean success = false;
        
        try {
            // 提前创建日志目录并刷新logback配置，确保文件appender可用
            try {
                com.dbcli.util.LogConfigManager.createLogDirectories("logs");
                com.dbcli.util.LogConfigManager.refresh();
            } catch (Exception ignore) {}
            
            // 记录应用启动
            LogManager.logApplicationStart(VERSION, args);
            
            // 解析命令行参数
            CommandLineProcessor processor = new CommandLineProcessor();
            AppConfig config = processor.parseArgs(args);
            
            if (config == null) {
                // 帮助信息已显示或参数错误
                LogManager.logApplicationEnd(false);
                return;
            }
            
            // 配置日志系统
            configureLogging(config);
            
            // 处理特殊命令
            if (handleSpecialCommands(config)) {
                LogManager.logApplicationEnd(true);
                return;
            }
            
            // 验证必要参数
            if (!validateRequiredParameters(config)) {
                LogManager.logApplicationEnd(false);
                if (!isTestEnvironment()) {
                    System.exit(1);
                }
                return;
            }
            
            // 创建日志目录
            LogConfigManager.createLogDirectories("logs");
            
            // 运行主程序
            DbCliRunner runner = new DbCliRunner(config);
            success = runner.run();
            
            if (success) {
                logger.info("dbcli 执行成功完成");
            } else {
                logger.error("dbcli 执行过程中出现错误");
                if (!isTestEnvironment()) {
                    System.exit(1);
                }
            }
            
        } catch (Exception e) {
            logger.error("dbcli 执行失败: {}", e.getMessage(), e);
            success = false;
            if (!isTestEnvironment()) {
                System.exit(1);
            }
        } finally {
            // 记录应用结束和清理资源
            LogManager.logApplicationEnd(success);
            LogManager.cleanup();
        }
    }
    
    /**
     * 配置日志系统
     */
    private static void configureLogging(AppConfig config) {
        try {
            // 根据命令行参数配置日志
            LogConfigManager.configureLogging(
                config.getLogLevel(),
                config.getLogFile(),
                config.isVerbose(),
                config.isQuiet()
            );
            
            logger.info("日志系统配置完成");
            
        } catch (Exception e) {
            logger.warn("配置日志系统失败: {}", e.getMessage());
        }
    }
    
    /**
     * 处理特殊命令（模板生成、帮助等）
     */
    private static boolean handleSpecialCommands(AppConfig config) {
        try {
            // 处理模板生成命令
            if (config.isGenerateTemplate()) {
                logger.info("开始生成配置模板");
                
                TemplateService templateService = new TemplateService();
                
                if (config.isInteractiveTemplate()) {
                    // 交互式模板生成
                    templateService.generateInteractiveTemplates(
                        config.getConfigPath() != null ? config.getConfigPath() : "configs",
                        config.getMetricsPath() != null ? config.getMetricsPath() : "metrics"
                    );
                } else {
                    // 标准模板生成
                    templateService.generateTemplates(
                        config.getConfigPath() != null ? config.getConfigPath() : "configs",
                        config.getMetricsPath() != null ? config.getMetricsPath() : "metrics"
                    );
                }
                
                logger.info("配置模板生成完成");
                return true;
            }
            
            // 处理连接测试命令
            if (config.isTestConnection()) {
                logger.info("仅执行连接测试，不收集指标");
                // 这里不直接返回true，让主程序处理连接测试
                return false;
            }
            
            // 处理加密命令
            if (config.isEncryptConfig()) {
                logger.info("配置文件加密功能将在主程序中处理");
                return false;
            }
            
            // 处理Web管理模式
            if (config.isWebManagement()) {
                logger.info("启动Web管理界面");
                startWebManagement(config);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("处理特殊命令失败: {}", e.getMessage(), e);
            return true; // 返回true表示已处理，但失败
        }
        
        return false;
    }
    
    /**
     * 验证必要参数
     */
    private static boolean validateRequiredParameters(AppConfig config) {
        boolean valid = true;
        
        // 如果不是特殊命令，需要验证配置文件路径
        if (!config.isGenerateTemplate() && !config.isShowHelp()) {
            if (config.getConfigPath() == null || config.getConfigPath().trim().isEmpty()) {
                logger.error("缺少必要参数：配置文件路径 (-c)");
                logger.error("使用 -h 查看帮助信息");
                valid = false;
            }

            // 仅在非测试模式下要求提供指标路径
            if (!config.isTestConnection()) {
                if (config.getMetricsPath() == null || config.getMetricsPath().trim().isEmpty()) {
                    logger.error("缺少必要参数：指标文件路径 (-m)");
                    logger.error("使用 -h 查看帮助信息");
                    valid = false;
                }
            }
        }
        
        // 验证输出格式
        if (config.getOutputFormat() != null) {
            String format = config.getOutputFormat().toLowerCase();
            if (!format.equals("excel") && !format.equals("html") && !format.equals("both")) {
                logger.error("不支持的输出格式: {}，支持的格式: excel, html, both", config.getOutputFormat());
                valid = false;
            }
        }
        
        // 验证并发数
        if (config.getConcurrency() <= 0) {
            logger.warn("并发数设置无效: {}，使用默认值: 10", config.getConcurrency());
            config.setConcurrency(10);
        }
        
        return valid;
    }
    
    /**
     * 显示版本信息
     */
    public static void showVersion() {
        logger.info("dbcli - 数据库指标收集工具");
        logger.info("版本: {}", VERSION);
        logger.info("支持数据库: Oracle, MySQL, PostgreSQL, 达梦");
        logger.info("功能特性: 并行查询, Excel/HTML报告, SM4加密, 数据脱敏");
        logger.info("");
    }
    
    /**
     * 显示使用示例
     */
    public static void showExamples() {
        logger.info("使用示例:");
        logger.info("");
        logger.info("1. 生成配置模板:");
        logger.info("   java -jar dbcli.jar --template");
        logger.info("");
        logger.info("2. 交互式生成配置模板:");
        logger.info("   java -jar dbcli.jar --template --interactive");
        logger.info("");
        logger.info("3. 执行指标收集并生成Excel报告:");
        logger.info("   java -jar dbcli.jar -c configs -m metrics -o reports -f excel");
        logger.info("");
        logger.info("4. 执行指标收集并生成HTML报告:");
        logger.info("   java -jar dbcli.jar -c configs -m metrics -o reports -f html");
        logger.info("");
        logger.info("5. 仅测试数据库连接:");
        logger.info("   java -jar dbcli.jar -c configs --test-connection");
        logger.info("");
        logger.info("6. 加密配置文件:");
        logger.info("   java -jar dbcli.jar -c configs --encrypt");
        logger.info("");
        logger.info("7. 详细日志输出:");
        logger.info("   java -jar dbcli.jar -c configs -m metrics -o reports --verbose");
        logger.info("");
        logger.info("8. 指定并发数和日志级别:");
        logger.info("   java -jar dbcli.jar -c configs -m metrics -o reports --concurrency 20 --log-level DEBUG");
        logger.info("");
    }
    
    /**
     * 获取版本号
     */
    public static String getVersion() {
        return VERSION;
    }
    
    /**
     * 启动Web管理界面
     */
    private static void startWebManagement(AppConfig config) {
        try {
            logger.info("正在启动Web管理服务器...");
            
            EnhancedWebServer webServer = new EnhancedWebServer(config);
            webServer.start();
            
            logger.info("Web管理界面已启动");
            logger.info("访问地址: http://localhost:{}", webServer.getPort());
            logger.info("功能包括: 实时监控、报告生成、配置管理、数据库状态");
            logger.info("按 Ctrl+C 停止服务器");
            
            // 保持服务器运行
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("正在关闭Web管理服务器...");
                webServer.stop();
                logger.info("Web管理服务器已关闭");
            }));
            
            // 等待服务器运行
            webServer.waitForShutdown();
            
        } catch (Exception e) {
            logger.error("启动Web管理界面失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否在测试环境中运行
     */
    private static boolean isTestEnvironment() {
        // 检查是否在JUnit测试环境中
        try {
            Class.forName("org.junit.Test");
            // 检查调用栈中是否有JUnit相关的类
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                if (className.contains("junit") || className.contains("surefire") || 
                    className.contains("Test") || className.contains("test")) {
                    return true;
                }
            }
        } catch (ClassNotFoundException e) {
            // JUnit不在classpath中，不是测试环境
        }
        return false;
    }
}