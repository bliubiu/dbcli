package com.dbcli;

import com.dbcli.config.AppConfig;
import com.dbcli.web.EnhancedWebServer;

import java.lang.reflect.Method;
import java.util.Map;

public class JsonParsingTest {
    public static void main(String[] args) {
        try {
            // 创建AppConfig实例
            AppConfig config = new AppConfig();
            config.setWebPort(8080);
            
            // 创建EnhancedWebServer实例
            EnhancedWebServer server = new EnhancedWebServer(config);
            
            // 使用反射获取内部类ConfigManagementHandler
            Class<?> serverClass = EnhancedWebServer.class;
            Class<?>[] innerClasses = serverClass.getDeclaredClasses();
            Class<?> configHandlerClass = null;
            
            for (Class<?> innerClass : innerClasses) {
                if (innerClass.getSimpleName().equals("ConfigManagementHandler")) {
                    configHandlerClass = innerClass;
                    break;
                }
            }
            
            if (configHandlerClass == null) {
                System.err.println("未找到ConfigManagementHandler类");
                return;
            }
            
            // 创建ConfigManagementHandler实例
            Object configHandler = configHandlerClass.getDeclaredConstructor(EnhancedWebServer.class).newInstance(server);
            
            // 获取parseJson方法
            Method parseJsonMethod = configHandlerClass.getDeclaredMethod("parseJson", String.class);
            parseJsonMethod.setAccessible(true);
            
            // 测试正常的JSON
            String normalJson = "{\"format\":\"excel\",\"test\":\"value\"}";
            System.out.println("测试正常JSON: " + normalJson);
            Map<String, Object> result1 = (Map<String, Object>) parseJsonMethod.invoke(configHandler, normalJson);
            System.out.println("解析结果: " + result1);
            
            // 测试包含控制字符的JSON（模拟之前的问题）
            String jsonWithControlChar = "{\"message\":\"Report generated at 2025-09-27 16:26:20\u0000\", \"status\":\"success\"}";
            System.out.println("\n测试包含控制字符的JSON: " + jsonWithControlChar);
            Map<String, Object> result2 = (Map<String, Object>) parseJsonMethod.invoke(configHandler, jsonWithControlChar);
            System.out.println("解析结果: " + result2);
            
            System.out.println("\n测试完成，JSON解析功能正常！");
            
        } catch (Exception e) {
            System.err.println("测试过程中出现错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}