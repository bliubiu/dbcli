package com.dbcli.core;

import com.dbcli.config.AppConfig;
import com.dbcli.config.ConfigLoader;
import com.dbcli.database.DatabaseManager;
import com.dbcli.executor.ConcurrentMetricsExecutor;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import com.dbcli.service.EncryptionService;
import com.dbcli.service.FastConnectionTestService;
import com.dbcli.service.ReportGenerator;
import com.dbcli.service.ReportGeneratorFactory;
import com.dbcli.service.TemplateService;
import com.dbcli.storage.MetricsStorageManager;
import com.dbcli.storage.StorageConfig;
import com.dbcli.util.FileUtil;
import com.dbcli.util.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * dbcli核心运行器 - 协调所有功能模块的执行
 */
public class DbCliRunner {
    private static final Logger logger = LoggerFactory.getLogger(DbCliRunner.class);
    
    private final AppConfig config;
    private final TemplateService templateService;
    private final EncryptionService encryptionService;
    private final FastConnectionTestService connectionTestService;
    private final ConfigLoader configLoader;
    private final DatabaseManager databaseManager;
    private final ConcurrentMetricsExecutor metricsExecutor;
    private final ReportGeneratorFactory reportGeneratorFactory;
    private MetricsStorageManager storageManager;

    public DbCliRunner(AppConfig config) {
        this.config = config;
        this.templateService = new TemplateService();
        this.encryptionService = new EncryptionService();
        this.databaseManager = new DatabaseManager();
        this.connectionTestService = new FastConnectionTestService(this.databaseManager.getConnectionFactory(), this.config.getConcurrency());
        this.configLoader = new ConfigLoader(new EncryptionService()); // 传入EncryptionService参数
        this.metricsExecutor = new ConcurrentMetricsExecutor(config.getConcurrency(), 30000L); // 30秒超时
        this.reportGeneratorFactory = new ReportGeneratorFactory();
        this.storageManager = createStorageManager();
    }
    
    /**
     * 创建存储管理器
     */
    private MetricsStorageManager createStorageManager() {
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setEnabled(true);
        storageConfig.setType("postgresql");
        storageConfig.setHost("localhost");
        storageConfig.setPort(5432);
        storageConfig.setDatabase("dbcli_metrics");
        storageConfig.setUsername("dbcli_user");
        storageConfig.setPassword("dbcli_password");
        storageConfig.setBatchMode(true);
        storageConfig.setBatchSize(100);
        
        return new MetricsStorageManager(storageConfig);
    }
    
    /**
     * 主运行方法
     */
    public boolean run() {
        try {
            logger.info("=".repeat(60));
            logger.info("开始执行 dbcli 数据库指标收集工具");
            logger.info("配置路径: {}", config.getConfigPath());
            logger.info("指标路径: {}", config.getMetricsPath());
            logger.info("输出路径: {}", config.getOutputPath());
            logger.info("输出格式: {}", config.getOutputFormat());
            logger.info("并发数: {}", config.getConcurrency());
            logger.info("=".repeat(60));
            
            // 创建必要的目录
            createDirectories();
            
            // 处理特殊命令
            if (handleSpecialCommands()) {
                return true;
            }
            
            // 执行主要流程
            return executeMainWorkflow();
            
        } catch (Exception e) {
            logger.error("DbCliRunner 执行失败: {}", e.getMessage(), e);
            return false;
        } finally {
            // 清理资源
            cleanup();
        }
    }
    
