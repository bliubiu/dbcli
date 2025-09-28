package com.dbcli.database;

import com.dbcli.config.ConfigLoader;
import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.dbcli.service.EncryptionService;
import com.dbcli.util.DataMaskUtil;
import com.dbcli.util.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接管理器
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final ConnectionFactory connectionFactory;
    private final EncryptionService encryptionService;
    private final Map<String, Map<String, DatabaseConfig>> databaseSystems = new ConcurrentHashMap<>();
    private final Set<String> failedConnections = ConcurrentHashMap.newKeySet();

    public DatabaseManager() {
        this.connectionFactory = new ConnectionFactory();
        this.encryptionService = new EncryptionService();
    }

    /**
     * 加载数据库配置
     */
    public void loadConfigurations(String configPath) {
        File configDir = new File(configPath);
        if (!configDir.exists() || !configDir.isDirectory()) {
            logger.warn("配置目录不存在: {}", configPath);
            return;
        }

        File[] configFiles = configDir.listFiles((dir, name) ->
                name.endsWith("-config.yaml") || name.endsWith("-config.yml"));

        if (configFiles == null || configFiles.length == 0) {
            logger.warn("未找到数据库配置文件");
            return;
        }

        for (File configFile : configFiles) {
            String dbType = extractDbType(configFile.getName());
            loadConfigFile(configFile, dbType);
        }

        logger.info("已加载 {} 种数据库类型的配置", databaseSystems.size());
    }

    /**
     * 提取数据库类型
     */
    private String extractDbType(String filename) {
        if (filename.startsWith("oracle-")) return "oracle";
        if (filename.startsWith("ora-")) return "oracle";
        if (filename.startsWith("mysql-")) return "mysql";
        if (filename.startsWith("pg-")) return "postgresql";
        if (filename.startsWith("postgresql-")) return "postgresql";
        if (filename.startsWith("dm-")) return "dm";
        return "unknown";
    }

    /**
     * 加载单个配置文件
     */
    @SuppressWarnings("unchecked")
    private void loadConfigFile(File configFile, String dbType) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> config;

            try (FileInputStream fis = new FileInputStream(configFile)) {
                config = yaml.load(fis);
            }

            Map<String, DatabaseConfig> systemConfigs = new HashMap<>();

            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String systemName = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Map) {
                    Map<String, Object> systemConfigMap = (Map<String, Object>) value;
                    DatabaseConfig dbConfig = parseDatabaseConfig(systemConfigMap);

                    if (dbConfig != null && dbConfig.isEnable()) {
                        systemConfigs.put(systemName, dbConfig);
                        LogManager.setDbContext(dbType, systemName, null);
                        try {
                            logger.info("加载数据库系统配置: {} (类型: {}, 节点数: {})",
                                    systemName, dbType, dbConfig.getNodes().size());
                        } finally {
                            LogManager.clearDbContext();
                        }
                    }
                }
            }

            if (!systemConfigs.isEmpty()) {
                databaseSystems.put(dbType, systemConfigs);
            }

        } catch (Exception e) {
            logger.error("加载配置文件失败: {}", configFile.getName(), e);
        }
    }

    /**
     * 解析数据库配置
     */
    @SuppressWarnings("unchecked")
    private DatabaseConfig parseDatabaseConfig(Map<String, Object> configMap) {
        DatabaseConfig config = new DatabaseConfig();

        config.setEnable((Boolean) configMap.getOrDefault("enable", false));
        config.setPort(asInteger(configMap.get("port"), 0));

        // 解密用户名和密码（兼容是否带 ENC(...)）
        String username = (String) configMap.get("username");
        String password = (String) configMap.get("password");

        if (username != null) {
            username = encryptionService.decrypt(username);
        }
        if (password != null) {
            password = encryptionService.decrypt(password);
        }

        config.setUsername(username);
        config.setPassword(password);

        // 解析节点配置
        Object nodesObj = configMap.get("nodes");
        if (nodesObj instanceof List) {
            List<Map<String, Object>> nodesList = (List<Map<String, Object>>) nodesObj;
            List<DatabaseNode> nodes = new ArrayList<>();

            for (Map<String, Object> nodeMap : nodesList) {
                DatabaseNode node = new DatabaseNode();

                // 解密主机地址（兼容 ENC(...)）
                String host = (String) nodeMap.get("host");
                if (host != null) {
                    host = encryptionService.decrypt(host);
                }
                node.setHost(host);

                node.setSvcName((String) nodeMap.get("svc_name"));
                node.setSidName((String) nodeMap.get("sid_name"));
                node.setPort(asInteger(nodeMap.get("port"), null));
                String roleRaw = (String) nodeMap.get("role");
                if (roleRaw != null) {
                    String r = roleRaw.toLowerCase();
                    if ("primary".equals(r) || "master".equals(r)) {
                        node.setRole("master");
                    } else if (r.contains("standby") || "slave".equals(r) || "physical_standby".equals(r)) {
                        node.setRole("standby");
                    } else {
                        logger.warn("无效的节点role值: {}，仅支持 master/standby，已置空", roleRaw);
                        node.setRole(null);
                    }
                } else {
                    node.setRole(null);
                }
                nodes.add(node);
            }

            config.setNodes(nodes);
        }

        return config;
    }

    /**
     * 获取数据库连接（按角色选择节点）
     */
    public Connection getConnection(String dbType, String systemName, String nodeRole) throws SQLException {
        Map<String, DatabaseConfig> systems = databaseSystems.get(dbType);
        if (systems == null) {
            throw new SQLException("未找到数据库类型配置: " + dbType);
        }

        DatabaseConfig config = systems.get(systemName);
        if (config == null) {
            throw new SQLException("未找到数据库系统配置: " + systemName);
        }

        // 根据角色选择节点
        DatabaseNode targetNode = selectNode(config.getNodes(), nodeRole);
        if (targetNode == null) {
            throw new SQLException("未找到匹配角色的节点: " + nodeRole);
        }

        String connectionKey = buildConnectionKey(dbType, systemName, targetNode);

        // 检查是否在失败列表中
        if (failedConnections.contains(connectionKey)) {
            throw new SQLException("节点连接已标记为失败: " + connectionKey);
        }

        try {
            return connectionFactory.getConnection(systemName, targetNode, config, dbType);
        } catch (SQLException e) {
            // 标记连接失败
            failedConnections.add(connectionKey);
            logger.warn("连接失败，已加入跳过列表: {}", connectionKey);
            throw e;
        }
    }

    /**
     * 获取数据库连接（指定节点）
     */
    public Connection getConnectionForNode(String dbType, String systemName, DatabaseNode node) throws SQLException {
        Map<String, DatabaseConfig> systems = databaseSystems.get(dbType);
        if (systems == null) {
            throw new SQLException("未找到数据库类型配置: " + dbType);
        }

        DatabaseConfig config = systems.get(systemName);
        if (config == null) {
            throw new SQLException("未找到数据库系统配置: " + systemName);
        }

        if (node == null) {
            throw new SQLException("节点不能为空");
        }

        String connectionKey = buildConnectionKey(dbType, systemName, node);

        if (failedConnections.contains(connectionKey)) {
            throw new SQLException("节点连接已标记为失败: " + connectionKey);
        }

        try {
            return connectionFactory.getConnection(systemName, node, config, dbType);
        } catch (SQLException e) {
            failedConnections.add(connectionKey);
            logger.warn("连接失败，已加入跳过列表: {}", connectionKey);
            throw e;
        }
    }

    /**
     * 根据角色选择节点
     */
    private DatabaseNode selectNode(List<DatabaseNode> nodes, String nodeRole) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        // 如果指定了角色，优先选择匹配角色的节点
        if (nodeRole != null && !nodeRole.isEmpty()) {
            for (DatabaseNode node : nodes) {
                if (nodeRole.equalsIgnoreCase(node.getRole())) {
                    return node;
                }
            }
        }

        // 如果没有找到匹配角色的节点，返回第一个节点
        return nodes.get(0);
    }

    /**
     * 按角色筛选节点（用于 fan-out）
     */
    public List<DatabaseNode> getNodesByRole(String dbType, String systemName, String role) {
        List<DatabaseNode> list = getNodes(dbType, systemName);
        if (role == null || role.trim().isEmpty()) {
            return list;
        }
        List<DatabaseNode> matched = new ArrayList<>();
        for (DatabaseNode n : list) {
            if (n.getRole() != null && role.equalsIgnoreCase(n.getRole())) {
                matched.add(n);
            }
        }
        return matched;
    }

    /**
     * 获取所有可用的数据库系统
     */
    public Map<String, List<String>> getAvailableSystems() {
        Map<String, List<String>> result = new HashMap<>();

        for (Map.Entry<String, Map<String, DatabaseConfig>> entry : databaseSystems.entrySet()) {
            String dbType = entry.getKey();
            List<String> systemNames = new ArrayList<>(entry.getValue().keySet());
            result.put(dbType, systemNames);
        }

        return result;
    }

    /**
     * 测试所有数据库连接 - 已移除，使用 FastConnectionTestService 代替
     */
    @Deprecated
    public void testAllConnections() {
        logger.info("连接测试功能已移至 FastConnectionTestService，请使用专门的连接测试服务");
    }

    /**
     * 构建连接键
     */
    private String buildConnectionKey(String dbType, String systemName, DatabaseNode node) {
        return String.format("%s-%s-%s-%s", dbType, systemName, node.getHost(), node.getSvcName());
    }

    /**
     * 获取失败连接列表
     */
    public Set<String> getFailedConnections() {
        return new HashSet<>(failedConnections);
    }

    /**
     * 检查节点是否在黑名单中
     */
    public boolean isNodeBlacklisted(String nodeKey) {
        return failedConnections.contains(nodeKey);
    }

    /**
     * 清除失败连接标记
     */
    public void clearFailedConnections() {
        failedConnections.clear();
        logger.info("已清除失败连接标记");
    }

    /**
     * 打印连接统计信息
     */
    public void printConnectionStats() {
        logger.info("=== 数据库连接统计 ===");

        for (Map.Entry<String, Map<String, DatabaseConfig>> dbTypeEntry : databaseSystems.entrySet()) {
            String dbType = dbTypeEntry.getKey();
            Map<String, DatabaseConfig> systems = dbTypeEntry.getValue();

            int totalNodes = systems.values().stream()
                    .mapToInt(config -> config.getNodes().size())
                    .sum();

            logger.info("数据库类型: {} - 系统数: {}, 节点数: {}", dbType, systems.size(), totalNodes);

            for (Map.Entry<String, DatabaseConfig> systemEntry : systems.entrySet()) {
                String systemName = systemEntry.getKey();
                DatabaseConfig config = systemEntry.getValue();

                logger.info("  系统: {} - 节点数: {}", systemName, config.getNodes().size());

                for (DatabaseNode node : config.getNodes()) {
                    String maskedHost = DataMaskUtil.maskIpAddress(node.getHost());
                    logger.info("    节点: {} - {} ({})", maskedHost, node.getSvcName(), node.getRole());
                }
            }
        }

        if (!failedConnections.isEmpty()) {
            logger.warn("失败连接数: {}", failedConnections.size());
        }

        // 打印连接池统计
        connectionFactory.printDataSourceStats();
    }

    /**
     * 初始化数据库管理器（内存模式）
     */
    public void initialize(Map<String, DatabaseConfig> databaseConfigs) {
        logger.info("初始化数据库管理器，配置数量: {}", databaseConfigs.size());

        // 按数据库类型分组配置
        for (Map.Entry<String, DatabaseConfig> entry : databaseConfigs.entrySet()) {
            String configName = entry.getKey();
            DatabaseConfig config = entry.getValue();
            String dbType = config.getType();

            if (dbType == null) {
                logger.warn("配置 {} 缺少数据库类型信息，跳过", configName);
                continue;
            }

            databaseSystems.computeIfAbsent(dbType, k -> new HashMap<>()).put(configName, config);
            logger.debug("添加数据库配置: {} (类型: {})", configName, dbType);
        }

        logger.info("数据库管理器初始化完成，支持 {} 种数据库类型", databaseSystems.size());
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        logger.info("清理数据库连接管理器资源...");
        connectionFactory.closeAll();
        databaseSystems.clear();
        failedConnections.clear();
        logger.info("数据库连接管理器资源清理完成");
    }

    /**
     * 关闭所有连接
     */
    public void shutdown() {
        logger.info("关闭数据库连接管理器...");
        connectionFactory.closeAll();
        databaseSystems.clear();
        failedConnections.clear();
        logger.info("数据库连接管理器已关闭");
    }

    /**
     * 获取连接工厂
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * 获取指定系统的节点列表
     */
    public java.util.List<DatabaseNode> getNodes(String dbType, String systemName) {
        Map<String, DatabaseConfig> systems = databaseSystems.get(dbType);
        if (systems == null) return java.util.Collections.emptyList();
        DatabaseConfig cfg = systems.get(systemName);
        if (cfg == null || cfg.getNodes() == null) return java.util.Collections.emptyList();
        return cfg.getNodes();
    }

    /**
     * 获取指定系统配置
     */
    public DatabaseConfig getConfig(String dbType, String systemName) {
        Map<String, DatabaseConfig> systems = databaseSystems.get(dbType);
        if (systems == null) return null;
        return systems.get(systemName);
    }

    private Integer asInteger(Object v, Integer defVal) {
        if (v == null) return defVal;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return defVal;
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defVal; }
        }
        return defVal;
    }
}
