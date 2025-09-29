package com.dbcli.storage;

import com.dbcli.util.SM4Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 存储配置加载器
 * 用于从YAML配置文件加载存储配置，并处理加密的配置项
 */
public class StorageConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(StorageConfigLoader.class);
    
    /**
     * 从YAML配置文件加载存储配置
     * @param configPath 配置文件路径
     * @return 存储配置对象
     */
    public static StorageConfig loadConfig(String configPath) {
        try {
            // 检查配置文件是否存在
            if (!Files.exists(Paths.get(configPath))) {
                logger.warn("存储配置文件不存在: {}, 使用默认配置", configPath);
                return createDefaultConfig();
            }
            
            // 读取YAML配置文件
            InputStream inputStream = Files.newInputStream(Paths.get(configPath));
            Yaml yaml = new Yaml();
            Map<String, Object> configMap = yaml.load(inputStream);
            
            // 解析配置
            return parseConfig(configMap);
        } catch (Exception e) {
            logger.error("加载存储配置文件失败: {}", e.getMessage(), e);
            return createDefaultConfig();
        }
    }
    
    /**
     * 解析配置映射
     * @param configMap 配置映射
     * @return 存储配置对象
     */
    @SuppressWarnings("unchecked")
    private static StorageConfig parseConfig(Map<String, Object> configMap) {
        StorageConfig config = new StorageConfig();
        
        if (configMap == null || configMap.get("storage") == null) {
            logger.warn("配置文件中未找到storage配置，使用默认配置");
            return config;
        }
        
        Map<String, Object> storageMap = (Map<String, Object>) configMap.get("storage");
        
        // 解析基本配置
        if (storageMap.get("enabled") != null) {
            config.setEnabled((Boolean) storageMap.get("enabled"));
        }
        
        if (storageMap.get("type") != null) {
            config.setType((String) storageMap.get("type"));
        }
        
        if (storageMap.get("batchMode") != null) {
            config.setBatchMode((Boolean) storageMap.get("batchMode"));
        }
        
        if (storageMap.get("batchSize") != null) {
            config.setBatchSize((Integer) storageMap.get("batchSize"));
        }
        
        // 解析PostgreSQL配置
        if (storageMap.get("postgresql") != null) {
            Map<String, Object> postgresqlMap = (Map<String, Object>) storageMap.get("postgresql");
            
            if (postgresqlMap.get("host") != null) {
                String host = (String) postgresqlMap.get("host");
                config.setHost(decryptIfEncrypted(host));
            }
            
            if (postgresqlMap.get("port") != null) {
                config.setPort((Integer) postgresqlMap.get("port"));
            }
            
            if (postgresqlMap.get("database") != null) {
                String database = (String) postgresqlMap.get("database");
                config.setDatabase(decryptIfEncrypted(database));
            }
            
            if (postgresqlMap.get("username") != null) {
                String username = (String) postgresqlMap.get("username");
                config.setUsername(decryptIfEncrypted(username));
            }
            
            if (postgresqlMap.get("password") != null) {
                String password = (String) postgresqlMap.get("password");
                config.setPassword(decryptIfEncrypted(password));
            }
        }
        
        return config;
    }
    
    /**
     * 如果字符串是加密的，则解密它
     * @param value 可能加密的值
     * @return 解密后的值或原始值
     */
    private static String decryptIfEncrypted(String value) {
        if (value != null && value.startsWith("ENC(") && value.endsWith(")")) {
            // 提取加密内容
            String encryptedContent = value.substring(4, value.length() - 1);
            // 解密
            String decrypted = SM4Util.decrypt(encryptedContent);
            if (decrypted != null) {
                return decrypted;
            } else {
                logger.warn("解密配置项失败: {}", value);
                return value; // 解密失败时返回原始值
            }
        }
        return value;
    }
    
    /**
     * 创建默认配置
     * @return 默认存储配置对象
     */
    private static StorageConfig createDefaultConfig() {
        StorageConfig config = new StorageConfig();
        config.setEnabled(false); // 默认不启用存储功能
        return config;
    }
}