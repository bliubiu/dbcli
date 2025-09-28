package com.dbcli.service;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.util.EncryptionUtil;
import com.dbcli.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

// 新增导入，确保UTF-8写回，避免中文键名乱码
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 配置文件加密服务
 */
public class EncryptionService {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);
    
    /**
     * 解密文本
     */
    public String decrypt(String encryptedText) {
        return EncryptionUtil.decrypt(encryptedText);
    }

    /**
     * 加密文本
     */
    public String encrypt(String plainText) {
        return EncryptionUtil.encrypt(plainText);
    }

    public void encryptConfigs(String configPath) throws IOException {
        File configDir = new File(configPath);
        if (!configDir.exists() || !configDir.isDirectory()) {
            logger.warn("配置目录不存在: {}", configPath);
            return;
        }
        
        File[] configFiles = configDir.listFiles((dir, name) -> 
            name.endsWith("-config.yaml") || name.endsWith("-config.yml"));
        
        if (configFiles == null || configFiles.length == 0) {
            logger.warn("未找到配置文件");
            return;
        }
        
        for (File configFile : configFiles) {
            encryptConfigFile(configFile);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void encryptConfigFile(File configFile) throws IOException {
        logger.info("加密配置文件: {}", configFile.getName());
        
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        
        try (FileInputStream fis = new FileInputStream(configFile)) {
            config = yaml.load(fis);
        }
        
        if (config == null || config.isEmpty()) {
            logger.warn("配置文件为空: {}", configFile.getName());
        } else {
            // 遍历配置并加密敏感信息
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map) {
                    Map<String, Object> dbConfig = (Map<String, Object>) value;
                    encryptDatabaseConfig(dbConfig);
                } else if (value instanceof List) {
                    List<Object> list = (List<Object>) value;
                    for (Object item : list) {
                        if (item instanceof Map) {
                            encryptDatabaseConfig((Map<String, Object>) item);
                        }
                    }
                }
            }
        }
        
        // 写回文件（UTF-8）
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            yaml.dump(config, writer);
        }
        
        logger.info("配置文件加密完成: {}", configFile.getName());
    }
    
    @SuppressWarnings("unchecked")
    private void encryptDatabaseConfig(Map<String, Object> dbConfig) {
        // 加密用户名
        Object username = dbConfig.get("username");
        if (username instanceof String && !EncryptionUtil.isEncrypted((String) username)) {
            dbConfig.put("username", EncryptionUtil.encrypt((String) username));
        }
        
        // 加密密码
        Object password = dbConfig.get("password");
        if (password instanceof String && !EncryptionUtil.isEncrypted((String) password)) {
            dbConfig.put("password", EncryptionUtil.encrypt((String) password));
        }
        
        // 加密节点配置中的敏感信息
        Object nodes = dbConfig.get("nodes");
        if (nodes instanceof java.util.List) {
            java.util.List<Object> nodesList = (java.util.List<Object>) nodes;
            for (Object nodeObj : nodesList) {
                if (nodeObj instanceof Map) {
                    Map<String, Object> node = (Map<String, Object>) nodeObj;
                    
                    // 加密主机地址
                    Object host = node.get("host");
                    if (host instanceof String && !EncryptionUtil.isEncrypted((String) host)) {
                        node.put("host", EncryptionUtil.encrypt((String) host));
                    }
                }
            }
        }
    }
}
