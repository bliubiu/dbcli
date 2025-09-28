package com.dbcli.web;

import com.dbcli.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置管理功能测试
 */
public class ConfigManagementTest {
    
    @TempDir
    Path tempDir;
    
    private EnhancedWebServer webServer;
    private AppConfig config;
    private HttpClient httpClient;
    private int port = 18080; // 使用不同的端口避免冲突
    
    @BeforeEach
    void setUp() throws IOException {
        // 创建测试配置
        config = new AppConfig();
        config.setConfigPath(tempDir.resolve("configs").toString());
        config.setMetricsPath(tempDir.resolve("metrics").toString());
        config.setOutputPath(tempDir.resolve("reports").toString());
        config.setWebPort(port);
        
        // 创建必要的目录
        Files.createDirectories(tempDir.resolve("configs"));
        Files.createDirectories(tempDir.resolve("metrics"));
        Files.createDirectories(tempDir.resolve("reports"));
        
        // 创建测试配置文件
        String testConfig = """
            test-db:
              enable: true
              port: 3306
              username: testuser
              password: testpass
              nodes:
              - {host: localhost, svc_name: mysql, role: master}
            """;
        Files.writeString(tempDir.resolve("configs/test-config.yml"), testConfig);
        
        // 启动Web服务器
        webServer = new EnhancedWebServer(config);
        webServer.start();
        
        // 创建HTTP客户端
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // 等待服务器启动
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @AfterEach
    void tearDown() {
        if (webServer != null) {
            webServer.stop();
        }
    }
    
    @Test
    void testGetConfigList() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/config"))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("test-config.yml"));
        System.out.println("配置列表响应: " + response.body());
    }
    
    @Test
    void testGetSpecificConfig() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/config/test-config.yml"))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("test-db"));
        assertTrue(response.body().contains("testuser"));
        System.out.println("配置文件内容响应: " + response.body());
    }
    
    @Test
    void testUpdateConfig() throws Exception {
        String updatedConfig = """
            updated-db:
              enable: true
              port: 3307
              username: updateduser
              password: updatedpass
              nodes:
              - {host: newhost, svc_name: mysql, role: master}
            """;
        
        String requestBody = String.format("""
            {
              "fileName": "test-config.yml",
              "content": "%s",
              "type": "database"
            }
            """, updatedConfig.replace("\n", "\\n").replace("\"", "\\\""));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/config"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("更新配置响应状态: " + response.statusCode());
        System.out.println("更新配置响应内容: " + response.body());
        
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("success"));
        
        // 验证文件是否真的被更新了
        String fileContent = Files.readString(tempDir.resolve("configs/test-config.yml"));
        assertTrue(fileContent.contains("updated-db"));
        assertTrue(fileContent.contains("updateduser"));
        System.out.println("验证更新后的文件内容: " + fileContent);
    }
    
    @Test
    void testCreateNewConfig() throws Exception {
        String newConfig = """
            new-db:
              enable: true
              port: 5432
              username: newuser
              password: newpass
              nodes:
              - {host: newserver, svc_name: postgres, role: master}
            """;
        
        String requestBody = String.format("""
            {
              "fileName": "new-config.yml",
              "content": "%s",
              "type": "database"
            }
            """, newConfig.replace("\n", "\\n").replace("\"", "\\\""));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/config"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("创建新配置响应状态: " + response.statusCode());
        System.out.println("创建新配置响应内容: " + response.body());
        
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("success"));
        
        // 验证新文件是否被创建
        Path newConfigFile = tempDir.resolve("configs/new-config.yml");
        assertTrue(Files.exists(newConfigFile));
        
        String fileContent = Files.readString(newConfigFile);
        assertTrue(fileContent.contains("new-db"));
        assertTrue(fileContent.contains("newuser"));
        System.out.println("验证新创建的文件内容: " + fileContent);
    }
}