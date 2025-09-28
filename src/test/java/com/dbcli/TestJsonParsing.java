package com.dbcli;

import java.util.HashMap;
import java.util.Map;

public class TestJsonParsing {
    public static void main(String[] args) {
        // 测试JSON解析功能
        String testJson = "{\"format\":\"excel\"}";
        System.out.println("Testing JSON: " + testJson);
        
        Map<String, Object> result = parseJson(testJson);
        System.out.println("Parsed result: " + result);
        
        if (result.containsKey("format")) {
            System.out.println("Format value: " + result.get("format"));
        }
    }
    
    private static Map<String, Object> parseJson(String json) {
        // 简化的JSON解析实现
        Map<String, Object> result = new HashMap<>();
        try {
            // 简单的JSON解析实现，处理常见的转义字符
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            
            // 按逗号分割键值对，但要避免分割引号内的逗号
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = unescapeJsonString(keyValue[0].trim());
                    String value = unescapeJsonString(keyValue[1].trim());
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
            System.err.println("JSON parsing failed: " + e.getMessage());
        }
        return result;
    }
    
    private static String unescapeJsonString(String str) {
        // 去掉首尾引号
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        } else if (str.startsWith("'") && str.endsWith("'")) {
            str = str.substring(1, str.length() - 1);
        }
        
        // 处理常见的JSON转义字符
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                char next = str.charAt(i + 1);
                switch (next) {
                    case '"':
                        sb.append('"');
                        i++;
                        break;
                    case '\\':
                        sb.append('\\');
                        i++;
                        break;
                    case '/':
                        sb.append('/');
                        i++;
                        break;
                    case 'b':
                        sb.append('\b');
                        i++;
                        break;
                    case 'f':
                        sb.append('\f');
                        i++;
                        break;
                    case 'n':
                        sb.append('\n');
                        i++;
                        break;
                    case 'r':
                        sb.append('\r');
                        i++;
                        break;
                    case 't':
                        sb.append('\t');
                        i++;
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }
}