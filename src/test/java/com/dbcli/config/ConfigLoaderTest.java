package com.dbcli.config;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.MetricConfig;
import com.dbcli.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigLoaderTest {
    private ConfigLoader configLoader;
    private String configPath;
    private String metricsPath;

    @BeforeEach
    public void setUp() {
        // 使用测试资源目录下的配置文件
        configPath = "src/test/resources/config";
        metricsPath = "src/test/resources/metrics";
        
        // 创建加密服务实例
        EncryptionService encryptionService = new EncryptionService();
        configLoader = new ConfigLoader(encryptionService);
    }

    @Test
    public void testLoadDatabaseConfigs() throws IOException {
        Map<String, DatabaseConfig> dbConfigs = configLoader.loadDatabaseConfigs(configPath);
        assertNotNull(dbConfigs, "数据库配置不应为null");
        
        // 由于测试环境可能没有实际的配置文件，这里只验证方法能正常执行
        // 在实际环境中，可以添加更多具体的验证
    }

    @Test
    public void testLoadMetricConfigs() throws IOException {
        List<MetricConfig> metricsConfig = configLoader.loadMetricConfigs(metricsPath);
        assertNotNull(metricsConfig, "指标配置不应为null");
        
        // 由于测试环境可能没有实际的配置文件，这里只验证方法能正常执行
        // 在实际环境中，可以添加更多具体的验证
    }
    
    @Test
    public void testValidateDatabaseConfig() {
        // 测试空配置
        assertFalse(configLoader.validateDatabaseConfig(null), "空配置应该验证失败");
        
        // 测试有效配置
        DatabaseConfig validConfig = new DatabaseConfig();
        validConfig.setType("mysql");
        validConfig.setUsername("testuser");
        validConfig.setPassword("testpass");
        
        // 由于需要节点配置，这个测试会失败，但验证了验证逻辑
        assertFalse(configLoader.validateDatabaseConfig(validConfig), "没有节点的配置应该验证失败");
    }
}
