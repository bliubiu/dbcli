package com.dbcli.core;

import com.dbcli.config.ConfigurationValidator;
import com.dbcli.service.EnhancedReportGeneratorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 增强DbCliRunner测试
 */
public class EnhancedDbCliRunnerTest {
    
    private EnhancedDbCliRunner runner;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        runner = new EnhancedDbCliRunner();
    }
    
    @Test
    void testServiceContainerInitialization() {
        ServiceContainer container = runner.getServiceContainer();
        assertNotNull(container);
        
        // 验证核心服务已注册
        assertTrue(container.hasService("ConfigLoader"));
        assertTrue(container.hasService("DatabaseManager"));
        assertTrue(container.hasService("MetricsExecutor"));
    }
    
    @Test
    void testConfigurationValidator() {
        ConfigurationValidator validator = runner.getValidator();
        assertNotNull(validator);
    }
    
    @Test
    void testReportGeneratorFactory() {
        EnhancedReportGeneratorFactory factory = runner.getReportFactory();
        assertNotNull(factory);
        
        // 验证支持的格式
        assertTrue(factory.supportsFormat("excel"));
        assertTrue(factory.supportsFormat("html"));
        assertFalse(factory.supportsFormat("unsupported"));
    }
    
    @Test
    void testRunWithInvalidConfigPath() {
        String configPath = tempDir.resolve("nonexistent.json").toString();
        String metricsPath = tempDir.resolve("metrics.json").toString();
        String outputPath = tempDir.resolve("output.xlsx").toString();
        
        // 创建指标文件
        try {
            new File(metricsPath).createNewFile();
        } catch (Exception e) {
            fail("无法创建测试文件");
        }
        
        boolean result = runner.run(configPath, metricsPath, outputPath, "excel");
        assertFalse(result, "应该因为配置文件不存在而失败");
    }
    
    @Test
    void testRunWithInvalidMetricsPath() {
        String configPath = tempDir.resolve("config.json").toString();
        String metricsPath = tempDir.resolve("nonexistent.json").toString();
        String outputPath = tempDir.resolve("output.xlsx").toString();
        
        // 创建配置文件
        try {
            new File(configPath).createNewFile();
        } catch (Exception e) {
            fail("无法创建测试文件");
        }
        
        boolean result = runner.run(configPath, metricsPath, outputPath, "excel");
        assertFalse(result, "应该因为指标文件不存在而失败");
    }
    
    @Test
    void testRunWithUnsupportedFormat() {
        String configPath = tempDir.resolve("config.json").toString();
        String metricsPath = tempDir.resolve("metrics.json").toString();
        String outputPath = tempDir.resolve("output.txt").toString();
        
        // 创建测试文件
        try {
            new File(configPath).createNewFile();
            new File(metricsPath).createNewFile();
        } catch (Exception e) {
            fail("无法创建测试文件");
        }
        
        boolean result = runner.run(configPath, metricsPath, outputPath, "unsupported");
        assertFalse(result, "应该因为不支持的格式而失败");
    }
}