    /**
     * 处理特殊命令
     */
    private boolean handleSpecialCommands() throws Exception {
        // 处理模板生成
        if (config.isGenerateTemplate()) {
            logger.info("生成配置文件模板...");
            LogManager.startTimer("template_generation");
            
            if (config.isInteractiveTemplate()) {
                templateService.generateInteractiveTemplates(config.getConfigPath(), config.getMetricsPath());
            } else {
                templateService.generateTemplates(config.getConfigPath(), config.getMetricsPath());
            }
            
            long duration = LogManager.endTimer("template_generation");
            logger.info("模板文件生成完成，耗时: {}ms", duration);
            return true;
        }
        
        // 处理配置文件加密
        if (config.isEncryptConfig()) {
            logger.info("加密配置文件...");
            LogManager.startTimer("config_encryption");
            
            encryptionService.encryptConfigs(config.getConfigPath());
            
            long duration = LogManager.endTimer("config_encryption");
            logger.info("配置文件加密完成，耗时: {}ms", duration);
            return true;
        }
        
        // 处理连接测试
        if (config.isTestConnection()) {
            LogManager.setOperation("connection_test");
            logger.info("测试数据库连接...");
            if (config.isClean()) {
                cleanConnectionTestFiles();
            }
            LogManager.startTimer("connection_test");
            
            Map<String, DatabaseConfig> dbConfigs = configLoader.loadDatabaseConfigs(config.getConfigPath());
            Map<String, Map<String, DatabaseConfig>> groupedConfigs = convertToTypeSystemMap(dbConfigs);
            connectionTestService.testConnectionsWithNames(groupedConfigs);
            
            long duration = LogManager.endTimer("connection_test");
            LogManager.setOperation("connection_test");
            logger.info("数据库连接测试完成，耗时: {}ms", duration);
            return true;
        }
        
        return false;
    }
    
    /**
     * 执行主要工作流程
     */
    private boolean executeMainWorkflow() throws Exception {
        // 1. 加载配置文件
        logger.info("步骤 1/5: 加载配置文件");
        LogManager.setOperation("load_config");
        Map<String, DatabaseConfig> databaseConfigs = loadDatabaseConfigs();
        if (databaseConfigs.isEmpty()) {
            logger.error("未找到有效的数据库配置");
            return false;
        }
        
        // 2. 加载指标配置
        logger.info("步骤 2/5: 加载指标配置");
        LogManager.setOperation("load_metric");
        List<MetricConfig> metricConfigs = loadMetricConfigs();
        if (metricConfigs.isEmpty()) {
            logger.error("未找到有效的指标配置");
            return false;
        }
        
        // 3. 测试数据库连接（仅在明确要求时执行）
        if (config.isTestConnection()) {
            logger.info("步骤 3/5: 测试数据库连接");
            LogManager.setOperation("connection_test");
            Map<String, Map<String, DatabaseConfig>> groupedConfigs = convertToTypeSystemMap(databaseConfigs);
            connectionTestService.testConnectionsWithNames(groupedConfigs);
            // 注入失败主机集合（SM4确定性加密）用于步骤4跳过
            metricsExecutor.setFailedEncryptedHosts(connectionTestService.getFailedEncryptedHosts());
        } else {
            logger.info("步骤 3/5: 预检查连接（隐式）；失败目标将跳过后续指标收集");
            LogManager.setOperation("connection_precheck");
            Map<String, Map<String, DatabaseConfig>> groupedConfigs = convertToTypeSystemMap(databaseConfigs);
            try {
                connectionTestService.testConnectionsWithNames(groupedConfigs);
            } catch (Exception e) {
                logger.debug("预检查连接异常: {}", e.getMessage());
            }
            // 注入失败主机集合（SM4确定性加密）用于步骤4跳过
            metricsExecutor.setFailedEncryptedHosts(connectionTestService.getFailedEncryptedHosts());
        }
        
        // 4. 执行指标收集
        logger.info("步骤 4/5: 执行指标收集");
        LogManager.setOperation("execute_metrics");
        List<MetricResult> results = executeMetricsCollection(databaseConfigs, metricConfigs);
        if (results.isEmpty()) {
            logger.warn("未收集到任何指标数据");
            return false;
        }
        
        // 5. 生成报告
        logger.info("步骤 5/5: 生成报告");
        LogManager.setOperation("generate_report");
        boolean reportSuccess = generateReports(results);
        
        // 输出执行摘要
        printExecutionSummary(results);
        
        return reportSuccess;
    }
    
    /**
     * 转换Map<String, DatabaseConfig>为Map<String, List<DatabaseConfig>>
     */
    private Map<String, List<DatabaseConfig>> convertToListMap(Map<String, DatabaseConfig> configMap) {
        Map<String, List<DatabaseConfig>> listMap = new HashMap<>();
        for (Map.Entry<String, DatabaseConfig> entry : configMap.entrySet()) {
            DatabaseConfig config = entry.getValue();
            // 跳过未启用的配置
            if (config == null || !config.isEnable()) {
                continue;
            }
            String dbType = config.getType();
            if (dbType == null) {
                dbType = "unknown";
            }
            listMap.computeIfAbsent(dbType, k -> new ArrayList<>()).add(config);
        }
        return listMap;
    }

