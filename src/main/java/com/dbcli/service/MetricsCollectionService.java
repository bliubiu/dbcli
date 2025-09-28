package com.dbcli.service;

import com.dbcli.config.AppConfig;
import com.dbcli.database.DriverLoader;
import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import com.dbcli.util.DataMaskUtil;
import com.dbcli.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 修复的指标收集服务
 * 解决单值指标数据收集不完整的问题
 */
public class MetricsCollectionService {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectionService.class);

    private final AppConfig config;
    private final Set<String> failedConnections;
    private final Set<String> blacklistedConnections;
    private final List<MetricResult> allResults;
    private final ExecutorService executorService;

    // 统计信息
    private int totalNodes = 0;
    private int successfulNodes = 0;
    private int failedNodes = 0;
    private int skippedNodes = 0;
    private int totalMetrics = 0;
    private int successfulMetrics = 0;
    private int failedMetrics = 0;

    public MetricsCollectionService(AppConfig config) {
        this.config = config;
        this.failedConnections = loadFailedConnections();
        this.blacklistedConnections = loadBlacklistedConnections();
        this.allResults = Collections.synchronizedList(new ArrayList<>());
        this.executorService = Executors.newFixedThreadPool(Math.min(config.getThreads(), 5));
    }

    public void collectMetrics() throws Exception {
        logger.info("开始执行修复的指标收集...");

        try {
            checkDriverStatus();
            Map<String, Map<String, Object>> allConfigs = loadAllConfigs();
            Map<String, List<MetricConfig>> allMetrics = loadAllMetrics();

            if (allConfigs.isEmpty()) {
                logger.warn("未找到任何数据库配置");
                return;
            }

            if (allMetrics.isEmpty()) {
                logger.warn("未找到任何指标配置");
                return;
            }

            executeMetricsCollection(allConfigs, allMetrics);
            generateReport();
            printStatistics();

        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkDriverStatus() {
        logger.info("检查数据库驱动状态...");
        DriverLoader.loadAllDrivers();

        List<String> availableTypes = DriverLoader.getAvailableDbTypes();
        if (availableTypes.isEmpty()) {
            throw new RuntimeException("未找到任何可用的数据库驱动");
        }

        logger.info("可用的数据库驱动: {}", String.join(", ", availableTypes));
    }

    private Set<String> loadFailedConnections() {
        Set<String> failed = new HashSet<>();
        File errorFile = new File("logs/db_conn.err");

        if (errorFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(errorFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    int bracket = line.indexOf("] ");
                    int colon = line.indexOf(':', (bracket >= 0 ? bracket + 2 : 0));
                    if (colon > (bracket >= 0 ? bracket + 1 : -1)) {
                        String hostPart = line.substring((bracket >= 0 ? bracket + 2 : 0), colon).trim();
                        if (!hostPart.isEmpty()) {
                            failed.add(hostPart);
                        }
                    }
                }
                logger.info("加载失败连接清单(按主机): {} 个", failed.size());
            } catch (IOException e) {
                logger.warn("读取连接失败清单文件异常", e);
            }
        }

        return failed;
    }

    private Set<String> loadBlacklistedConnections() {
        Set<String> blacklisted = new HashSet<>();
        File blacklistFile = new File("logs/db_conn_blacklist.txt");

        if (blacklistFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(blacklistFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        blacklisted.add(line);
                    }
                }
                logger.info("加载黑名单连接: {} 个", blacklisted.size());
            } catch (IOException e) {
                logger.warn("读取黑名单文件异常", e);
            }
        }

        return blacklisted;
    }

    private void executeMetricsCollection(Map<String, Map<String, Object>> allConfigs,
                                          Map<String, List<MetricConfig>> allMetrics) {

        List<Future<Void>> futures = new ArrayList<>();

        for (String dbType : Arrays.asList("oracle", "mysql", "pg", "dm")) {
            if (!DriverLoader.isDriverAvailable(dbType)) {
                logger.warn("跳过不支持的数据库类型: {} (驱动不可用)", dbType);
                continue;
            }

            if (allConfigs.containsKey(dbType) && allMetrics.containsKey(dbType)) {
                logger.info("为数据库类型 {} 启动指标收集任务，配置数量: {}，指标数量: {}", 
                    dbType.toUpperCase(), allConfigs.get(dbType).size(), allMetrics.get(dbType).size());
                
                final String finalDbType = dbType;
                Future<Void> future = executorService.submit(() -> {
                    collectMetricsForDbType(finalDbType, allConfigs.get(finalDbType), allMetrics.get(finalDbType));
                    return null;
                });
                futures.add(future);
            }
        }

        if (futures.isEmpty()) {
            logger.warn("没有找到任何可执行的数据库类型配置");
            return;
        }

        logger.info("启动了 {} 个数据库类型的指标收集任务", futures.size());

        for (Future<Void> future : futures) {
            try {
                future.get(300, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.error("指标收集任务超时");
                future.cancel(true);
            } catch (Exception e) {
                logger.error("指标收集任务异常", e);
            }
        }
    }

    private Map<String, Map<String, Object>> loadAllConfigs() throws IOException {
        Map<String, Map<String, Object>> allConfigs = new HashMap<>();

        File configDir = new File(config.getConfigPath());
        if (!configDir.exists()) {
            logger.warn("配置目录不存在: {}", config.getConfigPath());
            return allConfigs;
        }

        File[] configFiles = configDir.listFiles((dir, name) ->
                name.endsWith("-config.yaml") || name.endsWith("-config.yml"));

        if (configFiles != null) {
            Yaml yaml = new Yaml();
            for (File configFile : configFiles) {
                String dbType = extractDbType(configFile.getName());
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    Map<String, Object> cfg = yaml.load(fis);
                    if (cfg != null) {
                        allConfigs.put(dbType, cfg);
                    }
                } catch (Exception e) {
                    logger.error("加载配置文件失败: {}", configFile.getName(), e);
                }
            }
        }

        return allConfigs;
    }

    private Map<String, List<MetricConfig>> loadAllMetrics() throws IOException {
        Map<String, List<MetricConfig>> allMetrics = new HashMap<>();

        File metricsDir = new File(config.getMetricsPath());
        if (!metricsDir.exists()) {
            logger.warn("指标目录不存在: {}", config.getMetricsPath());
            return allMetrics;
        }

        File[] metricFiles = metricsDir.listFiles((dir, name) ->
                name.endsWith("-metrics.yaml") || name.endsWith("-metrics.yml"));

        if (metricFiles != null) {
            Yaml yaml = new Yaml();
            for (File metricFile : metricFiles) {
                String dbType = extractDbType(metricFile.getName());
                try (FileInputStream fis = new FileInputStream(metricFile)) {
                    Object yamlContent = yaml.load(fis);
                    List<Map<String, Object>> metricsList = null;
                    
                    if (yamlContent instanceof List) {
                        metricsList = (List<Map<String, Object>>) yamlContent;
                    } else if (yamlContent instanceof Map) {
                        Map<String, Object> yamlMap = (Map<String, Object>) yamlContent;
                        if (yamlMap.containsKey("metrics")) {
                            Object metricsObj = yamlMap.get("metrics");
                            if (metricsObj instanceof List) {
                                metricsList = (List<Map<String, Object>>) metricsObj;
                            }
                        }
                    }
                    
                    if (metricsList != null) {
                        List<MetricConfig> metrics = parseMetrics(metricsList);
                        allMetrics.put(dbType, metrics);
                        logger.info("从文件 {} 加载了 {} 个指标配置", metricFile.getName(), metrics.size());
                    } else {
                        logger.warn("无法解析指标文件: {} - 格式不正确", metricFile.getName());
                    }
                } catch (Exception e) {
                    logger.error("加载指标文件失败: {}", metricFile.getName(), e);
                }
            }
        }

        return allMetrics;
    }

    private String extractDbType(String filename) {
        if (filename.startsWith("oracle-")) return "oracle";
        if (filename.startsWith("mysql-")) return "mysql";
        if (filename.startsWith("pg-")) return "pg";
        if (filename.startsWith("dm-")) return "dm";
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private List<MetricConfig> parseMetrics(List<Map<String, Object>> metricsList) {
        List<MetricConfig> metrics = new ArrayList<>();

        for (Map<String, Object> metricData : metricsList) {
            try {
                MetricConfig metric = new MetricConfig();
                metric.setType((String) metricData.get("type"));
                metric.setName((String) metricData.get("name"));
                metric.setDescription((String) metricData.get("description"));
                metric.setSql((String) metricData.get("sql"));

                Object columnsObj = metricData.get("columns");
                if (columnsObj instanceof List) {
                    metric.setColumns((List<String>) columnsObj);
                }

                Object strategyObj = metricData.get("execution_strategy");
                if (strategyObj instanceof Map) {
                    Map<String, Object> strategyMap = (Map<String, Object>) strategyObj;
                    MetricConfig.ExecutionStrategy strategy = new MetricConfig.ExecutionStrategy();
                    strategy.setMode((String) strategyMap.get("mode"));

                    Object retryObj = strategyMap.get("retry_policy");
                    if (retryObj instanceof Map) {
                        Map<String, Object> retryMap = (Map<String, Object>) retryObj;
                        MetricConfig.RetryPolicy retry = new MetricConfig.RetryPolicy();
                        retry.setEnabled((Boolean) retryMap.getOrDefault("enabled", false));
                        retry.setMaxAttempts((Integer) retryMap.getOrDefault("max_attempts", 1));
                        retry.setBackoffMs(((Number) retryMap.getOrDefault("backoff_ms", 1000)).longValue());
                        retry.setDelayMs(((Number) retryMap.getOrDefault("delay_ms", 1000)).longValue());
                        strategy.setRetryPolicy(retry);
                    }

                    metric.setExecutionStrategy(strategy);
                }

                Object thresholdObj = metricData.get("threshold");
                if (thresholdObj instanceof Map) {
                    Map<String, Object> thresholdMap = (Map<String, Object>) thresholdObj;
                    MetricConfig.Threshold threshold = new MetricConfig.Threshold();
                    threshold.setLevel((String) thresholdMap.get("level"));
                    threshold.setOperator((String) thresholdMap.get("operator"));
                    threshold.setValue(thresholdMap.get("value"));
                    metric.setThreshold(threshold);
                }

                metrics.add(metric);
            } catch (Exception e) {
                logger.error("解析指标配置失败: {}", metricData.get("name"), e);
            }
        }

        return metrics;
    }

    private void collectMetricsForDbType(String dbType, Map<String, Object> configs, List<MetricConfig> metrics) {
        logger.info("开始收集 {} 数据库指标，共 {} 个指标", dbType.toUpperCase(), metrics.size());

        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            String systemName = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dbConfigMap = (Map<String, Object>) value;
                
                String configDbType = (String) dbConfigMap.get("type");
                if (!isDbTypeMatch(dbType, configDbType)) {
                    logger.warn("数据库类型不匹配: 期望 {}, 实际 {}, 跳过系统 {}", 
                        dbType, configDbType, systemName);
                    continue;
                }
                
                logger.debug("处理系统 {} (类型: {})", systemName, configDbType);
                collectMetricsForSystem(systemName, dbConfigMap, metrics, dbType);
            }
        }
    }

    private boolean isDbTypeMatch(String expectedType, String actualType) {
        if (actualType == null) return false;
        
        String normalizedExpected = normalizeDbType(expectedType);
        String normalizedActual = normalizeDbType(actualType);
        
        return normalizedExpected.equals(normalizedActual);
    }

    private String normalizeDbType(String dbType) {
        if (dbType == null) return "";
        
        String normalized = dbType.toLowerCase().trim();
        
        switch (normalized) {
            case "postgresql":
            case "postgres":
                return "pg";
            case "dameng":
                return "dm";
            default:
                return normalized;
        }
    }

    @SuppressWarnings("unchecked")
    private void collectMetricsForSystem(String systemName, Map<String, Object> dbConfigMap,
                                         List<MetricConfig> metrics, String dbType) {
        Boolean enable = (Boolean) dbConfigMap.get("enable");
        if (enable == null || !enable) {
            logger.debug("跳过未启用的数据库系统: {}", systemName);
            return;
        }

        try {
            Integer defaultPort = (Integer) dbConfigMap.get("port");
            String username = EncryptionUtil.decrypt((String) dbConfigMap.get("username"));
            String password = EncryptionUtil.decrypt((String) dbConfigMap.get("password"));

            Object nodesObj = dbConfigMap.get("nodes");
            if (nodesObj instanceof Map) {
                Map<String, Object> nodes = (Map<String, Object>) nodesObj;

                for (Map.Entry<String, Object> nodeEntry : nodes.entrySet()) {
                    String nodeName = nodeEntry.getKey();

                    if (nodeEntry.getValue() instanceof Map) {
                        Map<String, Object> nodeConfig = (Map<String, Object>) nodeEntry.getValue();
                        collectMetricsForNode(systemName, nodeName, nodeConfig, defaultPort,
                                username, password, metrics, dbType);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("处理数据库系统失败: {} - {}", systemName, e.getMessage());
        }
    }

    private void collectMetricsForNode(String systemName, String nodeName, Map<String, Object> nodeConfig,
                                       Integer defaultPort, String username, String password,
                                       List<MetricConfig> metrics, String dbType) {

        String host = EncryptionUtil.decrypt((String) nodeConfig.get("host"));
        String instName = (String) nodeConfig.get("inst_name");
        Integer port = (Integer) nodeConfig.getOrDefault("port", defaultPort);
        String role = (String) nodeConfig.get("role");

        String connectionId = String.format("%s-%s-%s", systemName, nodeName, DataMaskUtil.maskHostname(host));

        totalNodes++;

        String maskedHost = DataMaskUtil.maskHostname(host);
        if (failedConnections.contains(maskedHost)) {
            logger.info("⏭️  跳过失败的连接: {} ({})", connectionId, maskedHost);
            skippedNodes++;
            return;
        }

        if (blacklistedConnections.contains(connectionId) || 
            blacklistedConnections.contains(EncryptionUtil.encrypt(connectionId))) {
            logger.info("⏭️  跳过黑名单连接: {}", connectionId);
            skippedNodes++;
            return;
        }
        
        if (blacklistedConnections.contains(maskedHost)) {
            logger.info("⏭️  跳过黑名单主机: {} ({})", connectionId, maskedHost);
            skippedNodes++;
            return;
        }

        try {
            String jdbcUrl = buildJdbcUrl(dbType, host, port, instName);

            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("connectTimeout", "10000");
            props.setProperty("socketTimeout", "30000");

            try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
                logger.info("🔗 开始收集节点指标: {}", connectionId);

                int nodeSuccessCount = 0;
                int nodeFailCount = 0;

                for (MetricConfig metric : metrics) {
                    totalMetrics++;
                    boolean success = collectSingleMetric(conn, systemName, instName,
                            DataMaskUtil.maskHostname(host), metric, dbType, role);
                    if (success) {
                        successfulMetrics++;
                        nodeSuccessCount++;
                    } else {
                        failedMetrics++;
                        nodeFailCount++;
                    }
                }

                logger.info("✅ 节点指标收集完成: {} (成功: {}, 失败: {})",
                        connectionId, nodeSuccessCount, nodeFailCount);
                successfulNodes++;

            } catch (SQLException e) {
                logger.error("❌ 数据库连接失败: {} - {}", connectionId, e.getMessage());
                failedNodes++;
                addToBlacklist(connectionId, jdbcUrl, e.getMessage());
            }

        } catch (Exception e) {
            logger.error("💥 指标收集异常: {} - {}", connectionId, e.getMessage());
            failedNodes++;
        }
    }
    
    private void addToBlacklist(String connectionId, String jdbcUrl, String errorMessage) {
        try {
            File logDir = new File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            File blacklistFile = new File("logs/db_conn_blacklist.txt");
            try (FileWriter writer = new FileWriter(blacklistFile, true)) {
                String encryptedConnectionId = EncryptionUtil.encrypt(connectionId);
                writer.write(encryptedConnectionId + "\n");
                writer.flush();
                
                logger.info("已将失败连接添加到黑名单: {}", connectionId);
            }
            
            File errorFile = new File("logs/db_conn_error.txt");
            try (FileWriter writer = new FileWriter(errorFile, true)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String maskedJdbcUrl = DataMaskUtil.maskJdbcUrl(jdbcUrl);
                writer.write(String.format("[%s] %s - %s\n", timestamp, maskedJdbcUrl, errorMessage));
                writer.flush();
            }
            
        } catch (Exception e) {
            logger.warn("写入黑名单文件失败", e);
        }
    }

    private String buildJdbcUrl(String dbType, String host, Integer port, String instName) {
        switch (dbType.toLowerCase()) {
            case "oracle":
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, instName);
            case "mysql":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&connectTimeout=10000&socketTimeout=30000",
                        host, port, instName);
            case "pg":
                return String.format("jdbc:postgresql://%s:%d/%s?connectTimeout=10&socketTimeout=30",
                        host, port, instName);
            case "dm":
                return String.format("jdbc:dm://%s:%d", host, port);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
    }

    private boolean collectSingleMetric(Connection conn, String systemName, String databaseName, String nodeIp,
                                        MetricConfig metric, String dbType, String role) {

        if (!shouldExecuteMetric(metric, role)) {
            logger.debug("跳过指标 {} (执行策略不匹配)", metric.getName());
            return true;
        }

        MetricResult result = new MetricResult(systemName, databaseName, nodeIp,
                metric.getName(), metric.getDescription(),
                metric.getType(), dbType);

        try {
            if ("SINGLE".equalsIgnoreCase(metric.getType())) {
                collectSingleValueMetric(conn, metric, result);
            } else if ("MULTI".equalsIgnoreCase(metric.getType())) {
                collectMultiValueMetric(conn, metric, result);
            }

            result.setSuccess(true);
            logger.debug("✅ 指标收集成功: {} - {}", metric.getName(), systemName);

            synchronized (allResults) {
                allResults.add(result);
            }

            return true;

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            logger.error("❌ 指标收集失败: {} - {} - {}", metric.getName(), systemName, e.getMessage());

            synchronized (allResults) {
                allResults.add(result);
            }

            return false;
        }
    }

    private boolean shouldExecuteMetric(MetricConfig metric, String role) {
        MetricConfig.ExecutionStrategy strategy = metric.getExecutionStrategy();
        if (strategy == null) {
            return true;
        }

        String mode = strategy.getMode();
        if ("first".equalsIgnoreCase(mode)) {
            return "primary".equalsIgnoreCase(role) || "master".equalsIgnoreCase(role);
        } else if ("role".equalsIgnoreCase(mode)) {
            return true;
        }

        return true;
    }

    private void collectSingleValueMetric(Connection conn, MetricConfig metric, MetricResult result) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(metric.getSql())) {
            stmt.setQueryTimeout(30);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (metric.getColumns() != null && !metric.getColumns().isEmpty()) {
                        // 处理多列单值指标
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        List<String> columnNames = new ArrayList<>();
                        List<String> configColumns = metric.getColumns();
                        
                        for (int i = 0; i < columnCount; i++) {
                            if (i < configColumns.size()) {
                                columnNames.add(configColumns.get(i));
                            } else {
                                columnNames.add(metaData.getColumnLabel(i + 1));
                            }
                        }
                        
                        result.setColumns(columnNames);
                        
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = columnNames.get(i - 1);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        
                        result.setMultiValues(Arrays.asList(row));
                        
                        // 将所有值连接成一个字符串作为主值
                        StringBuilder valueBuilder = new StringBuilder();
                        for (int i = 1; i <= columnCount; i++) {
                            if (i > 1) valueBuilder.append(" | ");
                            Object val = rs.getObject(i);
                            valueBuilder.append(val != null ? val.toString() : "");
                        }
                        result.setValue(valueBuilder.toString());
                        
                        logger.debug("多列单值指标收集成功: {} - {} 列", metric.getName(), columnCount);
                    } else {
                        // 处理单列单值指标 - 使用description作为列名
                        Object value = rs.getObject(1);
                        result.setValue(value);
                        
                        // 为单列单值指标也设置columns和multiValues，以便Excel报告生成器统一处理
                        String columnName = metric.getDescription() != null ? metric.getDescription() : metric.getName();
                        result.setColumns(Arrays.asList(columnName));
                        
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put(columnName, value);
                        result.setMultiValues(Arrays.asList(row));
                        
                        logger.debug("单值指标收集成功: {} = {} (列名: {})", metric.getName(), value, columnName);
                    }
                }
            }
        }
    }

    private void collectMultiValueMetric(Connection conn, MetricConfig metric, MetricResult result) throws SQLException {
        List<Map<String, Object>> values = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(metric.getSql())) {
            stmt.setQueryTimeout(30);

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                List<String> columnNames = new ArrayList<>();
                if (metric.getColumns() != null && !metric.getColumns().isEmpty()) {
                    List<String> cfg = metric.getColumns();
                    for (int i = 0; i < columnCount; i++) {
                        if (i < cfg.size()) {
                            columnNames.add(cfg.get(i));
                        } else {
                            columnNames.add(metaData.getColumnLabel(i + 1));
                        }
                    }
                } else {
                    for (int i = 1; i <= columnCount; i++) {
                        columnNames.add(metaData.getColumnLabel(i));
                    }
                }

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();

                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = columnNames.get(i - 1);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }

                    values.add(row);
                }

                result.setColumns(columnNames);
            }
        }

        result.setMultiValues(values);
    }

    private void generateReport() throws Exception {
        if (allResults.isEmpty()) {
            logger.warn("⚠️  没有收集到任何指标数据，无法生成报告");
            return;
        }

        try {
            ReportGeneratorFactory factory = new ReportGeneratorFactory();
            List<ReportGenerator> generators = factory.createGenerators(config.getFormat());

            if (generators.isEmpty()) {
                logger.warn("⚠️ 不支持的报告格式或格式未指定: {}", config.getFormat());
            }

            for (ReportGenerator generator : generators) {
                generator.generate(allResults, config.getOutputPath(), config.getMetricsPath());
                logger.info("📊 {} 报告生成成功", generator.getClass().getSimpleName());
            }

            generateStatisticsReport();

        } catch (Exception e) {
            logger.error("❌ 生成报告失败", e);
            throw e;
        }
    }

    private void generateStatisticsReport() throws IOException {
        File logDir = new File("logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File statsFile = new File(logDir, "metrics_stats_" + timestamp + ".txt");

        try (PrintWriter writer = new PrintWriter(new FileWriter(statsFile))) {
            writer.println("# 指标收集统计报告");
            writer.println("# 生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println();

            writer.println("## 节点统计");
            writer.println("- 总节点数: " + totalNodes);
            writer.println("- 成功节点: " + successfulNodes);
            writer.println("- 失败节点: " + failedNodes);
            writer.println("- 跳过节点: " + skippedNodes);
            writer.printf("- 成功率: %.2f%%%n", totalNodes > 0 ? (double) successfulNodes / totalNodes * 100 : 0);
            writer.println();

            writer.println("## 指标统计");
            writer.println("- 总指标数: " + totalMetrics);
            writer.println("- 成功指标: " + successfulMetrics);
            writer.println("- 失败指标: " + failedMetrics);
            writer.printf("- 成功率: %.2f%%%n", totalMetrics > 0 ? (double) successfulMetrics / totalMetrics * 100 : 0);
            writer.println();

            writer.println("## 数据库类型统计");
            Map<String, Integer> dbTypeCount = new HashMap<>();
            Map<String, Integer> dbTypeSuccess = new HashMap<>();
            
            for (MetricResult result : allResults) {
                String dbType = result.getDbType() != null ? result.getDbType().toUpperCase() : "UNKNOWN";
                dbTypeCount.put(dbType, dbTypeCount.getOrDefault(dbType, 0) + 1);
                if (result.isSuccess()) {
                    dbTypeSuccess.put(dbType, dbTypeSuccess.getOrDefault(dbType, 0) + 1);
                }
            }
            
            for (Map.Entry<String, Integer> entry : dbTypeCount.entrySet()) {
                String dbType = entry.getKey();
                int total = entry.getValue();
                int success = dbTypeSuccess.getOrDefault(dbType, 0);
                writer.printf("- %s: 总数=%d, 成功=%d, 成功率=%.2f%%%n", 
                    dbType, total, success, total > 0 ? (double) success / total * 100 : 0);
            }
        }

        logger.info("统计报告已生成: {}", statsFile.getAbsolutePath());
    }

    private void printStatistics() {
        logger.info("=== 指标收集统计 ===");
        logger.info("节点统计 - 总数: {}, 成功: {}, 失败: {}, 跳过: {}", 
            totalNodes, successfulNodes, failedNodes, skippedNodes);
        logger.info("指标统计 - 总数: {}, 成功: {}, 失败: {}", 
            totalMetrics, successfulMetrics, failedMetrics);
        
        if (totalNodes > 0) {
            double nodeSuccessRate = (double) successfulNodes / totalNodes * 100;
            logger.info("节点成功率: {:.2f}%", nodeSuccessRate);
        }
        
        if (totalMetrics > 0) {
            double metricSuccessRate = (double) successfulMetrics / totalMetrics * 100;
            logger.info("指标成功率: {:.2f}%", metricSuccessRate);
        }
        
        logger.info("收集到的结果总数: {}", allResults.size());
        logger.info("===================");
    }
}
