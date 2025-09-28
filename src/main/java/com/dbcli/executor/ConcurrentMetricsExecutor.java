package com.dbcli.executor;

import com.dbcli.config.ConfigLoader;
import com.dbcli.database.DatabaseManager;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import com.dbcli.service.EncryptionService;
import com.dbcli.util.DataMaskUtil;
import com.dbcli.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 并发指标执行器
 * - 按指标文件名匹配数据库类型，仅为对应类型执行（oracle/mysql/pg/dm -> manager键 oracle/mysql/postgresql/dm）
 * - 执行前读取 logs/db_conn.err 失败清单，若某系统所有节点均命中失败清单则跳过该系统
 * - 支持自定义指标目录（默认 metrics）
 */
public class ConcurrentMetricsExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentMetricsExecutor.class);

    private final DatabaseManager databaseManager;
    private final Map<String, QueryExecutor> queryExecutors = new ConcurrentHashMap<>();
    private final ConfigLoader configLoader;

    private final int threadCount;
    private final long executionTimeoutSeconds;

    private String metricsDirPath = "metrics";
    private Set<String> failedEncryptedHosts = Collections.emptySet();

    public ConcurrentMetricsExecutor(int threadCount, long executionTimeoutSeconds) {
        this.threadCount = threadCount;
        this.executionTimeoutSeconds = executionTimeoutSeconds;

        this.databaseManager = new DatabaseManager();
        this.configLoader = new ConfigLoader(new EncryptionService());
    }

    /**
     * 兼容运行器调用（内存模式）：
     * 内存指标缺少文件来源，无法可靠分发到类型，为避免跨类型执行，这里仅初始化环境并返回空。
     */
    public List<MetricResult> executeMetrics(Map<String, DatabaseConfig> databaseConfigs,
                                             List<MetricConfig> metricConfigs, int timeoutSeconds) {
        logger.info("开始执行指标收集（内存模式），数据库配置数: {}, 指标数(忽略类型分发): {}",
                databaseConfigs != null ? databaseConfigs.size() : 0,
                metricConfigs != null ? metricConfigs.size() : 0);
        try {
            databaseManager.initialize(databaseConfigs);
            logger.warn("内存模式无指标文件来源，已跳过分发以避免跨类型执行。");
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("指标收集执行失败（内存模式）", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从目录加载配置与指标，并并发执行
     */
    public List<MetricResult> executeAllMetrics(String configPath, String metricsPath) {
        logger.info("开始并发执行指标收集，线程数: {}", threadCount);

        try {
            // 1. 加载数据库配置
            databaseManager.loadConfigurations(configPath);

            // 2. （兼容旧逻辑）连接测试入口保留（若使用 FastConnectionTestService 执行过，将产生失败清单）
            databaseManager.testAllConnections();

            // 3. 覆盖指标目录
            if (metricsPath != null && !metricsPath.trim().isEmpty()) {
                this.metricsDirPath = metricsPath.trim();
            }

            // 4. 加载连接失败灰名单（加密标识），用于自动跳过后续指标执行
            Set<String> encBlacklist = loadEncryptedBlacklist("logs/db_conn_blacklist.txt");
            setFailedEncryptedHosts(encBlacklist);
            logger.info("已加载失败主机清单(加密)条目数: {}", failedEncryptedHosts.size());

            // 5. 加载并按类型分组指标（按文件名）
            Map<String, List<MetricConfig>> metricsByType = loadAndGroupMetricsByFile(this.metricsDirPath);
            if (metricsByType.isEmpty()) {
                logger.warn("未找到任何可执行的指标文件，目录: {}", this.metricsDirPath);
                return Collections.emptyList();
            }

            // 6. 并发执行
            List<MetricResult> results = executeConcurrently(metricsByType);

            // 7. 打印摘要
            printStats(results);

            return results;
        } catch (Exception e) {
            logger.error("指标收集执行失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 读取失败连接清单（按脱敏IP）
     * 匹配形如 ***.***.x.y 的主机标识
     */
    private Set<String> loadFailedMaskedHosts(String errFilePath) {
        Set<String> set = new HashSet<>();
        File f = new File(errFilePath);
        if (!f.exists() || !f.isFile()) {
            return set;
        }
        Pattern p = Pattern.compile("(\\*{3}\\.\\*{3}\\.[0-9]{1,3}\\.[0-9]{1,3})");

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                while (m.find()) {
                    set.add(m.group(1));
                }
            }
        } catch (Exception e) {
            logger.warn("读取失败连接清单异常: {}", e.getMessage());
        }
        return set;
    }

    /**
     * 从指标目录按文件名分组指标到数据库类型
     * 键名规范化以匹配 DatabaseManager.getAvailableSystems() 的键：
     * - oracle -> oracle
     * - mysql -> mysql
     * - pg -> postgresql
     * - dm/dameng -> dm
     */
    private QueryExecutor getOrCreateQueryExecutor(String dbType) {
        return queryExecutors.computeIfAbsent(dbType, key -> {
            logger.info("Creating dedicated query executor for database type: {}", key);
            // Each type gets its own pool with the configured concurrency.
            // This provides isolation, preventing a slow DB type from blocking others.
            return new QueryExecutor(databaseManager, this.threadCount);
        });
    }

    private Map<String, List<MetricConfig>> loadAndGroupMetricsByFile(String metricsDir) {
        Map<String, List<MetricConfig>> grouped = new HashMap<>();

        File dir = new File(metricsDir);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("指标目录不存在或不是目录: {}", metricsDir);
            return grouped;
        }

        File[] files = dir.listFiles((d, name) ->
                name.endsWith("-metrics.yml") || name.endsWith("-metrics.yaml"));
        if (files == null || files.length == 0) {
            logger.warn("指标目录未发现 *-metrics.yml(yaml) 文件: {}", metricsDir);
            return grouped;
        }

        for (File f : files) {
            String inferred = extractDbTypeFromFilename(f.getName());
            String managerKey = toManagerKey(inferred);
            if ("unknown".equals(managerKey)) {
                logger.warn("无法从文件名识别数据库类型，已跳过: {}", f.getName());
                continue;
            }

            try {
                Path p = f.toPath();
                List<MetricConfig> metrics = configLoader.loadMetricConfigsFromFile(p);
                if (metrics != null && !metrics.isEmpty()) {
                    grouped.computeIfAbsent(managerKey, k -> new ArrayList<>()).addAll(metrics);
                    logger.info("加载指标文件: {} -> 类型: {}，条数: {}", f.getName(), managerKey, metrics.size());
                } else {
                    logger.warn("指标文件为空或解析失败: {}", f.getName());
                }
            } catch (Exception ex) {
                logger.error("加载指标文件失败: {}", f.getName(), ex);
            }
        }

        int total = grouped.values().stream().mapToInt(List::size).sum();
        logger.info("指标分组完成，类型数: {}，合计指标条数: {}", grouped.size(), total);

        return grouped;
    }

    /**
     * 并发执行：仅对存在可用系统的数据库类型派发其对应指标
     * 若系统所有节点均在失败清单中，则跳过该系统
     */
    private List<MetricResult> executeConcurrently(Map<String, List<MetricConfig>> metricsByType) {
        Map<String, List<String>> available = databaseManager.getAvailableSystems();
        if (available.isEmpty()) {
            logger.warn("未发现任何已加载的数据库系统配置");
            return Collections.emptyList();
        }

        List<CompletableFuture<MetricResult>> allFutures = new ArrayList<>();

        for (Map.Entry<String, List<MetricConfig>> entry : metricsByType.entrySet()) {
            String managerKey = entry.getKey(); // 目标键（标准化后）
            List<MetricConfig> metrics = entry.getValue();

            // 根据 available 的真实键名做匹配（兼容键名差异）
            String actualKey = resolveAvailableKey(available, managerKey);
            if (actualKey == null) {
                logger.warn("跳过数据库类型 {}（在已加载系统中未找到匹配键）", managerKey);
                continue;
            }

            List<String> systems = available.get(actualKey);
            if (systems == null || systems.isEmpty()) {
                logger.warn("数据库类型 {} 未配置任何系统，跳过", actualKey);
                continue;
            }

            // 获取或创建该数据库类型的专用执行器
            QueryExecutor specificQueryExecutor = getOrCreateQueryExecutor(actualKey);

            for (String systemName : systems) {
                if (shouldSkipSystem(actualKey, systemName)) {
                    logger.info("⏭️  跳过系统（所有节点连接失败）：{} - {}", actualKey, systemName);
                    continue;
                }

                logger.info("为数据库类型 {} 分发指标，系统: {}，指标数: {}", actualKey, systemName, metrics.size());

                for (MetricConfig metric : metrics) {
                    String mode = (metric != null && metric.getExecutionStrategy() != null && metric.getExecutionStrategy().getMode() != null)
                            ? metric.getExecutionStrategy().getMode().toLowerCase(Locale.ROOT)
                            : "all";

                    switch (mode) {
                        case "all": {
                            List<DatabaseNode> nodes = databaseManager.getNodes(actualKey, systemName);
                            if (nodes == null || nodes.isEmpty()) {
                                logger.warn("系统 {} 无任何节点可执行（模式=all）", systemName);
                                break;
                            }
                            for (DatabaseNode node : nodes) {
                                if (shouldSkipNode(actualKey, systemName, node)) {
                                    logger.info("⏭️  跳过节点（连接失败）：{} - {} - {}", actualKey, systemName, node.getHost());
                                    continue;
                                }
                                CompletableFuture<MetricResult> f =
                                        specificQueryExecutor.executeMetricAsyncForNode(actualKey, systemName, metric, node);
                                allFutures.add(f);
                            }
                            break;
                        }
                        case "standby": {
                            List<DatabaseNode> nodes = databaseManager.getNodesByRole(actualKey, systemName, "standby");
                            if (nodes == null || nodes.isEmpty()) {
                                logger.warn("系统 {} 指标 {} 未找到 standby 节点，跳过", systemName, metric.getName());
                                break;
                            }
                            for (DatabaseNode node : nodes) {
                                if (shouldSkipNode(actualKey, systemName, node)) {
                                    logger.info("⏭️  跳过节点（连接失败）：{} - {} - {}", actualKey, systemName, node.getHost());
                                    continue;
                                }
                                CompletableFuture<MetricResult> f =
                                        specificQueryExecutor.executeMetricAsyncForNode(actualKey, systemName, metric, node);
                                allFutures.add(f);
                            }
                            break;
                        }
                        case "master": {
                            List<DatabaseNode> nodes = databaseManager.getNodesByRole(actualKey, systemName, "master");
                            if (nodes == null || nodes.isEmpty()) {
                                logger.warn("系统 {} 指标 {} 未找到 master 节点，跳过", systemName, metric.getName());
                                break;
                            }
                            for (DatabaseNode node : nodes) {
                                if (shouldSkipNode(actualKey, systemName, node)) {
                                    logger.info("⏭️  跳过节点（连接失败）：{} - {} - {}", actualKey, systemName, node.getHost());
                                    continue;
                                }
                                CompletableFuture<MetricResult> f =
                                        specificQueryExecutor.executeMetricAsyncForNode(actualKey, systemName, metric, node);
                                allFutures.add(f);
                            }
                            break;
                        }
                        case "first":
                        default: {
                            String role = determineNodeRole(metric);
                            CompletableFuture<MetricResult> f =
                                    specificQueryExecutor.executeMetricAsync(actualKey, systemName, metric, role);
                            allFutures.add(f);
                            break;
                        }
                    }
                }
            }
        }

        if (allFutures.isEmpty()) {
            logger.warn("未生成任何指标执行任务");
            return Collections.emptyList();
        }
        
        // 使用任意一个执行器来等待所有结果，因为等待逻辑与执行器实例无关
        return queryExecutors.values().iterator().next().waitForResults(allFutures, executionTimeoutSeconds);
    }

    /**
     * 判断是否应跳过系统：若该系统所有节点的“加密后的完整连接标识”均命中失败清单，则跳过
     * 连接标识 = dbType + "|" + JDBC_URL（由 ConnectionFactory.buildConnectionString 构造）
     */
    private boolean shouldSkipSystem(String dbType, String systemName) {
        if (failedEncryptedHosts == null || failedEncryptedHosts.isEmpty()) {
            return false;
        }
        List<DatabaseNode> nodes = databaseManager.getNodes(dbType, systemName);
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }
        int total = 0;
        int failed = 0;
        for (DatabaseNode n : nodes) {
            if (n.getHost() == null || n.getHost().isEmpty()) {
                continue;
            }
            total++;
            try {
                com.dbcli.model.DatabaseConfig cfg = databaseManager.getConfig(dbType, systemName);
                if (cfg != null) {
                    String id = dbType + "|" + databaseManager.getConnectionFactory().buildConnectionString(dbType, cfg, n);
                    String enc = EncryptionUtil.encryptDeterministic(id);
                    if (enc != null && failedEncryptedHosts.contains(enc)) {
                        failed++;
                    }
                }
            } catch (Exception ex) {
                logger.debug("跳过系统判断时构造连接标识异常: {}", ex.getMessage());
            }
        }
        return total > 0 && failed == total;
    }

    /**
     * 从文件名提取粗略类型（oracle/mysql/pg/dm）
     */
    private String extractDbTypeFromFilename(String filename) {
        String low = filename.toLowerCase(Locale.ROOT);
        if (low.startsWith("oracle-")) return "oracle";
        if (low.startsWith("mysql-")) return "mysql";
        if (low.startsWith("pg-")) return "pg";
        if (low.startsWith("dm-") || low.startsWith("dameng-")) return "dm";
        return "unknown";
    }

    /**
     * 加载加密的失败连接灰名单（每行一个加密标识）
     */
    private Set<String> loadEncryptedBlacklist(String filePath) {
        Set<String> set = new HashSet<>();
        File f = new File(filePath);
        if (!f.exists() || !f.isFile()) {
            return set;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (!s.isEmpty() && !s.startsWith("#")) {
                    set.add(s);
                }
            }
        } catch (Exception e) {
            logger.warn("读取连接失败灰名单异常: {}", e.getMessage());
        }
        return set;
    }

    /**
     * 节点级别跳过判断：若该节点的加密连接标识在灰名单中，则跳过
     */
    private boolean shouldSkipNode(String dbType, String systemName, DatabaseNode node) {
        if (failedEncryptedHosts == null || failedEncryptedHosts.isEmpty() || node == null) {
            return false;
        }
        try {
            com.dbcli.model.DatabaseConfig cfg = databaseManager.getConfig(dbType, systemName);
            if (cfg == null) return false;
            String id = dbType + "|" + databaseManager.getConnectionFactory().buildConnectionString(dbType, cfg, node);
            String enc = EncryptionUtil.encryptDeterministic(id);
            return enc != null && failedEncryptedHosts.contains(enc);
        } catch (Exception e) {
            logger.debug("节点跳过判断构造连接标识异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 转为 DatabaseManager 使用的键
     * - pg -> postgresql
     * - dm/dameng -> dm
     */
    private String toManagerKey(String type) {
        if (type == null) return "unknown";
        String t = type.toLowerCase(Locale.ROOT).trim();
        switch (t) {
            case "postgres":
            case "postgresql":
            case "pg":
                return "postgresql";
            case "dameng":
            case "dm":
                return "dm";
            case "oracle":
            case "mysql":
                return t;
            default:
                return "unknown";
        }
    }

    /**
     * 针对 available 的真实键名做匹配（规范化两侧键）
     */
    private String resolveAvailableKey(Map<String, List<String>> available, String targetManagerKey) {
        if (available.containsKey(targetManagerKey)) {
            return targetManagerKey;
        }
        for (String k : available.keySet()) {
            if (toManagerKey(k).equals(targetManagerKey)) {
                return k;
            }
        }
        return null;
    }

    /**
     * 根据执行策略决定节点选择：
     * - first -> 返回 null（由下游按“第一个节点”选择）
     * - standby/all/未配置 -> 返回 null
     */
    private String determineNodeRole(MetricConfig metric) {
        return null;
    }

    public void setFailedEncryptedHosts(Set<String> hosts) {
        this.failedEncryptedHosts = (hosts != null) ? new HashSet<>(hosts) : Collections.emptySet();
    }

    private void printStats(List<MetricResult> results) {
        if (results == null || results.isEmpty()) {
            logger.warn("未收集到任何指标结果");
            return;
        }

        long success = results.stream().filter(MetricResult::isSuccess).count();
        long failed = results.size() - success;

        Map<String, Long> byDb = new HashMap<>();
        for (MetricResult r : results) {
            String key = r.getDbType() != null ? r.getDbType() : "unknown";
            byDb.put(key, byDb.getOrDefault(key, 0L) + 1);
        }

        logger.info("=== 指标执行统计 ===");
        logger.info("总结果数: {}，成功: {}，失败: {}", results.size(), success, failed);
        logger.info("按数据库类型统计: {}", byDb);
    }

    /**
     * 关闭执行器及底层资源
     */
    public void shutdown() {
        logger.info("关闭并发指标执行器...");
        for (QueryExecutor qe : queryExecutors.values()) {
            try {
                qe.shutdown();
            } catch (Exception ignore) {
            }
        }
        queryExecutors.clear();
        try {
            databaseManager.shutdown();
        } catch (Exception ignore) {
        }
        logger.info("并发指标执行器已关闭");
    }
}