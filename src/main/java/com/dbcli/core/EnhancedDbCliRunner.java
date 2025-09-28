package com.dbcli.core;

import com.dbcli.config.ConfigLoader;
import com.dbcli.config.ConfigurationValidator;
import com.dbcli.database.DatabaseManager;
import com.dbcli.executor.EnhancedMetricsExecutor;
import com.dbcli.executor.MetricsExecutor;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import com.dbcli.service.EnhancedReportGeneratorFactory;
import com.dbcli.service.ReportGeneratorInterface;
import com.dbcli.util.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 增强的数据库CLI运行器
 * 集成了所有优化组件和新功能
 */
public class EnhancedDbCliRunner {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedDbCliRunner.class);
    
    private final ServiceContainer serviceContainer;
    private final ConfigurationValidator validator;
    private final EnhancedReportGeneratorFactory reportFactory;
    
    public EnhancedDbCliRunner() {
        this.serviceContainer = new ServiceContainer();
        this.validator = new ConfigurationValidator();
        this.reportFactory = new EnhancedReportGeneratorFactory();
        
        initializeServices();
    }
    
    /**
     * 初始化服务
     */
    private void initializeServices() {
        try {
            // 注册核心服务 - 使用延迟初始化
            // 这些服务将在首次使用时创建
            logger.info("服务将在首次使用时延迟初始化");
            
            logger.info("服务容器初始化完成");
            
        } catch (Exception e) {
            logger.error("服务初始化失败", e);
            throw new RuntimeException("服务初始化失败", e);
        }
    }
    
    /**
     * 运行指标收集
     */
    public boolean run(String configPath, String metricsPath, String outputPath, String format) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("开始执行增强版指标收集");
            logger.info("配置文件: {}", configPath);
            logger.info("指标文件: {}", metricsPath);
            logger.info("输出路径: {}", outputPath);
            logger.info("输出格式: {}", format);
            
            // 验证输入参数
            if (!validateInputs(configPath, metricsPath, outputPath, format)) {
                return false;
            }
            
            // 加载和验证配置
            ConfigLoader configLoader = serviceContainer.getOrCreate(ConfigLoader.class, 
                () -> new ConfigLoader(serviceContainer.getOrCreate(com.dbcli.service.EncryptionService.class, 
                    com.dbcli.service.EncryptionService::new)));
            Map<String, DatabaseConfig> databaseConfigs = configLoader.loadDatabaseConfigs(configPath);
            List<MetricConfig> metricConfigs = configLoader.loadMetricConfigs(metricsPath);
            
            if (!validateConfigurations(databaseConfigs, metricConfigs)) {
                return false;
            }
            
            // 执行指标收集
            MetricsExecutor executor = serviceContainer.getOrCreate(MetricsExecutor.class, () -> {
                DatabaseManager dbManager = serviceContainer.getOrCreate(DatabaseManager.class, DatabaseManager::new);
                return new EnhancedMetricsExecutor(10, dbManager, configLoader);
            });
            List<MetricResult> results = executor.executeMetrics(databaseConfigs, metricConfigs, 300);
            
            if (results.isEmpty()) {
                logger.warn("没有收集到任何指标数据");
                return false;
            }
            
            // 生成报告
            if (!generateReport(results, metricsPath, outputPath, format)) {
                return false;
            }
            
            // 输出执行统计
            printExecutionSummary(results, System.currentTimeMillis() - startTime);
            
            logger.info("指标收集执行完成");
            return true;
            
        } catch (Exception e) {
            logger.error("执行指标收集时发生异常", e);
            return false;
        } finally {
            // 清理资源
            cleanup();
        }
    }
    
    /**
     * 验证输入参数
     */
    private boolean validateInputs(String configPath, String metricsPath, String outputPath, String format) {
        // 验证配置文件
        if (!new File(configPath).exists()) {
            logger.error("配置文件不存在: {}", configPath);
            return false;
        }
        
        // 验证指标文件
        if (!new File(metricsPath).exists()) {
            logger.error("指标文件不存在: {}", metricsPath);
            return false;
        }
        
        // 验证输出目录
        File outputDir = new File(outputPath).getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                logger.error("无法创建输出目录: {}", outputDir.getAbsolutePath());
                return false;
            }
        }
        
        // 验证输出格式
        if (!reportFactory.supportsFormat(format)) {
            logger.error("不支持的输出格式: {}，支持的格式: {}", format, reportFactory.getSupportedFormats());
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证配置
     */
    private boolean validateConfigurations(Map<String, DatabaseConfig> databaseConfigs,
                                         List<MetricConfig> metricConfigs) {
        boolean allValid = true;
        
        // 验证数据库配置
        for (Map.Entry<String, DatabaseConfig> entry : databaseConfigs.entrySet()) {
            ConfigurationValidator.ValidationResult result = 
                validator.validateDatabaseConfig(entry.getValue());
            
            if (result.hasErrors()) {
                logger.error("数据库配置验证失败 [{}]: {}", entry.getKey(), result.getErrors());
                allValid = false;
            }
            
            if (result.hasWarnings()) {
                logger.warn("数据库配置警告 [{}]: {}", entry.getKey(), result.getWarnings());
            }
        }
        
        // 验证指标配置
        for (MetricConfig metricConfig : metricConfigs) {
            boolean isValid = validator.validateMetricConfig(metricConfig);
            
            if (!isValid) {
                logger.error("指标配置验证失败 [{}]", metricConfig.getName());
                allValid = false;
            }
        }
        
        return allValid;
    }
    
    /**
     * 生成报告
     */
    private boolean generateReport(List<MetricResult> results, String metricsPath, 
                                 String outputPath, String format) {
        try {
            ReportGeneratorInterface generator = reportFactory.getBestGenerator(format, results.size());
            
            logger.info("使用报告生成器: {} 生成 {} 条结果", 
                generator.getClass().getSimpleName(), results.size());
            
            generator.generateReport(results, metricsPath, outputPath);
            
            logger.info("报告生成成功: {}", outputPath);
            return true;
            
        } catch (Exception e) {
            logger.error("生成报告失败", e);
            return false;
        }
    }
    
    /**
     * 输出执行摘要
     */
    private void printExecutionSummary(List<MetricResult> results, long totalTime) {
        int total = results.size();
        long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        long failed = total - successful;
        
        logger.info("=== 执行摘要 ===");
        logger.info("总指标数: {}", total);
        String successPct = String.format("%.1f", total > 0 ? successful * 100.0 / total : 0.0);
        logger.info("成功: {} ({}%)", successful, successPct);
        String failPct = String.format("%.1f", total > 0 ? failed * 100.0 / total : 0.0);
        logger.info("失败: {} ({}%)", failed, failPct);
        logger.info("总耗时: {} ms", totalTime);
        logger.info("平均耗时: {} ms/指标", total > 0 ? totalTime / total : 0);
        
        // 输出失败的指标
        if (failed > 0) {
            logger.info("=== 失败指标 ===");
            results.stream()
                .filter(r -> !r.isSuccess())
                .forEach(r -> logger.warn("失败: {} - {} - {}", 
                    r.getSystemName(), r.getMetricName(), r.getErrorMessage()));
        }
        
        // 输出性能统计
        try {
            MetricsExecutor executor = serviceContainer.getOrCreate(MetricsExecutor.class, () -> null);
            if (executor instanceof EnhancedMetricsExecutor) {
                EnhancedMetricsExecutor enhancedExecutor = (EnhancedMetricsExecutor) executor;
                
                logger.info("=== 性能统计 ===");
                logger.info("线程池状态: {}", enhancedExecutor.getThreadPoolStats());
                logger.info("熔断器状态: {}", enhancedExecutor.getCircuitBreakerStates());
            }
        } catch (Exception e) {
            logger.debug("无法获取性能统计信息", e);
        }
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        try {
            // 关闭指标执行器
            try {
                MetricsExecutor executor = serviceContainer.getOrCreate(MetricsExecutor.class, () -> null);
                if (executor != null) {
                    executor.shutdown();
                }
            } catch (Exception e) {
                logger.debug("关闭指标执行器时出错", e);
            }
            
            // 关闭数据库管理器
            try {
                DatabaseManager dbManager = serviceContainer.getOrCreate(DatabaseManager.class, () -> null);
                if (dbManager != null) {
                    dbManager.shutdown();
                }
            } catch (Exception e) {
                logger.debug("关闭数据库管理器时出错", e);
            }
            
            // 关闭服务容器
            serviceContainer.shutdown();
            
            // 关闭日志管理器
            LogManager.shutdown();
            
            logger.info("资源清理完成");
            
        } catch (Exception e) {
            logger.error("资源清理时发生异常", e);
        }
    }
    
    /**
     * 获取服务容器（用于测试）
     */
    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }
    
    /**
     * 获取配置验证器（用于测试）
     */
    public ConfigurationValidator getValidator() {
        return validator;
    }
    
    /**
     * 获取报告工厂（用于测试）
     */
    public EnhancedReportGeneratorFactory getReportFactory() {
        return reportFactory;
    }
}