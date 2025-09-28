package com.dbcli.service;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.dbcli.database.ConnectionFactory;
import com.dbcli.util.DataMaskUtil;
import com.dbcli.util.EncryptionUtil;
import com.dbcli.util.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public class FastConnectionTestService {
    private static final Logger logger = LoggerFactory.getLogger(FastConnectionTestService.class);
    private static final String CACHE_FILE = "logs/connection_cache.properties";
    private static final String ERROR_FILE = "logs/db_conn_error.txt";
    private static final String BLACKLIST_FILE = "logs/db_conn_blacklist.txt";
    private static final java.time.format.DateTimeFormatter TS_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5分钟缓存

    private final ConnectionFactory connectionFactory;
    private final ExecutorService executor;
    private final Set<String> failedEncryptedHosts = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, CacheEntry> greyList = new ConcurrentHashMap<>();

    public FastConnectionTestService(ConnectionFactory connectionFactory) {
        this(connectionFactory, 10);
    }

    public FastConnectionTestService(ConnectionFactory connectionFactory, int threads) {
        this.connectionFactory = connectionFactory;
        int n = threads <= 0 ? 1 : threads;
        this.executor = Executors.newFixedThreadPool(n);
    }
    
    @Deprecated
    public boolean testConnections(Map<String, List<DatabaseConfig>> allConfigs) {
        logger.info("开始数据库连接测试...");
        failedEncryptedHosts.clear();
        
        Properties cache = loadCache();
        
        int totalConnections = 0;
        int successfulConnections = 0;
        int failedConnections = 0;
        
        List<Future<TestResult>> futures = new ArrayList<>();
        List<DatabaseConfig> submittedConfigs = new ArrayList<>();
        List<DatabaseNode> submittedNodes = new ArrayList<>();
        
        for (Map.Entry<String, List<DatabaseConfig>> entry : allConfigs.entrySet()) {
            String dbType = entry.getKey();
            List<DatabaseConfig> configs = entry.getValue();
            
            for (DatabaseConfig config : configs) {
                if (config.getNodes() != null) {
                    for (DatabaseNode node : config.getNodes()) {
                        totalConnections++;
                        Future<TestResult> future = executor.submit(() -> testSingleConnection(config, node, cache));
                        futures.add(future);
                        submittedConfigs.add(config);
                        submittedNodes.add(node);
                    }
                } else {
                    totalConnections++;
                    Future<TestResult> future = executor.submit(() -> testSingleConnection(config, null, cache));
                    futures.add(future);
                    submittedConfigs.add(config);
                    submittedNodes.add(null);
                }
            }
        }
        
        for (int i = 0; i < futures.size(); i++) {
            Future<TestResult> future = futures.get(i);
            DatabaseConfig cfg = submittedConfigs.get(i);
            DatabaseNode node = submittedNodes.get(i);
            try {
                TestResult result = future.get(15, TimeUnit.SECONDS);
                if (result.success) {
                    successfulConnections++;
                } else {
                    failedConnections++;
                    String hostForLog = (result.node != null && result.node.getHost() != null) ? result.node.getHost() : (cfg != null ? cfg.getHost() : "unknown");
                    LogManager.logConnectionFailure("UNKNOWN", "conn-test", hostForLog, result.error);
                }
            } catch (Exception e) {
                failedConnections++;
                logger.error("测试任务执行异常: {}", e.getMessage());
                DatabaseNode testNode = (node != null ? node : createDefaultNode(cfg));
                String hostForLog = (testNode != null && testNode.getHost() != null) ? testNode.getHost() : (cfg != null ? cfg.getHost() : "unknown");
                LogManager.logConnectionFailure("UNKNOWN", "conn-test", hostForLog, e.getMessage());
                try {
                    String normType = normalizeDbType(cfg != null ? cfg.getType() : null);
                    String encId = EncryptionUtil.encryptDeterministic(normType + "|" + connectionFactory.buildConnectionString(normType, cfg, testNode));
                    if (encId != null) {
                        failedEncryptedHosts.add(encId);
                        greyList.put(encId, new CacheEntry(true, System.currentTimeMillis()));
                    }
                } catch (Exception ex) {
                    logger.warn("写入灰名单失败: {}", ex.getMessage());
                }
            }
        }
        
        saveCache(cache);
        
        double successRate = totalConnections > 0 ? (double) successfulConnections / totalConnections * 100 : 0;
        
        logger.info("=== 数据库连接测试完成 ===");
        logger.info("总连接数: {}", totalConnections);
        logger.info("成功连接: {}", successfulConnections);
        logger.info("失败连接: {}", failedConnections);
        logger.info("成功率: {}%", String.format("%.1f", successRate));
        
        if (failedConnections > 0) {
            logger.warn("发现 {} 个连接失败，详情请查看: {}", failedConnections, ERROR_FILE);
            return false;
        }
        
        return true;
    }
    
    private TestResult testSingleConnection(DatabaseConfig config, DatabaseNode node, Properties cache) {
        String cacheKey = generateCacheKey(config, node);
        
        String cachedResult = cache.getProperty(cacheKey);
        if (cachedResult != null) {
            String[] parts = cachedResult.split("\\|");
            if (parts.length >= 2) {
                long timestamp = Long.parseLong(parts[1]);
                if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
                    boolean success = "SUCCESS".equals(parts[0]);
                    String maskedInfo = getMaskedInfo(config, node);
                    if (success) {
                        logger.info("✓ [缓存] {}", maskedInfo);
                    } else {
                        logger.warn("✗ [缓存] {}", maskedInfo);
                    }
                    return new TestResult(config, node, success, success ? null : "缓存的失败结果");
                }
            }
        }
        
        DatabaseNode testNode = (node != null ? node : createDefaultNode(config));
        String maskedInfo = getMaskedInfo(config, node);
        
        try {
            String systemName = "test-system";
            boolean success = connectionFactory.testConnection(systemName, testNode, config, config.getType());
            
            if (success) {
                logger.info("✓ {}", maskedInfo);
                cache.setProperty(cacheKey, "SUCCESS|" + System.currentTimeMillis());
                return new TestResult(config, node, true, null);
            } else {
                logger.warn("✗ {} - 连接测试失败", maskedInfo);
                cache.setProperty(cacheKey, "FAILED|" + System.currentTimeMillis());
                String normType = normalizeDbType(config.getType());
                String encId = EncryptionUtil.encryptDeterministic(normType + "|" + connectionFactory.buildConnectionString(normType, config, testNode));
                if (encId != null) {
                    failedEncryptedHosts.add(encId);
                    greyList.put(encId, new CacheEntry(true, System.currentTimeMillis()));
                }
                return new TestResult(config, node, false, "连接测试失败");
            }
        } catch (Exception e) {
            logger.warn("✗ {} - {}", maskedInfo, e.getMessage());
            cache.setProperty(cacheKey, "FAILED|" + System.currentTimeMillis());
            String normType = normalizeDbType(config.getType());
            String encId = EncryptionUtil.encryptDeterministic(normType + "|" + connectionFactory.buildConnectionString(normType, config, testNode));
            if (encId != null) {
                failedEncryptedHosts.add(encId);
                greyList.put(encId, new CacheEntry(true, System.currentTimeMillis()));
            }
            return new TestResult(config, node, false, e.getMessage());
        }
    }
    
    private DatabaseNode createDefaultNode(DatabaseConfig config) {
        DatabaseNode node = new DatabaseNode();
        node.setHost(config.getHost());
        node.setPort(config.getPort());
        node.setSvcName("default");
        return node;
    }
    
    private String getMaskedInfo(DatabaseConfig config, DatabaseNode node) {
        String host = node != null ? node.getHost() : config.getHost();
        Integer nodePort = (node != null ? node.getPort() : null);
        Integer configPort = config.getPort();
        int port = (nodePort != null ? nodePort.intValue() : (configPort != null ? configPort.intValue() : 0));
        String systemName = "系统";
        String database = (node != null && node.getSvcName() != null) ? node.getSvcName() : "默认数据库";

        return String.format("%s:%d/%s [%s]",
            DataMaskUtil.maskHostname(host), port,
            DataMaskUtil.maskDatabaseName(database), systemName);
    }
    
    private String generateCacheKey(DatabaseConfig config, DatabaseNode node) {
        String host = node != null ? node.getHost() : config.getHost();
        Integer nodePort = (node != null ? node.getPort() : null);
        Integer configPort = config.getPort();
        int port = (nodePort != null ? nodePort.intValue() : (configPort != null ? configPort.intValue() : 0));
        String database = (node != null && node.getSvcName() != null) ? node.getSvcName() : "default";

        return String.format("%s_%s_%d_%s_%s",
            config.getType(), host, port, database, config.getUsername());
    }
    
    private Properties loadCache() {
        Properties cache = new Properties();
        Path cacheFile = Paths.get(CACHE_FILE);
        
        if (Files.exists(cacheFile)) {
            try (InputStream is = Files.newInputStream(cacheFile)) {
                cache.load(is);
                logger.debug("加载连接缓存: {} 条记录", cache.size());
            } catch (IOException e) {
                logger.warn("加载连接缓存失败: {}", e.getMessage());
            }
        }
        
        return cache;
    }
    
    private void saveCache(Properties cache) {
        try {
            Path cacheFile = Paths.get(CACHE_FILE);
            Files.createDirectories(cacheFile.getParent());
            
            try (OutputStream os = Files.newOutputStream(cacheFile)) {
                cache.store(os, "Database Connection Cache - " + new Date());
                logger.debug("保存连接缓存: {} 条记录", cache.size());
            }
        } catch (IOException e) {
            logger.warn("保存连接缓存失败: {}", e.getMessage());
        }
    }
    
    private String normalizeDbType(String dbType) {
        if (dbType == null) return "unknown";
        String t = dbType.toLowerCase().trim();
        switch (t) {
            case "pg":
            case "postgres":
            case "postgresql":
                return "postgresql";
            case "dameng":
            case "dm":
                return "dm";
            case "oracle":
            case "mysql":
                return t;
            default:
                return t;
        }
    }
    
    @Deprecated
    public Set<String> getFailedConnections() {
        Set<String> failedConnections = new HashSet<>();
        Path errorFile = Paths.get(ERROR_FILE);
        
        if (Files.exists(errorFile)) {
            try {
                List<String> lines = Files.readAllLines(errorFile);
                for (String line : lines) {
                    if (line.contains("] ") && line.contains(" - ")) {
                        String connectionInfo = line.substring(line.indexOf("] ") + 2, line.lastIndexOf(" - "));
                        failedConnections.add(connectionInfo.trim());
                    }
                }
                logger.debug("加载失败连接列表: {} 个", failedConnections.size());
            } catch (IOException e) {
                logger.warn("读取错误文件失败: {}", e.getMessage());
            }
        }
        
        return failedConnections;
    }
    
    public boolean shouldSkipConnection(DatabaseConfig config, DatabaseNode node) {
        DatabaseNode testNode = node != null ? node : createDefaultNode(config);
        String normType = normalizeDbType(config.getType());
        String id = normType + "|" + connectionFactory.buildConnectionString(normType, config, testNode);
        String enc = EncryptionUtil.encryptDeterministic(id);
        if (enc == null) {
            return false;
        }
        CacheEntry entry = greyList.get(enc);
        if (entry != null) {
            // 检查是否过期（15分钟）
            if (System.currentTimeMillis() - entry.timestamp < 15 * 60 * 1000) {
                return entry.value;
            } else {
                // 过期则移除
                greyList.remove(enc);
            }
        }
        return false;
    }
    
    public Set<String> getFailedEncryptedHosts() {
        return new HashSet<>(failedEncryptedHosts);
    }
    
    public boolean testConnectionsWithNames(Map<String, Map<String, DatabaseConfig>> namedConfigs) {
        logger.info("开始带名称的数据库连接测试...");
        LogManager.setOperation("connection_test");
        failedEncryptedHosts.clear();

        List<Future<Boolean>> futures = new ArrayList<>();
        List<String> systems = new ArrayList<>();
        List<String> dbTypes = new ArrayList<>();
        List<DatabaseConfig> cfgs = new ArrayList<>();
        List<DatabaseNode> nodes = new ArrayList<>();

        // 构建测试任务（保留系统名，跳过 enable=false）
        for (Map.Entry<String, Map<String, DatabaseConfig>> entry : namedConfigs.entrySet()) {
            String dbTypeNorm = normalizeDbType(entry.getKey());
            for (Map.Entry<String, DatabaseConfig> e2 : entry.getValue().entrySet()) {
                String systemName = e2.getKey();
                DatabaseConfig cfg = e2.getValue();

                if (cfg == null) continue;
                if (!cfg.isEnable()) {
                    logger.info("跳过[enable=false]: {}", systemName);
                    continue;
                }

                if (cfg.getNodes() != null && !cfg.getNodes().isEmpty()) {
                    for (DatabaseNode n : cfg.getNodes()) {
                        DatabaseNode testNode = (n != null ? n : createDefaultNode(cfg));
                        futures.add(executor.submit(() -> connectionFactory.testConnection(systemName, testNode, cfg, dbTypeNorm)));
                        systems.add(systemName);
                        dbTypes.add(dbTypeNorm);
                        cfgs.add(cfg);
                        nodes.add(testNode);
                    }
                } else {
                    DatabaseNode testNode = createDefaultNode(cfg);
                    futures.add(executor.submit(() -> connectionFactory.testConnection(systemName, testNode, cfg, dbTypeNorm)));
                    systems.add(systemName);
                    dbTypes.add(dbTypeNorm);
                    cfgs.add(cfg);
                    nodes.add(testNode);
                }
            }
        }

        int total = futures.size();
        int ok = 0, fail = 0;
        // 去重集合：错误清单键(system|maskedJdbc) 与 黑名单密文
        Set<String> errKeys = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> encIds = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

        for (int i = 0; i < futures.size(); i++) {
            Future<Boolean> f = futures.get(i);
            String system = systems.get(i);
            String dbType = dbTypes.get(i);
            DatabaseConfig cfg = cfgs.get(i);
            DatabaseNode node = nodes.get(i);

            String jdbc = connectionFactory.buildConnectionString(dbType, cfg, node);
            String maskedJdbc = DataMaskUtil.maskJdbcUrl(jdbc);

            boolean success = false;
            try {
                success = f.get(15, TimeUnit.SECONDS);
            } catch (Exception ex) {
                success = false;
                logger.debug("连接测试任务异常 [{}-{}]: {}", system, node != null ? node.getHost() : "unknown", ex.getMessage());
            }

            if (success) {
                ok++;
                LogManager.setDbContext(dbType, system, null);
                try {
                    logger.info("✓ {} [{}]", maskedJdbc, system);
                } finally {
                    LogManager.clearDbContext();
                }
            } else {
                fail++;
                LogManager.setDbContext(dbType, system, null);
                try {
                    logger.warn("✗ {} [{}]", maskedJdbc, system);
                } finally {
                    LogManager.clearDbContext();
                }

                // 失败清单：system|maskedJdbc 去重
                errKeys.add(system + "|" + maskedJdbc);

                // 黑名单：dbType|jdbc → 确定性加密 ENC_D(...)
                String id = dbType + "|" + jdbc;
                String enc = EncryptionUtil.encryptDeterministic(id);
                if (enc != null) {
                    failedEncryptedHosts.add(enc);
                    encIds.add(enc);
                    greyList.put(enc, new CacheEntry(true, System.currentTimeMillis()));
                }
            }
        }

        // 持久化失败清单与黑名单（去重追加）
        persistErrorAndBlacklist(errKeys, encIds);

        double rate = total > 0 ? (ok * 100.0 / total) : 0.0;
        logger.info("=== 数据库连接测试完成 ===");
        logger.info("总连接数: {}", total);
        logger.info("成功连接: {}", ok);
        logger.info("失败连接: {}", fail);
        logger.info("成功率: {}%", String.format("%.1f", rate));
        if (fail > 0) {
            logger.warn("发现 {} 个连接失败，详情请查看: {}", fail, ERROR_FILE);
        }
        LogManager.clearOperation();
        return fail == 0;
    }

    private void persistErrorAndBlacklist(Set<String> errorKeys, Set<String> encIds) {
        try {
            // 确保目录存在
            java.nio.file.Path errPath = java.nio.file.Paths.get(ERROR_FILE);
            java.nio.file.Path blPath = java.nio.file.Paths.get(BLACKLIST_FILE);
            java.nio.file.Files.createDirectories(errPath.getParent());
            java.nio.file.Files.createDirectories(blPath.getParent());

            // 读取已存在错误清单，建立去重键（system|maskedJdbc）
            java.util.Set<String> existingErrKeys = new java.util.HashSet<>();
            if (java.nio.file.Files.exists(errPath)) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(errPath);
                for (String line : lines) {
                    // 期望格式：yyyy-MM-dd HH:mm:ss|system|maskedJdbc
                    int p1 = line.indexOf('|');
                    if (p1 > 0) {
                        int p2 = line.indexOf('|', p1 + 1);
                        if (p2 > p1) {
                            String key = line.substring(p1 + 1); // system|maskedJdbc
                            existingErrKeys.add(key);
                        }
                    }
                }
            }

            // 过滤新错误键与拼接输出行
            java.util.List<String> toAppendErr = new java.util.ArrayList<>();
            String now = java.time.LocalDateTime.now().format(TS_FMT);
            for (String k : errorKeys) {
                if (!existingErrKeys.contains(k)) {
                    toAppendErr.add(now + "|" + k);
                }
            }
            if (!toAppendErr.isEmpty()) {
                java.nio.file.Files.write(
                        errPath,
                        toAppendErr,
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND
                );
            }

            // 读取已存在黑名单密文，去重
            java.util.Set<String> existingBl = new java.util.HashSet<>();
            if (java.nio.file.Files.exists(blPath)) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(blPath);
                for (String line : lines) {
                    String v = line == null ? "" : line.trim();
                    if (!v.isEmpty()) existingBl.add(v);
                }
            }

            java.util.List<String> toAppendBl = new java.util.ArrayList<>();
            for (String enc : encIds) {
                if (!existingBl.contains(enc)) {
                    toAppendBl.add(enc);
                }
            }
            if (!toAppendBl.isEmpty()) {
                java.nio.file.Files.write(
                        blPath,
                        toAppendBl,
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND
                );
            }
        } catch (Exception e) {
            logger.warn("保存失败清单/黑名单失败: {}", e.getMessage());
        }
    }

    public void shutdown() {
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
    
    // 替代 Guava Cache 的简单缓存条目类
    private static class CacheEntry {
        final boolean value;
        final long timestamp;
        
        CacheEntry(boolean value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
    
    private static class TestResult {
        final DatabaseConfig config;
        final DatabaseNode node;
        final boolean success;
        final String error;
        
        TestResult(DatabaseConfig config, DatabaseNode node, boolean success, String error) {
            this.config = config;
            this.node = node;
            this.success = success;
            this.error = error;
        }
    }
}