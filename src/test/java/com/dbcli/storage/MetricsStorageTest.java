package com.dbcli.storage;

import com.dbcli.model.MetricResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 指标存储功能测试类
 */
public class MetricsStorageTest {
    private static final Logger logger = LoggerFactory.getLogger(MetricsStorageTest.class);
    
    private MetricsStorageManager storageManager;
    private StorageConfig storageConfig;
    
    @BeforeEach
    public void setUp() {
        // 创建测试用的存储配置（使用内存数据库）
        storageConfig = new StorageConfig();
        storageConfig.setEnabled(false); // 测试时不实际连接数据库
        storageConfig.setType("postgresql");
        storageConfig.setHost("localhost");
        storageConfig.setPort(5432);
        storageConfig.setDatabase("test_db");
        storageConfig.setUsername("test_user");
        storageConfig.setPassword("test_password");
        storageConfig.setBatchMode(true);
        storageConfig.setBatchSize(10);
        
        storageManager = new MetricsStorageManager(storageConfig);
    }
    
    @AfterEach
    public void tearDown() {
        if (storageManager != null) {
            storageManager.close();
        }
    }
    
    @Test
    public void testStorageConfig() {
        assertNotNull(storageConfig);
        assertFalse(storageConfig.isEnabled());
        assertEquals("postgresql", storageConfig.getType());
        assertEquals("localhost", storageConfig.getHost());
        assertEquals(5432, storageConfig.getPort());
        assertEquals("test_db", storageConfig.getDatabase());
        assertEquals("test_user", storageConfig.getUsername());
        assertEquals("test_password", storageConfig.getPassword());
        assertTrue(storageConfig.isBatchMode());
        assertEquals(10, storageConfig.getBatchSize());
    }
    
    @Test
    public void testMetricsStorageManagerCreation() {
        assertNotNull(storageManager);
        assertEquals(storageConfig, storageManager.getConfig());
    }
    
    @Test
    public void testSaveSingleMetricResult() {
        MetricResult result = createTestMetricResult();
        
        // 验证不会抛出异常
        assertDoesNotThrow(() -> {
            storageManager.saveMetricResult(result);
        });
    }
    
    @Test
    public void testSaveMultipleMetricResults() {
        List<MetricResult> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MetricResult result = createTestMetricResult();
            result.setMetricName("test_metric_" + i);
            results.add(result);
        }
        
        // 验证不会抛出异常
        assertDoesNotThrow(() -> {
            storageManager.saveMetricResults(results);
        });
    }
    
    @Test
    public void testFlush() {
        // 验证不会抛出异常
        assertDoesNotThrow(() -> {
            storageManager.flush();
        });
    }
    
    /**
     * 创建测试用的指标结果
     */
    private MetricResult createTestMetricResult() {
        MetricResult result = new MetricResult();
        result.setSystemName("test_system");
        result.setDatabaseName("test_database");
        result.setNodeIp("192.168.1.100");
        result.setMetricName("test_metric");
        result.setMetricDescription("Test metric for unit testing");
        result.setMetricType("GAUGE");
        result.setValue("42");
        result.setExecuteTime(LocalDateTime.now());
        result.setCollectTime(LocalDateTime.now());
        result.setSuccess(true);
        result.setDbType("postgresql");
        result.setThresholdLevel("medium");
        result.setUnit("count");
        result.setNodeRole("master");
        
        return result;
    }
}