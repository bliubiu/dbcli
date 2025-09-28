package com.dbcli;

import java.util.HashMap;
import java.util.Map;

public class TestJsonEscape {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(TestJsonEscape.class.getName());
    
    public static void main(String[] args) {
        // 测试修复后的JSON转义处理
        System.out.println("Testing fixed JSON escape handling...");
        
        // 测试包含控制字符的配置内容
        String configContent = "database:\n  name: testdb\n  host: localhost\n  port: 5432\n  # 这里可能包含控制字符\u0000\n  description: \"Test database configuration\"";
        System.out.println("Original config content: " + configContent);
        
        try {
            // 测试新的escapeJsonString方法
            String escaped = escapeJsonString(configContent);
            System.out.println("Escaped content: " + escaped);
            
            // 测试将配置内容包装成JSON响应
            Map<String, Object> result = new HashMap<>();
            result.put("name", "test-config.yml");
            result.put("content", configContent);
            
            String jsonResponse = toJson(result);
            System.out.println("JSON response: " + jsonResponse);
            
            // 验证JSON响应是有效的（不包含控制字符错误）
            if (isValidJsonString(jsonResponse)) {
                System.out.println("✓ JSON response is valid");
            } else {
                System.out.println("✗ JSON response is invalid");
            }
        } catch (Exception e) {
            System.err.println("JSON processing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String toJson(Object obj) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(entry.getKey().toString().replace("\"", "\\\"")).append("\":");
                sb.append(toJson(entry.getValue()));
                i++;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof String) {
            return "\"" + escapeJsonString(obj.toString()) + "\"";
        } else {
            return obj != null ? obj.toString() : "null";
        }
    }
    
    private static String escapeJsonString(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // 处理控制字符，避免"Bad control character in string literal"错误
                    if (c >= 32 && c <= 126) { // 可打印ASCII字符
                        sb.append(c);
                    } else if (c == '\t' || c == '\n' || c == '\r') { // 允许的控制字符
                        sb.append(c);
                    } else {
                        // 跳过其他控制字符（如\u0000等）
                        System.out.println("Skipping control character: \\u" + String.format("%04x", (int)c));
                    }
                    break;
            }
        }
        return sb.toString();
    }
    
    private static boolean isValidJsonString(String json) {
        // 简单验证JSON字符串是否包含控制字符错误
        try {
            // 检查是否包含未转义的控制字符
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                // 检查是否在字符串内部有未转义的控制字符
                if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                    // 检查是否被转义
                    if (i == 0 || json.charAt(i-1) != '\\') {
                        System.out.println("Found unescaped control character at position " + i + ": \\u" + String.format("%04x", (int)c));
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}