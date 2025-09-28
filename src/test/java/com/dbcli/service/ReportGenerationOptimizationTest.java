package com.dbcli.service;

import com.dbcli.model.MetricResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报告生成优化功能测试
 */
class ReportGenerationOptimizationTest {
    
    @TempDir
    Path tempDir;
    
    private List<MetricResult> testResults;
    
    @BeforeEach
    void setUp() {
        testResults = generateTestResults(1000);
    }
    
    @Test
    void testStreamingExcelGenerator() throws Exception {
        ReportGeneratorInterface generator = EnhancedReportGeneratorFactory.getGenerator("streaming_excel");
        assertNotNull(generator);
        assertEquals("streaming_excel", generator.getReportFormat());
        assertEquals(".xlsx", generator.getFileExtension());
        assertTrue(generator.supportsResultCount(100000));
        
        String outputPath = tempDir.resolve("streaming_test.xlsx").toString();
        generator.generateReport(testResults, outputPath);
        
        // 验证文件是否生成
        assertTrue(tempDir.resolve("streaming_test.xlsx").toFile().exists());
    }
    
    @Test
    void testPaginatedHtmlGenerator() throws Exception {
        ReportGeneratorInterface generator = EnhancedReportGeneratorFactory.getGenerator("paginated_html");
        assertNotNull(generator);
        assertEquals("paginated_html", generator.getReportFormat());
        assertEquals(".html", generator.getFileExtension());
        assertTrue(generator.supportsResultCount(50000));
        
        String outputPath = tempDir.resolve("paginated_test").toString();
        generator.generateReport(testResults, outputPath);
        
        // 验证目录和文件是否生成
        assertTrue(tempDir.resolve("paginated_test").toFile().exists());
        assertTrue(tempDir.resolve("paginated_test").resolve("index.html").toFile().exists());
        assertTrue(tempDir.resolve("paginated_test").resolve("styles.css").toFile().exists());
    }
    
    @Test
    void testIntelligentSelection() {
        // 测试小数据量选择
        ReportGeneratorInterface smallDataGenerator = EnhancedReportGeneratorFactory.getOptimalGenerator("excel", 500);
        assertEquals("excel", smallDataGenerator.getReportFormat());
        
        // 测试中等数据量选择
        ReportGeneratorInterface mediumDataGenerator = EnhancedReportGeneratorFactory.getOptimalGenerator("excel", 15000);
        assertEquals("streaming_excel", mediumDataGenerator.getReportFormat());
        
        // 测试大数据量选择
        ReportGeneratorInterface largeDataGenerator = EnhancedReportGeneratorFactory.getOptimalGenerator("html", 8000);
        assertEquals("paginated_html", largeDataGenerator.getReportFormat());
        
        // 测试超大数据量选择
        ReportGeneratorInterface veryLargeDataGenerator = EnhancedReportGeneratorFactory.getOptimalGenerator("html", 60000);
        assertEquals("streaming_excel", veryLargeDataGenerator.getReportFormat());
    }
    
    @Test
    void testPerformancePrioritySelection() {
        // 测试性能优先选择
        ReportGeneratorInterface performanceGenerator = EnhancedReportGeneratorFactory.getOptimalGenerator("excel", 25000, true);
        assertEquals("streaming_excel", performanceGenerator.getReportFormat());
        
        ReportGeneratorInterface normalGenerator = EnhancedReportGeneratorFactory.getOptimalGenerator("excel", 25000, false);
        assertEquals("streaming_excel", normalGenerator.getReportFormat());
        
        // 测试小数据量性能优先
        ReportGeneratorInterface smallPerformanceGenerator = EnhancedReportGeneratorFactory.getOptimalGenerator("html", 1500, true);
        assertEquals("html", smallPerformanceGenerator.getReportFormat());
    }
    
    @Test
    void testFormatSupport() {
        // 测试格式支持检查
        assertTrue(EnhancedReportGeneratorFactory.isFormatSupported("excel"));
        assertTrue(EnhancedReportGeneratorFactory.isFormatSupported("html"));
        assertTrue(EnhancedReportGeneratorFactory.isFormatSupported("streaming_excel"));
        assertTrue(EnhancedReportGeneratorFactory.isFormatSupported("paginated_html"));
        assertFalse(EnhancedReportGeneratorFactory.isFormatSupported("unknown_format"));
        
        // 测试记录数支持
        assertTrue(EnhancedReportGeneratorFactory.canHandleResultCount("streaming_excel", 100000));
        assertTrue(EnhancedReportGeneratorFactory.canHandleResultCount("paginated_html", 50000));
        assertTrue(EnhancedReportGeneratorFactory.canHandleResultCount("excel", 10000));
    }
    
    @Test
    void testRecommendedFormats() {
        // 测试推荐格式
        String[] smallDataRecommended = EnhancedReportGeneratorFactory.getRecommendedFormats(500);
        assertEquals("excel", smallDataRecommended[0]);
        
        String[] mediumDataRecommended = EnhancedReportGeneratorFactory.getRecommendedFormats(5000);
        assertEquals("paginated_html", mediumDataRecommended[0]);
        
        String[] largeDataRecommended = EnhancedReportGeneratorFactory.getRecommendedFormats(60000);
        assertEquals("streaming_excel", largeDataRecommended[0]);
    }
    
    @Test
    void testCustomPageSizeGenerators() {
        // 测试自定义页面大小的生成器
        ReportGeneratorInterface smallPageGenerator = EnhancedReportGeneratorFactory.getGenerator("paginated_html_small");
        assertNotNull(smallPageGenerator);
        assertTrue(smallPageGenerator.getDescription().contains("50"));
        
        ReportGeneratorInterface largePageGenerator = EnhancedReportGeneratorFactory.getGenerator("paginated_html_large");
        assertNotNull(largePageGenerator);
        assertTrue(largePageGenerator.getDescription().contains("200"));
    }
    
