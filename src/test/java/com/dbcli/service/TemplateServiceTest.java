package com.dbcli.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * TemplateService测试类
 */
public class TemplateServiceTest {
    
    private TemplateService templateService;
    private String tempDir;
    
    @BeforeEach
    public void setUp() throws IOException {
        templateService = new TemplateService();
        tempDir = System.getProperty("java.io.tmpdir") + "/dbcli-test-" + System.currentTimeMillis();
        Files.createDirectories(Paths.get(tempDir));
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        // 清理临时目录
        deleteDirectory(new File(tempDir));
    }
    
    @Test
    public void testGenerateTemplates() throws IOException {
        templateService.generateTemplates(tempDir, tempDir);
        
        // 验证生成的配置文件
        assertTrue(new File(tempDir, "oracle-config.yml").exists());
        assertTrue(new File(tempDir, "mysql-config.yml").exists());
        assertTrue(new File(tempDir, "pg-config.yml").exists());
        assertTrue(new File(tempDir, "dm-config.yml").exists());
        
        // 验证生成的指标文件
        assertTrue(new File(tempDir, "oracle-metrics.yml").exists());
        assertTrue(new File(tempDir, "mysql-metrics.yml").exists());
        assertTrue(new File(tempDir, "pg-metrics.yml").exists());
        assertTrue(new File(tempDir, "dm-metrics.yml").exists());
    }
    
    @Test
    public void testGenerateInteractiveTemplates() throws IOException {
        templateService.generateInteractiveTemplates(tempDir, tempDir);
        
        // 验证生成的配置文件
        assertTrue(new File(tempDir, "oracle-config.yml").exists());
        assertTrue(new File(tempDir, "mysql-config.yml").exists());
        assertTrue(new File(tempDir, "pg-config.yml").exists());
        assertTrue(new File(tempDir, "dm-config.yml").exists());
        
        // 验证生成的指标文件
        assertTrue(new File(tempDir, "oracle-metrics.yml").exists());
        assertTrue(new File(tempDir, "mysql-metrics.yml").exists());
        assertTrue(new File(tempDir, "pg-metrics.yml").exists());
        assertTrue(new File(tempDir, "dm-metrics.yml").exists());
    }
    
    @Test
    public void testGenerateReadmeTemplate() throws IOException {
        templateService.generateReadmeTemplate(tempDir);
        
        // 验证生成的README文件
        assertTrue(new File(tempDir, "README.md").exists());
    }
    
    @Test
    public void testTemplateFileContent() throws IOException {
        templateService.generateTemplates(tempDir, tempDir);
        
        // 读取Oracle模板文件内容
        String oracleTemplate = new String(Files.readAllBytes(
            Paths.get(tempDir, "oracle-config.yml")));
        
        // 验证模板内容包含必要的字段
        assertTrue(oracleTemplate.contains("enable:"), "应包含enable字段");
        assertTrue(oracleTemplate.contains("port:"), "应包含port字段");
        assertTrue(oracleTemplate.contains("username:"), "应包含username字段");
        assertTrue(oracleTemplate.contains("password:"), "应包含password字段");
        assertTrue(oracleTemplate.contains("nodes:"), "应包含nodes字段");
        assertTrue(oracleTemplate.contains("host:"), "应包含host字段");
        assertTrue(oracleTemplate.contains("svc_name:"), "应包含svc_name字段");
        assertTrue(oracleTemplate.contains("role:"), "应包含role字段");
    }
    
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}