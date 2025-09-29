package com.dbcli.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * SQL脚本加载器
 * 用于从资源文件中加载SQL脚本
 */
public class SQLScriptLoader {
    private static final Logger logger = LoggerFactory.getLogger(SQLScriptLoader.class);
    
    /**
     * 从资源文件加载SQL脚本
     * @param resourcePath 资源文件路径
     * @return SQL脚本内容
     */
    public static String loadSQLScript(String resourcePath) {
        try (InputStream inputStream = SQLScriptLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                logger.error("无法找到SQL脚本文件: {}", resourcePath);
                return null;
            }
            
            // 使用Scanner读取整个文件内容
            Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String content = scanner.hasNext() ? scanner.next() : "";
            
            // 移除SQL注释和多余的空白字符
            return cleanSQLScript(content);
        } catch (IOException e) {
            logger.error("加载SQL脚本文件失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 清理SQL脚本内容，移除注释和多余的空白字符
     * @param script 原始SQL脚本
     * @return 清理后的SQL脚本
     */
    private static String cleanSQLScript(String script) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        
        // 移除单行注释
        script = script.replaceAll("--.*", "");
        
        // 移除多余的空白字符和换行符
        script = script.replaceAll("\\s+", " ").trim();
        
        return script;
    }
}