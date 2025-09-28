package com.dbcli.integration;

import com.dbcli.DbCliApplication;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DbCliIntegrationTest {

    @Test
    public void testMainExecution() throws Exception {
        // 备份并重定向标准输出
        PrintStream originalOut = System.out;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(bos));

        try {
            // 准备命令行参数，使用测试资源目录
            String[] args = {
                    "-c", "src/test/resources/config",
                    "-m", "src/test/resources/metrics",
                    "-o", "target/reports",
                    "-f", "excel"
            };

            // 执行主程序
            DbCliApplication.main(args);

            // 检查输出内容
            String output = bos.toString();
            assertTrue(output.contains("指标收集任务完成"), "输出应包含任务完成信息");
            assertTrue(output.contains("Excel报告生成成功"), "输出应包含报告生成成功信息");

        } finally {
            // 恢复标准输出
            System.setOut(originalOut);
        }
    }
}