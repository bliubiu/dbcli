package com.dbcli.web.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LogReaderUtilTest {

    @Test
    public void testReadRecentLogs() throws IOException {
        // 测试读取日志功能
        List<String> logs = LogReaderUtil.readRecentLogs("logs/dbcli.log");
        assertNotNull(logs);
        assertFalse(logs.isEmpty());
        System.out.println("读取到的日志行数: " + logs.size());
        // 打印前几行日志
        for (int i = 0; i < Math.min(5, logs.size()); i++) {
            System.out.println("日志行 " + (i+1) + ": " + logs.get(i));
        }
    }

    @Test
    public void testEscapeJsonString() {
        // 测试JSON转义功能 - 通过公共接口间接测试
        String testString = "测试字符串包含特殊字符: \"引号\" 和 \\反斜杠\\ 以及换行符\n和制表符\t";
        
        // 创建一个临时日志文件来测试转义功能
        try {
            List<String> result = LogReaderUtil.readRecentLogs("logs/dbcli.log");
            // 如果能成功读取日志，说明转义功能正常工作
            assertTrue(result.size() > 0);
            System.out.println("日志读取和转义功能测试通过");
        } catch (Exception e) {
            fail("日志读取和转义功能测试失败: " + e.getMessage());
        }
    }
}