package com.dbcli;

import java.util.HashMap;
import java.util.Map;

public class TestReportGeneration {
    public static void main(String[] args) {
        // 测试报告生成过程中可能出现的JSON问题
        String testJson = "{\"format\":\"excel\", \"options\":{\"includeCharts\":true, \"theme\":\"default\"}}";
        System.out.println("Testing JSON: " + testJson);
        
        Map<String, Object> result = parseJson(testJson);
        System.out.println("Parsed result: " + result);
        
        if (result.containsKey("format")) {
            System.out.println("Format value: " + result.get("format"));
        }
        
        // 测试包含特殊字符的JSON
        String testJsonWithSpecialChars = "{\"message\":\"Report generated at 2025-09-27 16:26:20\", \"path\":\"C:\\\\Users\\\\test\\\\reports\\\\db_report.xlsx\"}";
        System.out.println("\nTesting JSON with special chars: " + testJsonWithSpecialChars);
        
        Map<String, Object> result2 = parseJson(testJsonWithSpecialChars);
        System.out.println("Parsed result: " + result2);
    }
    
    private static Map<String, Object> parseJson(String json) {
        // 使用更可靠的JSON解析实现
        Map<String, Object> result = new HashMap<>();
        try {
            // 简单的JSON解析实现，处理常见的转义字符
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            
            // 按逗号分割键值对，但要避免分割引号内的逗号
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
        // 更复杂的分割逻辑，处理嵌套对象
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
        
        // 添加最后一个键值对
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
                    case 'u':
                        // 处理Unicode转义 \\uXXXX
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
                sb.append(c);
            }
        }
        
        return sb.toString();
    }
}