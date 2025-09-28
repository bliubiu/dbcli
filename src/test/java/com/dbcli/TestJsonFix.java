package com.dbcli;

import java.util.HashMap;
import java.util.Map;

public class TestJsonFix {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(TestJsonFix.class.getName());
    
    public static void main(String[] args) {
        // 测试修复后的JSON解析器
        System.out.println("Testing fixed JSON parser...");
        
        // 测试包含控制字符的JSON字符串
        String testJsonWithControlChar = "{\"message\":\"Report generated at 2025-09-27 16:26:20\u0000\", \"status\":\"success\"}";
        System.out.println("Testing JSON with control character: " + testJsonWithControlChar);
        
        try {
            Map<String, Object> result = parseJson(testJsonWithControlChar);
            System.out.println("Parsed result: " + result);
        } catch (Exception e) {
            System.err.println("JSON parsing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static Map<String, Object> parseJson(String json) {
        // 使用修复后的JSON解析实现
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
        // 修复后的字符串转义处理，能够处理控制字符
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
                // 处理控制字符，避免"Bad control character in string literal"错误
                if (c >= 32 && c <= 126) { // 可打印ASCII字符
                    sb.append(c);
                } else if (c == '\t' || c == '\n' || c == '\r') { // 允许的控制字符
                    sb.append(c);
                } else {
                    // 跳过其他控制字符（如\u0000等）
                    System.out.println("Skipping control character: \\u" + String.format("%04x", (int)c));
                }
            }
        }
        
        return sb.toString();
    }
}