    // 将 Map<系统名, DatabaseConfig> 转换为 Map<数据库类型, Map<系统名, DatabaseConfig>>
    private Map<String, Map<String, DatabaseConfig>> convertToTypeSystemMap(Map<String, DatabaseConfig> configMap) {
        Map<String, Map<String, DatabaseConfig>> grouped = new HashMap<>();
        for (Map.Entry<String, DatabaseConfig> entry : configMap.entrySet()) {
            String systemName = entry.getKey();
            DatabaseConfig cfg = entry.getValue();
            if (cfg == null || !cfg.isEnable()) {
                continue;
            }
            String dbType = cfg.getType() != null ? cfg.getType() : "unknown";
            grouped.computeIfAbsent(dbType, k -> new HashMap<>()).put(systemName, cfg);
        }
        return grouped;
    }
    
    /**
     * 加载数据库配置
     */
    private Map<String, DatabaseConfig> loadDatabaseConfigs() throws Exception {
        LogManager.startTimer("load_database_configs");
        
        try {
            Map<String, DatabaseConfig> configs = configLoader.loadDatabaseConfigs(config.getConfigPath());
            
            long duration = LogManager.endTimer("load_database_configs");
            int totalConfigs = configs.size();
            
            LogManager.logConfigurationLoaded("数据库配置", config.getConfigPath(), totalConfigs);
            logger.info("加载数据库配置完成: {} 个配置项, 耗时: {}ms", totalConfigs, duration);
            
            return configs;
            
        } catch (Exception e) {
            LogManager.endTimer("load_database_configs");
            LogManager.logConfigurationLoadFailure("数据库配置", config.getConfigPath(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 加载指标配置
     */
    private List<MetricConfig> loadMetricConfigs() throws Exception {
        LogManager.startTimer("load_metric_configs");
        
        try {
            List<MetricConfig> configs = configLoader.loadMetricConfigs(config.getMetricsPath());
            
            long duration = LogManager.endTimer("load_metric_configs");
            int totalMetrics = configs.size();
            
            LogManager.logConfigurationLoaded("指标配置", config.getMetricsPath(), totalMetrics);
            logger.info("加载指标配置完成: {} 个指标, 耗时: {}ms", totalMetrics, duration);
            
            return configs;
            
        } catch (Exception e) {
            LogManager.endTimer("load_metric_configs");
            LogManager.logConfigurationLoadFailure("指标配置", config.getMetricsPath(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 执行指标收集
     */
    private List<MetricResult> executeMetricsCollection(Map<String, DatabaseConfig> databaseConfigs,
                                                       List<MetricConfig> metricConfigs) throws Exception {
        LogManager.startTimer("metrics_collection");
        
        try {
            // 基于目录重新加载并按文件名分组执行（仅执行对应类型指标）
            List<MetricResult> results = metricsExecutor.executeAllMetrics(config.getConfigPath(), config.getMetricsPath());
            
            // 保存指标结果到持久化存储
            if (storageManager != null && !results.isEmpty()) {
                logger.info("开始保存 {} 个指标结果到持久化存储", results.size());
                storageManager.saveMetricResults(results);
                storageManager.flush(); // 确保所有数据都写入存储
                logger.info("指标结果保存完成");
            }
            
            long duration = LogManager.endTimer("metrics_collection");
            logger.info("指标收集完成: {} 个结果, 耗时: {}ms", results.size(), duration);
            
            return results;
            
        } catch (Exception e) {
            LogManager.endTimer("metrics_collection");
            logger.error("指标收集失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 生成报告
     */
    private boolean generateReports(List<MetricResult> results) {
        boolean allSuccess = true;
        List<ReportGenerator> generators = reportGeneratorFactory.createGenerators(config.getOutputFormat());

        for (ReportGenerator generator : generators) {
            try {
                String format = generator.getFormat().toUpperCase();
                LogManager.logReportGenerationStart(format, config.getOutputPath());
                LogManager.startTimer(format + "_report_generation");

                generator.generate(results, config.getOutputPath(), config.getMetricsPath());

                long duration = LogManager.endTimer(format + "_report_generation");
                LogManager.logReportGenerationSuccess(format, config.getOutputPath(), results.size(), duration);
            } catch (Exception e) {
                LogManager.logReportGenerationFailure(generator.getFormat(), e.getMessage(), 0);
                logger.error("{} 报告生成失败: {}", generator.getFormat().toUpperCase(), e.getMessage(), e);
                allSuccess = false;
            }
        }

        return allSuccess;
    }
    
    /**
     * 输出执行摘要
     */
    private void printExecutionSummary(List<MetricResult> results) {
        logger.info("=".repeat(60));
        logger.info("执行摘要");
        logger.info("=".repeat(60));
        
        long successCount = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        long failureCount = results.size() - successCount;
        double successRate = results.size() > 0 ? (double) successCount / results.size() * 100 : 0;
        
        logger.info("总指标数: {}", results.size());
        logger.info("成功收集: {}", successCount);
        logger.info("收集失败: {}", failureCount);
        String successRateStr = String.format("%.2f", successRate);
        logger.info("成功率: {}%", successRateStr);
        
        // 按数据库类型统计
        Map<String, Long> dbTypeStats = results.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                MetricResult::getDbType,
                java.util.stream.Collectors.counting()
            ));
        
        logger.info("按数据库类型统计:");
        dbTypeStats.forEach((dbType, count) -> 
            logger.info("  {}: {} 个指标", dbType, count));
        
        // 输出报告文件位置（统一使用 Path.resolve，文件名为 yyyyMMdd）
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        java.nio.file.Path outPath = java.nio.file.Paths.get(config.getOutputPath());
        java.nio.file.Path excelPath = outPath.resolve("db_metrics_report_" + date + ".xlsx").normalize();
        java.nio.file.Path htmlPath = outPath.resolve("db_metrics_report_" + date + ".html").normalize();
        logger.info("报告文件:");
        
        String outputFormat = config.getOutputFormat() != null ? config.getOutputFormat().toLowerCase() : "excel";
        if ("excel".equals(outputFormat) || "both".equals(outputFormat)) {
            logger.info("  Excel: {}", excelPath.toString());
        }
        if ("html".equals(outputFormat) || "both".equals(outputFormat)) {
            logger.info("  HTML: {}", htmlPath.toString());
        }
        
        logger.info("=".repeat(60));
    }
    
    /**
     * 创建必要的目录
     */
    private void createDirectories() {
        String[] directories = {
            config.getConfigPath(),
            config.getMetricsPath(), 
            config.getOutputPath(),
            "logs",
            "lib"
        };
        
        for (String dir : directories) {
            FileUtil.createDirectoryIfNotExists(dir);
        }
        
        logger.debug("必要目录创建完成");
    }
    
    private void cleanConnectionTestFiles() {
        try {
            java.nio.file.Path err = java.nio.file.Paths.get("logs/db_conn_error.txt");
            java.nio.file.Path bl = java.nio.file.Paths.get("logs/db_conn_blacklist.txt");
            if (err.getParent() != null) java.nio.file.Files.createDirectories(err.getParent());
            if (bl.getParent() != null) java.nio.file.Files.createDirectories(bl.getParent());
            java.nio.file.Files.deleteIfExists(err);
            java.nio.file.Files.deleteIfExists(bl);
            logger.info("已清理历史失败清单与黑名单");
        } catch (Exception ex) {
            logger.warn("清理失败清单/黑名单文件失败: {}", ex.getMessage());
        }
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        try {
            if (databaseManager != null) {
                databaseManager.cleanup();
            }
            if (metricsExecutor != null) {
                metricsExecutor.shutdown();
            }
            if (connectionTestService != null) {
                connectionTestService.shutdown();
            }
            if (storageManager != null) {
                storageManager.close();
            }
            
            logger.debug("资源清理完成");
            
        } catch (Exception e) {
            logger.warn("清理资源时出现异常: {}", e.getMessage());
        }
    }
}