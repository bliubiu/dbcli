package com.dbcli.config;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.dbcli.model.MetricConfig;
import com.dbcli.service.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import com.dbcli.util.LogManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置加载器
 * 负责加载数据库配置和指标配置
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private final EncryptionService encryptionService;
    
    public ConfigLoader(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }
    
    /**
     * 加载数据库配置
     */
    public Map<String, DatabaseConfig> loadDatabaseConfigs(String configPath) {
        Map<String, DatabaseConfig> configs = new HashMap<>();
        
        try {
            Path configDir = Paths.get(configPath);
            if (!Files.exists(configDir)) {
                logger.warn("配置目录不存在: {}", configPath);
                return configs;
            }
            
            Files.walk(configDir)
                    .filter(path -> {
                        String fn = path.getFileName().toString().toLowerCase();
                        return fn.endsWith("-config.yml") || fn.endsWith("-config.yaml");
                    })
                    .forEach(yamlFile -> {
                        try {
                            String filename = yamlFile.getFileName().toString();
                            String dbType = extractDbType(filename);
                            
                            Map<String, DatabaseConfig> fileConfigs = loadConfigFile(yamlFile.toFile(), dbType);
                            for (Map.Entry<String, DatabaseConfig> entry : fileConfigs.entrySet()) {
                                String systemName = entry.getKey();
                                DatabaseConfig config = entry.getValue();
                                config.setType(dbType);
                                
                                configs.put(systemName, config);
                                LogManager.setDbContext(dbType, systemName, null);
                                try {
                                    logger.info("加载数据库配置: {} (类型: {}, 节点数: {})", 
                                           systemName, dbType, config.getNodes() != null ? config.getNodes().size() : 0);
                                } finally {
                                    LogManager.clearDbContext();
                                }
                            }
                        } catch (Exception e) {
                            logger.error("加载配置文件失败: {}", yamlFile, e);
                        }
                    });
            
        } catch (IOException e) {
            logger.error("读取配置目录失败: {}", configPath, e);
        }
        
        return configs;
    }
    
    /**
     * 提取数据库类型
     */
    private String extractDbType(String filename) {
        if (filename.startsWith("oracle-")) return "oracle";
        if (filename.startsWith("ora-")) return "oracle";
        if (filename.startsWith("mysql-")) return "mysql";
        if (filename.startsWith("postgresql-")) return "postgresql";
        if (filename.startsWith("pg-")) return "postgresql";
        if (filename.startsWith("dm-")) return "dm";
        return "unknown";
    }
    
    /**
     * 加载单个配置文件
     */
    @SuppressWarnings("unchecked")
    private Map<String, DatabaseConfig> loadConfigFile(File configFile, String dbType) {
        Map<String, DatabaseConfig> systemConfigs = new HashMap<>();
        
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> config;
            
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config = yaml.load(fis);
            }
            
            if (config == null) {
                logger.warn("配置文件为空: {}", configFile.getName());
                return systemConfigs;
            }
            
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String systemName = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Map) {
                    Map<String, Object> systemConfigMap = (Map<String, Object>) value;
                    DatabaseConfig dbConfig = parseDatabaseConfig(systemConfigMap);
                    
                    if (dbConfig != null) {
                        systemConfigs.put(systemName, dbConfig);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("加载配置文件失败: {}", configFile.getName(), e);
        }
        
        return systemConfigs;
    }
    
    /**
     * 解析数据库配置
     */
    @SuppressWarnings("unchecked")
    private DatabaseConfig parseDatabaseConfig(Map<String, Object> configMap) {
        DatabaseConfig config = new DatabaseConfig();
        
        config.setEnable((Boolean) configMap.getOrDefault("enable", false));
        config.setPort((Integer) configMap.getOrDefault("port", 0));
        
        // 解密用户名和密码
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
                
                // 解密主机地址
                String host = (String) nodeMap.get("host");
                if (host != null) {
                    host = encryptionService.decrypt(host);
                }
                
                node.setHost(host);
                node.setSvcName((String) nodeMap.get("svc_name"));
                node.setSidName((String) nodeMap.get("sid_name"));
                node.setPort((Integer) nodeMap.get("port"));
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
     * 加载指标配置
     */
    public List<MetricConfig> loadMetricConfigs(String metricsPath) {
        try {
            Path metricsDir = Paths.get(metricsPath);
            if (!Files.exists(metricsDir)) {
                logger.warn("指标目录不存在: {}", metricsPath);
                return Collections.emptyList();
            }
            
            return Files.walk(metricsDir)
                    .filter(path -> path.toString().toLowerCase().endsWith(".yml") || 
                                  path.toString().toLowerCase().endsWith(".yaml"))
                    .flatMap(path -> {
                        try {
                            return loadMetricConfigsFromFile(path).stream();
                        } catch (IOException e) {
                            logger.error("加载指标配置文件失败: {}", path, e);
                            return Collections.<MetricConfig>emptyList().stream();
                        }
                    })
                    .collect(Collectors.toList());
                    
        } catch (IOException e) {
            logger.error("读取指标目录失败: {}", metricsPath, e);
            return Collections.emptyList();
        }
    }
    
    /**
    /**
     * 从单个指标配置文件加载指标配置 - 公共方法
     */
    public List<MetricConfig> loadMetricConfigsFromFile(Path filePath) throws IOException {
        logger.info("加载指标配置文件: {}", filePath);
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            Yaml yaml = new Yaml();
            Object yamlData = yaml.load(inputStream);
            
            if (yamlData == null) {
                logger.warn("指标配置文件为空: {}", filePath);
                return Collections.emptyList();
            }
            
            List<MetricConfig> configs = new ArrayList<>();
            
            if (yamlData instanceof List) {
                // 处理YAML数组格式（以 - 开头的列表）
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> metricsList = (List<Map<String, Object>>) yamlData;
                for (Map<String, Object> metricMap : metricsList) {
                    MetricConfig config = parseMetricConfig(metricMap);
                    if (config != null) {
                        configs.add(config);
                    }
                }
            } else if (yamlData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> yamlMap = (Map<String, Object>) yamlData;
                
                // 处理包含metrics数组的结构
                if (yamlMap.containsKey("metrics")) {
                    Object metricsObj = yamlMap.get("metrics");
                    if (metricsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> metricsList = (List<Map<String, Object>>) metricsObj;
                        for (Map<String, Object> metricMap : metricsList) {
                            MetricConfig config = parseMetricConfig(metricMap);
                            if (config != null) {
                                configs.add(config);
                            }
                        }
                    }
                } else {
                    // 直接处理单个指标配置
                    MetricConfig config = parseMetricConfig(yamlMap);
                    if (config != null) {
                        configs.add(config);
                    }
                }
            }
            
            logger.info("从文件 {} 加载了 {} 个指标配置", filePath, configs.size());
            return configs;
            
        } catch (Exception e) {
            logger.error("解析指标配置文件失败: {}", filePath, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 解析单个指标配置
     */
    @SuppressWarnings("unchecked")
    private MetricConfig parseMetricConfig(Map<String, Object> metricMap) {
        MetricConfig config = new MetricConfig();
        
        config.setType((String) metricMap.get("type"));
        config.setName((String) metricMap.get("name"));
        config.setDescription((String) metricMap.get("description"));
        config.setSql((String) metricMap.get("sql"));
        
        // 解析columns
        Object columnsObj = metricMap.get("columns");
        if (columnsObj instanceof List) {
            config.setColumns((List<String>) columnsObj);
        }
        
        // 解析execution_strategy
        Object executionStrategyObj = metricMap.get("execution_strategy");
        if (executionStrategyObj instanceof Map) {
            Map<String, Object> strategyMap = (Map<String, Object>) executionStrategyObj;
            MetricConfig.ExecutionStrategy strategy = new MetricConfig.ExecutionStrategy();

            // 仅支持 first/all/master/standby；mode=role 已废弃，按 first 处理
            String mode = (String) strategyMap.get("mode");
            String m = mode != null ? mode.toLowerCase() : null;
            if (m != null && "role".equalsIgnoreCase(m)) {
                logger.warn("execution_strategy.mode=role 已废弃，将按 first 处理");
                m = "first";
            }
            // 校验允许的取值集合
            if (m != null) {
                switch (m) {
                    case "first":
                    case "all":
                    case "master":
                    case "standby":
                        break;
                    default:
                        logger.warn("不支持的 execution_strategy.mode: {}，将按 first 处理", m);
                        m = "first";
                }
            }
            strategy.setMode(m);

            // 解析retry_policy
            Object retryPolicyObj = strategyMap.get("retry_policy");
            if (retryPolicyObj instanceof Map) {
                Map<String, Object> retryMap = (Map<String, Object>) retryPolicyObj;
                MetricConfig.RetryPolicy retryPolicy = new MetricConfig.RetryPolicy();
                retryPolicy.setEnabled((Boolean) retryMap.getOrDefault("enabled", false));
                retryPolicy.setMaxAttempts((Integer) retryMap.getOrDefault("max_attempts", 1));
                retryPolicy.setBackoffMs(((Number) retryMap.getOrDefault("backoff_ms", 0)).longValue());
                retryPolicy.setDelayMs(((Number) retryMap.getOrDefault("delay_ms", 0)).longValue());
                strategy.setRetryPolicy(retryPolicy);
            }

            config.setExecutionStrategy(strategy);
        }
        
        // 解析threshold
        Object thresholdObj = metricMap.get("threshold");
        if (thresholdObj instanceof Map) {
            Map<String, Object> thresholdMap = (Map<String, Object>) thresholdObj;
            MetricConfig.Threshold threshold = new MetricConfig.Threshold();
            threshold.setLevel((String) thresholdMap.get("level"));
            threshold.setOperator((String) thresholdMap.get("operator"));
            threshold.setValue(thresholdMap.get("value"));
            config.setThreshold(threshold);
        }
        
        return config;
    }
    
    /**
     * 验证配置的有效性
     */
    public boolean validateDatabaseConfig(DatabaseConfig config) {
        if (config == null) {
            return false;
        }
        
        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            logger.error("数据库节点配置不能为空");
            return false;
        }
        
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            logger.error("数据库用户名不能为空");
            return false;
        }
        
        if (config.getPassword() == null || config.getPassword().trim().isEmpty()) {
            logger.error("数据库密码不能为空");
            return false;
        }
        
        if (config.getType() == null || !isValidDatabaseType(config.getType())) {
            logger.error("不支持的数据库类型: {}", config.getType());
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否为支持的数据库类型
     */
    private boolean isValidDatabaseType(String dbType) {
        return "oracle".equalsIgnoreCase(dbType) ||
               "mysql".equalsIgnoreCase(dbType) ||
               "postgresql".equalsIgnoreCase(dbType) ||
               "dm".equalsIgnoreCase(dbType);
    }
}