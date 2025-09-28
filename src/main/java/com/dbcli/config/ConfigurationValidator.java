package com.dbcli.config;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import com.dbcli.model.MetricConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class ConfigurationValidator {
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList());
        }
        
        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
        
        public static ValidationResult failure(String error) {
            return new ValidationResult(false, Collections.singletonList(error));
        }
        
        public static ValidationResult withWarnings(List<String> warnings) {
            return new ValidationResult(true, Collections.emptyList(), warnings);
        }
    }
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);
    
    private static final Pattern HOST_PATTERN = Pattern.compile(
        "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$|^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"
    );
    
    private static final Set<String> SUPPORTED_DB_TYPES = Set.of(
        "mysql", "postgresql", "oracle", "dm", "dameng"
    );
    
    public boolean validateConfiguration(Map<String, List<DatabaseConfig>> configs) {
        if (configs == null || configs.isEmpty()) {
            logger.error("配置为空或null");
            return false;
        }
        
        boolean isValid = true;
        
        for (Map.Entry<String, List<DatabaseConfig>> entry : configs.entrySet()) {
            String dbType = entry.getKey();
            List<DatabaseConfig> configList = entry.getValue();
            
            if (!validateDbType(dbType)) {
                isValid = false;
                continue;
            }
            
            if (configList == null || configList.isEmpty()) {
                logger.warn("数据库类型 {} 的配置列表为空", dbType);
                continue;
            }
            
            for (DatabaseConfig config : configList) {
                if (!validateDatabaseConfig(config, dbType)) {
                    isValid = false;
                }
            }
        }
        
        return isValid;
    }
    
    public boolean validateDatabaseConfig(DatabaseConfig config, String dbType) {
        if (config == null) {
            logger.error("数据库配置为null");
            return false;
        }
        
        boolean isValid = true;
        
        // 验证基本字段
        if (isEmpty(config.getHost())) {
            logger.error("主机地址不能为空");
            isValid = false;
        } else if (!isValidHost(config.getHost())) {
            logger.error("主机地址格式无效: {}", config.getHost());
            isValid = false;
        }
        
        // 修复端口验证 - 使用Integer包装类型进行null检查
        if (!isValidPort(config.getPort())) {
            logger.error("端口号无效: {}", config.getPort());
            isValid = false;
        }
        
        if (isEmpty(config.getUsername())) {
            logger.error("用户名不能为空");
            isValid = false;
        }
        
        if (isEmpty(config.getPassword())) {
            logger.error("密码不能为空");
            isValid = false;
        }
        
        // 验证数据库特定字段
        if (!validateDbSpecificFields(config, dbType)) {
            isValid = false;
        }
        
        // 验证节点配置
        if (config.getNodes() != null) {
            for (DatabaseNode node : config.getNodes()) {
                if (!validateDatabaseNode(node)) {
                    isValid = false;
                }
            }
        }
        
        return isValid;
    }
    
    // 重载方法，兼容只传DatabaseConfig的调用
    public ValidationResult validateDatabaseConfig(DatabaseConfig config) {
        if (config == null) {
            return ValidationResult.failure("数据库配置为null");
        }
        
        List<String> errors = new ArrayList<>();
        
        // 验证基本字段
        if (isEmpty(config.getHost())) {
            errors.add("主机地址不能为空");
        } else if (!isValidHost(config.getHost())) {
            errors.add("主机地址格式无效: " + config.getHost());
        }
        
        if (!isValidPort(config.getPort())) {
            errors.add("端口号无效: " + config.getPort());
        }
        
        if (isEmpty(config.getUsername())) {
            errors.add("用户名不能为空");
        }
        
        if (isEmpty(config.getPassword())) {
            errors.add("密码不能为空");
        }
        
        // 验证节点配置
        if (config.getNodes() != null) {
            for (int i = 0; i < config.getNodes().size(); i++) {
                DatabaseNode node = config.getNodes().get(i);
                if (node == null) {
                    errors.add("节点" + i + "配置为null");
                    continue;
                }
                
                if (isEmpty(node.getHost())) {
                    errors.add("节点" + i + "主机地址不能为空");
                } else if (!isValidHost(node.getHost())) {
                    errors.add("节点" + i + "主机地址格式无效: " + node.getHost());
                }
                
                if (node.getPort() != null && !isValidPort(node.getPort())) {
                    errors.add("节点" + i + "端口号无效: " + node.getPort());
                }
            }
        }
        
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
    
    public boolean validateDatabaseNode(DatabaseNode node) {
        if (node == null) {
            logger.error("数据库节点配置为null");
            return false;
        }
        
        boolean isValid = true;
        
        if (isEmpty(node.getHost())) {
            logger.error("节点主机地址不能为空");
            isValid = false;
        } else if (!isValidHost(node.getHost())) {
            logger.error("节点主机地址格式无效: {}", node.getHost());
            isValid = false;
        }
        
        // 修复端口验证 - 使用Integer包装类型进行null检查
        if (node.getPort() != null && !isValidPort(node.getPort())) {
            logger.error("节点端口号无效: {}", node.getPort());
            isValid = false;
        }
        
        return isValid;
    }
    
    public boolean validateMetricConfig(MetricConfig metric) {
        if (metric == null) {
            logger.error("指标配置为null");
            return false;
        }
        
        boolean isValid = true;
        
        if (isEmpty(metric.getName())) {
            logger.error("指标名称不能为空");
            isValid = false;
        }
        
        if (isEmpty(metric.getSql())) {
            logger.error("指标SQL不能为空");
            isValid = false;
        }
        
        // 移除timeout验证，因为MetricConfig没有这个字段
        
        return isValid;
    }
    
    private boolean validateDbType(String dbType) {
        if (isEmpty(dbType)) {
            logger.error("数据库类型不能为空");
            return false;
        }
        
        if (!SUPPORTED_DB_TYPES.contains(dbType.toLowerCase())) {
            logger.error("不支持的数据库类型: {}", dbType);
            return false;
        }
        
        return true;
    }
    
    private boolean validateDbSpecificFields(DatabaseConfig config, String dbType) {
        // 简化数据库特定字段验证，因为DatabaseConfig没有相关字段
        String type = dbType.toLowerCase();
        
        switch (type) {
            case "oracle":
                // Oracle数据库的特定验证可以在这里添加
                break;
            case "mysql":
            case "postgresql":
                // MySQL和PostgreSQL的特定验证可以在这里添加
                break;
            case "dm":
            case "dameng":
                // 达梦数据库的特定验证
                break;
        }
        
        return true;
    }
    
    private boolean isValidHost(String host) {
        if (isEmpty(host)) {
            return false;
        }
        
        // 检查是否为localhost
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        
        // 使用正则表达式验证主机名或IP地址
        return HOST_PATTERN.matcher(host).matches();
    }
    
    private boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }
    
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    public List<String> getValidationErrors(Map<String, List<DatabaseConfig>> configs) {
        List<String> errors = new ArrayList<>();
        
        if (configs == null || configs.isEmpty()) {
            errors.add("配置为空或null");
            return errors;
        }
        
        for (Map.Entry<String, List<DatabaseConfig>> entry : configs.entrySet()) {
            String dbType = entry.getKey();
            List<DatabaseConfig> configList = entry.getValue();
            
            if (!validateDbType(dbType)) {
                errors.add("不支持的数据库类型: " + dbType);
                continue;
            }
            
            if (configList == null || configList.isEmpty()) {
                errors.add("数据库类型 " + dbType + " 的配置列表为空");
                continue;
            }
            
            for (int i = 0; i < configList.size(); i++) {
                DatabaseConfig config = configList.get(i);
                List<String> configErrors = getConfigValidationErrors(config, dbType, i);
                errors.addAll(configErrors);
            }
        }
        
        return errors;
    }
    
    private List<String> getConfigValidationErrors(DatabaseConfig config, String dbType, int index) {
        List<String> errors = new ArrayList<>();
        String prefix = String.format("[%s配置%d] ", dbType, index);
        
        if (config == null) {
            errors.add(prefix + "配置为null");
            return errors;
        }
        
        if (isEmpty(config.getHost())) {
            errors.add(prefix + "主机地址不能为空");
        } else if (!isValidHost(config.getHost())) {
            errors.add(prefix + "主机地址格式无效: " + config.getHost());
        }
        
        if (!isValidPort(config.getPort())) {
            errors.add(prefix + "端口号无效: " + config.getPort());
        }
        
        if (isEmpty(config.getUsername())) {
            errors.add(prefix + "用户名不能为空");
        }
        
        if (isEmpty(config.getPassword())) {
            errors.add(prefix + "密码不能为空");
        }
        
        // 验证节点配置
        if (config.getNodes() != null) {
            for (int j = 0; j < config.getNodes().size(); j++) {
                DatabaseNode node = config.getNodes().get(j);
                List<String> nodeErrors = getNodeValidationErrors(node, prefix + "节点" + j + " ");
                errors.addAll(nodeErrors);
            }
        }
        
        return errors;
    }
    
    private List<String> getNodeValidationErrors(DatabaseNode node, String prefix) {
        List<String> errors = new ArrayList<>();
        
        if (node == null) {
            errors.add(prefix + "配置为null");
            return errors;
        }
        
        if (isEmpty(node.getHost())) {
            errors.add(prefix + "主机地址不能为空");
        } else if (!isValidHost(node.getHost())) {
            errors.add(prefix + "主机地址格式无效: " + node.getHost());
        }
        
        if (node.getPort() != null && !isValidPort(node.getPort())) {
            errors.add(prefix + "端口号无效: " + node.getPort());
        }
        
        return errors;
    }
}