    @Test
    void testGeneratorDescriptions() {
        // 测试生成器描述
        String excelDesc = EnhancedReportGeneratorFactory.getFormatDescription("excel");
        assertNotNull(excelDesc);
        assertFalse(excelDesc.isEmpty());
        
        String streamingExcelDesc = EnhancedReportGeneratorFactory.getFormatDescription("streaming_excel");
        assertNotNull(streamingExcelDesc);
        assertTrue(streamingExcelDesc.contains("流式"));
        
        String paginatedHtmlDesc = EnhancedReportGeneratorFactory.getFormatDescription("paginated_html");
        assertNotNull(paginatedHtmlDesc);
        assertTrue(paginatedHtmlDesc.contains("分页"));
    }
    
    @Test
    void testMaxResultsLimits() {
        // 测试最大结果数限制
        int excelMax = EnhancedReportGeneratorFactory.getFormatMaxResults("excel");
        assertTrue(excelMax > 0);
        
        int streamingExcelMax = EnhancedReportGeneratorFactory.getFormatMaxResults("streaming_excel");
        assertEquals(Integer.MAX_VALUE, streamingExcelMax);
        
        int paginatedHtmlMax = EnhancedReportGeneratorFactory.getFormatMaxResults("paginated_html");
        assertEquals(Integer.MAX_VALUE, paginatedHtmlMax);
    }
    
    @Test
    void testAvailableFormats() {
        // 测试可用格式列表
        List<String> availableFormats = new ArrayList<>(EnhancedReportGeneratorFactory.getAvailableFormats());
        assertNotNull(availableFormats);
        assertTrue(availableFormats.size() >= 4);
        assertTrue(availableFormats.contains("excel"));
        assertTrue(availableFormats.contains("html"));
        assertTrue(availableFormats.contains("streaming_excel"));
        assertTrue(availableFormats.contains("paginated_html"));
    }
    
    @Test
    void testFactoryReinitialization() {
        // 测试工厂重新初始化
        int originalSize = EnhancedReportGeneratorFactory.getAvailableFormats().size();
        
        EnhancedReportGeneratorFactory.clearGenerators();
        assertEquals(0, EnhancedReportGeneratorFactory.getAvailableFormats().size());
        
        EnhancedReportGeneratorFactory.reinitializeDefaults();
        assertEquals(originalSize, EnhancedReportGeneratorFactory.getAvailableFormats().size());
    }
    
    @Test
    void testLargeDataSetPerformance() throws Exception {
        // 测试大数据集性能
        List<MetricResult> largeResults = generateTestResults(10000);
        
        // 测试流式Excel性能
        ReportGeneratorInterface streamingGenerator = EnhancedReportGeneratorFactory.getGenerator("streaming_excel");
        long startTime = System.currentTimeMillis();
        String streamingPath = tempDir.resolve("large_streaming.xlsx").toString();
        streamingGenerator.generateReport(largeResults, streamingPath);
        long streamingDuration = System.currentTimeMillis() - startTime;
        
        // 测试分页HTML性能
        ReportGeneratorInterface paginatedGenerator = EnhancedReportGeneratorFactory.getGenerator("paginated_html");
        startTime = System.currentTimeMillis();
        String paginatedPath = tempDir.resolve("large_paginated").toString();
        paginatedGenerator.generateReport(largeResults, paginatedPath);
        long paginatedDuration = System.currentTimeMillis() - startTime;
        
        // 验证文件生成成功
        assertTrue(tempDir.resolve("large_streaming.xlsx").toFile().exists());
        assertTrue(tempDir.resolve("large_paginated").toFile().exists());
        
        // 记录性能数据（用于性能分析）
        System.out.printf("流式Excel生成耗时: %dms%n", streamingDuration);
        System.out.printf("分页HTML生成耗时: %dms%n", paginatedDuration);
    }
    
    /**
     * 生成测试数据
     */
    private List<MetricResult> generateTestResults(int count) {
        List<MetricResult> results = new ArrayList<>();
        
        String[] systems = {"系统A", "系统B", "系统C"};
        String[] dbTypes = {"MySQL", "PostgreSQL", "Oracle"};
        String[] metrics = {"CPU使用率", "内存使用率", "磁盘使用率"};
        
        for (int i = 0; i < count; i++) {
            MetricResult result = new MetricResult();
            result.setSystemName(systems[i % systems.length]);
            result.setDatabaseType(dbTypes[i % dbTypes.length]);
            result.setNodeIp("192.168.1." + (10 + i % 10));
            result.setMetricName(metrics[i % metrics.length]);
            result.setDescription("测试指标描述 " + (i + 1));
            result.setType("SINGLE");
            result.setValue(Math.random() * 100);
            result.setUnit("%");
            result.setThresholdLevel(Math.random() > 0.1 ? "正常" : "警告");
            result.setCollectTime(LocalDateTime.now().minusMinutes(i));
            result.setSuccess(Math.random() > 0.05);
            
            if (!result.isSuccess()) {
                result.setErrorMessage("测试错误信息 " + (i + 1));
            }
            
            // 添加一些多值指标
            if (i % 20 == 0) {
                result.setType("MULTI");
                result.setValue(null);
                
                List<Map<String, Object>> multiValues = new ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("时间", LocalDateTime.now().minusMinutes(j * 5).toString());
                    row.put("值", Math.random() * 100);
                    row.put("状态", Math.random() > 0.2 ? "正常" : "异常");
                    multiValues.add(row);
                }
                result.setMultiValues(multiValues);
            }
            
            results.add(result);
        }
        
        return results;
    }
}
