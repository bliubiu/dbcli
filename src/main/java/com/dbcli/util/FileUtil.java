package com.dbcli.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件工具类
 */
public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    
    /**
     * 创建目录（如果不存在）
     */
    public static void createDirectoryIfNotExists(String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.debug("创建目录: {}", dirPath);
            }
        } catch (IOException e) {
            logger.error("创建目录失败: {}", dirPath, e);
        }
    }
    
    /**
     * 检查文件是否存在
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
    
    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
    
    /**
     * 获取不带扩展名的文件名
     */
    public static String getFileNameWithoutExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return filename;
        }
        
        return filename.substring(0, lastDotIndex);
    }
    
    /**
     * 删除文件
     */
    public static boolean deleteFile(String filePath) {
        try {
            return Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            logger.error("删除文件失败: {}", filePath, e);
            return false;
        }
    }

    /**
     * 写入文件内容
     */
    public static void writeToFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        // 确保父目录存在
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, content.getBytes("UTF-8"));
        logger.debug("写入文件: {}", filePath);
    }
}
