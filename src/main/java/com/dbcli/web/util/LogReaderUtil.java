package com.dbcli.web.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogReaderUtil {
    private static final Logger logger = LoggerFactory.getLogger(LogReaderUtil.class);
    
    /**
     * 读取最近的日志内容
     * 
     * @param logFilePath 日志文件路径
     * @return 最近的日志行列表
     */
    public static List<String> readRecentLogs(String logFilePath) throws IOException {
        Path logFile = Paths.get(logFilePath);
        if (!Files.exists(logFile)) {
            return Arrays.asList("日志文件不存在: " + logFilePath);
        }
        
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long fileLength = raf.length();
            long startPosition = Math.max(0, fileLength - 20480); // 读取最后20KB
            
            raf.seek(startPosition);
            String line;
            while ((line = raf.readLine()) != null) {
                // RandomAccessFile读取的是ISO-8859-1编码，需要转换为UTF-8
                String utf8Line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                // 清理可能的控制字符和特殊字符
                utf8Line = utf8Line.replaceAll("[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
                // 转义JSON特殊字符
                utf8Line = escapeJsonString(utf8Line);
                lines.add(utf8Line);
            }
            
            // 只返回最后100行
            int start = Math.max(0, lines.size() - 100);
            return lines.subList(start, lines.size());
        }
    }
    
    /**
     * 转义JSON特殊字符
     * 
     * @param str 需要转义的字符串
     * @return 转义后的字符串
     */
    private static String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        
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
                    // 跳过可能导致JSON解析错误的控制字符
                    if (c < 0x20 || c > 0x7E) {
                        // 对于非ASCII字符，保留原样（UTF-8编码）
                        sb.append(c);
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}