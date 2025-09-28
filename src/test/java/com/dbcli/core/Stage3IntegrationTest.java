package com.dbcli.core;

import com.dbcli.core.Stage3IntegrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 第三阶段集成服务测试
 */
public class Stage3IntegrationTest {
    
    private Stage3IntegrationService service;
    private static final int TEST_PORT = 8081;
    
    @BeforeEach
    void setUp() throws IOException {
        service = new Stage3IntegrationService(TEST_PORT);
    }
    
    @AfterEach
    void tearDown() {
        if (service != null) {
            service.stop();
        }
    }
    
    @Test
    @DisplayName("测试服务初始化")
    void testServiceInitialization() {
        // 初始化前状态检查
        Stage3IntegrationService.ServiceStatus status = service.getStatus();
        assertFalse(status.isInitialized());
        assertFalse(status.isRunning());
        
        // 执行初始化
        service.initialize();
        
        // 初始化后状态检查
        status = service.getStatus();
        assertTrue(status.isInitialized());
        assertFalse(status.isRunning()); // 初始化后未启动
        assertEquals(TEST_PORT, status.getWebPort());
    }
    
    @Test
    @DisplayName("测试服务启动和停止")
    void testServiceStartStop() {
        // 初始化服务
        service.initialize();
        
        // 启动服务
        service.start();
        
        // 启动后状态检查
        Stage3IntegrationService.ServiceStatus status = service.getStatus();
        assertTrue(status.isInitialized());
        assertTrue(status.isRunning());
        assertTrue(status.isWebServerActive());
        assertTrue(status.isHotReloadActive());
        
        // 停止服务
        service.stop();
        
        // 停止后状态检查
        status = service.getStatus();
        assertTrue(status.isInitialized()); // 初始化状态保持
        assertFalse(status.isRunning());
        assertFalse(status.isWebServerActive());
        assertFalse(status.isHotReloadActive());
    }
    
    @Test
    @DisplayName("测试Web服务器可访问性")
    void testWebServerAccessibility() throws Exception {
        // 启动服务
        service.initialize();
        service.start();
        
        // 等待服务完全启动
        Thread.sleep(1000);
        
        // 测试Web服务器是否可访问
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Web服务器应该返回200状态码");
        
        connection.disconnect();
    }
    
    @Test
    @DisplayName("测试API端点")
    void testApiEndpoints() throws Exception {
        // 启动服务
        service.initialize();
        service.start();
        
        // 等待服务完全启动
        Thread.sleep(1000);
        
        // 测试状态API
        testApiEndpoint("/api/status");
        
        // 测试指标API
        testApiEndpoint("/api/metrics");
        
        // 测试配置API
        testApiEndpoint("/api/config");
    }
    
    private void testApiEndpoint(String endpoint) throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertTrue(responseCode == 200 || responseCode == 404, 
            "API端点 " + endpoint + " 应该返回有效的HTTP状态码");
        
        connection.disconnect();
    }
    
    @Test
    @DisplayName("测试重复初始化和启动")
    void testRepeatedInitializationAndStart() {
        // 第一次初始化和启动
        service.initialize();
        service.start();
        
        Stage3IntegrationService.ServiceStatus status1 = service.getStatus();
        assertTrue(status1.isInitialized());
        assertTrue(status1.isRunning());
        
        // 重复初始化和启动（应该被忽略）
        service.initialize();
        service.start();
        
        Stage3IntegrationService.ServiceStatus status2 = service.getStatus();
        assertTrue(status2.isInitialized());
        assertTrue(status2.isRunning());
        
        // 状态应该保持一致
        assertEquals(status1.getWebPort(), status2.getWebPort());
    }
    
    @Test
    @DisplayName("测试服务状态信息")
    void testServiceStatusInformation() {
        service.initialize();
        service.start();
        
        Stage3IntegrationService.ServiceStatus status = service.getStatus();
        
        // 验证状态信息的完整性
        assertNotNull(status);
        assertTrue(status.isInitialized());
        assertTrue(status.isRunning());
        assertTrue(status.isWebServerActive());
        assertTrue(status.isHotReloadActive());
        assertEquals(TEST_PORT, status.getWebPort());
        assertTrue(status.getWatchedFileCount() >= 0);
        
        // 验证toString方法
        String statusString = status.toString();
        assertNotNull(statusString);
        assertTrue(statusString.contains("ServiceStatus"));
        assertTrue(statusString.contains("initialized=true"));
        assertTrue(statusString.contains("running=true"));
    }
}