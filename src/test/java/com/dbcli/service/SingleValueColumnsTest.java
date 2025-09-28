package com.dbcli.service;

import com.dbcli.model.MetricResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单值指标columns功能测试
 */
public class SingleValueColumnsTest {

    @TempDir
    File tempDir;

    @Test
    public void testSingleValueWithColumns() throws Exception {
        // 创建测试数据
        List<MetricResult> results = createTestResults();
        
        // 生成Excel报告
        ExcelReportGenerator generator = new ExcelReportGenerator();
        String outputPath = new File(tempDir, "test_report.xlsx").getAbsolutePath();
        generator.generate(results, outputPath, "metrics");
        
        // 验证生成的Excel文件
        verifyExcelContent(outputPath);
    }

    @Test
    public void testStreamingExcelWithColumns() throws Exception {
        // 创建测试数据
        List<MetricResult> results = createTestResults();
        
        // 生成流式Excel报告
        StreamingExcelReportGenerator generator = new StreamingExcelReportGenerator();
        String outputPath = new File(tempDir, "test_streaming_report.xlsx").getAbsolutePath();
        generator.generateReport(results, "metrics", outputPath);
        
        // 验证生成的Excel文件
        verifyStreamingExcelContent(outputPath);
    }

    private List<MetricResult> createTestResults() {
        List<MetricResult> results = new ArrayList<>();
        
        // 创建有columns定义的单值指标结果
        MetricResult result1 = new MetricResult();
        result1.setSystemName("测试系统1");
        result1.setDatabaseName("测试数据库1");
        result1.setNodeIp("192.168.1.100");
        result1.setMetricName("db_config");
        result1.setMetricDescription("数据库配置信息");
        result1.setMetricType("SINGLE");
        result1.setDbType("ORACLE");
        result1.setSuccess(true);
        result1.setExecuteTime(LocalDateTime.now());
        
        // 设置columns
        List<String> columns1 = Arrays.asList("数据库ID", "数据库名", "创建时间", "归档模式", "闪回模式");
        result1.setColumns(columns1);
        
        // 设置多值数据（模拟单值指标返回多列数据）
        Map<String, Object> dataRow1 = new LinkedHashMap<>();
        dataRow1.put("DBID", "1234567890");
        dataRow1.put("NAME", "TESTDB");
        dataRow1.put("CREATED", "2023-01-01 10:00:00");
        dataRow1.put("LOG_MODE", "ARCHIVELOG");
        dataRow1.put("FLASHBACK_ON", "YES");
        result1.setMultiValues(Arrays.asList(dataRow1));
        
        results.add(result1);
        
        // 创建另一个有columns定义的单值指标结果
        MetricResult result2 = new MetricResult();
        result2.setSystemName("测试系统1");
        result2.setDatabaseName("测试数据库1");
        result2.setNodeIp("192.168.1.100");
        result2.setMetricName("instance_status");
        result2.setMetricDescription("DB实例信息");
        result2.setMetricType("SINGLE");
        result2.setDbType("ORACLE");
        result2.setSuccess(true);
        result2.setExecuteTime(LocalDateTime.now());
        
        // 设置columns
        List<String> columns2 = Arrays.asList("实例名", "主机名", "启动时间");
        result2.setColumns(columns2);
        
        // 设置多值数据
        Map<String, Object> dataRow2 = new LinkedHashMap<>();
        dataRow2.put("INSTANCE_NAME", "TESTDB1");
        dataRow2.put("HOST_NAME", "testserver");
        dataRow2.put("STARTUP_TIME", "2023-01-01 09:00:00");
        result2.setMultiValues(Arrays.asList(dataRow2));
        
        results.add(result2);
        
        // 创建传统单值指标结果（无columns）
        MetricResult result3 = new MetricResult();
        result3.setSystemName("测试系统2");
        result3.setDatabaseName("测试数据库2");
        result3.setNodeIp("192.168.1.101");
        result3.setMetricName("active_sessions");
        result3.setMetricDescription("活跃会话数");
        result3.setMetricType("SINGLE");
        result3.setDbType("MYSQL");
        result3.setSuccess(true);
        result3.setExecuteTime(LocalDateTime.now());
        result3.setValue(25);
        
        results.add(result3);
        
        return results;
    }

    private void verifyExcelContent(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            // 验证ORACLE工作表
            Sheet oracleSheet = workbook.getSheet("ORACLE");
            assertNotNull(oracleSheet, "应该存在ORACLE工作表");
            
            // 验证表头
            Row headerRow = oracleSheet.getRow(0);
            assertNotNull(headerRow, "应该存在表头行");
            
            // 验证表头包含基本列和columns定义的列
            List<String> expectedHeaders = Arrays.asList(
                "系统名称", "数据库名称", "节点IP", 
                "数据库ID", "数据库名", "创建时间", "归档模式", "闪回模式",
                "实例名", "主机名", "启动时间",
                "执行时间"
            );
            
            int headerCellCount = headerRow.getLastCellNum();
            assertTrue(headerCellCount >= expectedHeaders.size(), 
                "表头列数应该包含所有预期的列");
            
            // 验证数据行
            Row dataRow = oracleSheet.getRow(1);
            assertNotNull(dataRow, "应该存在数据行");
            
            // 验证基本信息
            assertEquals("测试系统1", getCellValue(dataRow.getCell(0)));
            assertEquals("测试数据库1", getCellValue(dataRow.getCell(1)));
            assertEquals("192.168.1.100", getCellValue(dataRow.getCell(2)));
            
            // 验证MYSQL工作表（传统单值指标）
            Sheet mysqlSheet = workbook.getSheet("MYSQL");
            assertNotNull(mysqlSheet, "应该存在MYSQL工作表");
            
            Row mysqlHeaderRow = mysqlSheet.getRow(0);
            assertNotNull(mysqlHeaderRow, "MYSQL工作表应该存在表头行");
            
            Row mysqlDataRow = mysqlSheet.getRow(1);
            assertNotNull(mysqlDataRow, "MYSQL工作表应该存在数据行");
            assertEquals("测试系统2", getCellValue(mysqlDataRow.getCell(0)));
        }
    }

    private void verifyStreamingExcelContent(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            // 验证ORACLE工作表
            Sheet oracleSheet = workbook.getSheet("ORACLE");
            assertNotNull(oracleSheet, "流式Excel应该存在ORACLE工作表");
            
            // 验证表头
            Row headerRow = oracleSheet.getRow(0);
            assertNotNull(headerRow, "流式Excel应该存在表头行");
            
            // 验证数据行
            Row dataRow = oracleSheet.getRow(1);
            assertNotNull(dataRow, "流式Excel应该存在数据行");
            
            // 验证基本信息
            assertEquals("测试系统1", getCellValue(dataRow.getCell(0)));
            assertEquals("测试数据库1", getCellValue(dataRow.getCell(1)));
            assertEquals("192.168.1.100", getCellValue(dataRow.getCell(2)));
            
            // 验证MYSQL工作表
            Sheet mysqlSheet = workbook.getSheet("MYSQL");
            assertNotNull(mysqlSheet, "流式Excel应该存在MYSQL工作表");
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}