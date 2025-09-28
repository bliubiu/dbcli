package com.dbcli;

import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class TestConfigFileHandling {
    
    public static void main(String[] args) {
        // 测试配置文件处理，特别是包含控制字符的情况
        System.out.println("Testing config file handling with control characters...");
        
        try {
            // 创建一个包含控制字符的测试配置内容
            String configContent = "database:\n  name: testdb\n  host: localhost\n  port: 5432\n  # 这里可能包含控制字符\u0000\n  description: \"Test database configuration\"";
            
            // 模拟配置文件读取
            System.out.println("Original config content:");
            System.out.println(configContent);
            
            // 模拟将配置内容包装成JSON响应的过程
            Map<String, Object> result = new HashMap<>();
            result.put("name", "test-config.yml");
            result.put("content", configContent);
            
            // 使用我们修复的JSON序列化方法
            String jsonResponse = toJson(result);
            System.out.println("\nGenerated JSON response:");
            System.out.println(jsonResponse);
            
            // 验证JSON响应是有效的（不包含控制字符错误）
            if (isValidJsonString(jsonResponse)) {
                System.out.println("\n✓ JSON response is valid");
            } else {
                System.out.println("\n✗ JSON response is invalid");
            }
            
            // 测试解析这个JSON响应
            Map<String, Object> parsed = parseJson(jsonResponse);
            System.out.println("\nParsed result:");
            System.out.println("Name: " + parsed.get("name"));
            System.out.println("Content: " + parsed.get("content"));
            
        } catch (Exception e) {
            System.err.println("Config file handling test failed: " + e.getMessage());
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
    
    // 简化的JSON解析方法（用于测试）
    private static Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        try {
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            
            String[] pairs = splitJsonPairs(content);
            for (String pair : pairs) {
                String[] keyValue = splitKeyValuePair(pair);
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
    
    private static String[] splitJsonPairs(String content) {
        java.util.List<String> pairs = new java.util.ArrayList<>();
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        char stringDelimiter = 0;
        int startIndex = 0;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (inString) {
                if (c == stringDelimiter && (i == 0 || content.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                } else if (c == '[') {
                    bracketCount++;
                } else if (c == ']') {
                    bracketCount--;
                } else if (c == ',' && braceCount == 0 && bracketCount == 0) {
                    pairs.add(content.substring(startIndex, i));
                    startIndex = i + 1;
                }
            }
        }
        
        if (startIndex < content.length()) {
            pairs.add(content.substring(startIndex));
        }
        
        return pairs.toArray(new String[0]);
    }
    
    private static String[] splitKeyValuePair(String pair) {
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        char stringDelimiter = 0;
        
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            
            if (inString) {
                if (c == stringDelimiter && (i == 0 || pair.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                } else if (c == '[') {
                    bracketCount++;
                } else if (c == ']') {
                    bracketCount--;
                } else if (c == ':' && braceCount == 0 && bracketCount == 0) {
                    return new String[] { pair.substring(0, i), pair.substring(i + 1) };
                }
            }
        }
        
        return new String[] { pair };
    }
    
    private static String unescapeJsonString(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        } else if (str.startsWith("'") && str.endsWith("'")) {
            str = str.substring(1, str.length() - 1);
        }
        
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
                    case 'u':
                        if (i + 5 < str.length()) {
                            try {
                                String hex = str.substring(i + 2, i + 6);
                                int codePoint = Integer.parseInt(hex, 16);
                                sb.append((char) codePoint);
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            } else {
                // 处理控制字符
                if (c >= 32 && c <= 126) { // 可打印ASCII字符
                    sb.append(c);
                } else if (c == '\t' || c == '\n' || c == '\r') { // 允许的控制字符
                    sb.append(c);
                } else {
                    // 跳过其他控制字符
                    System.out.println("Skipping control character: \\u" + String.format("%04x", (int)c));
                }
            }
        }
        
        return sb.toString();
    }
}