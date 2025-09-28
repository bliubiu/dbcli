package com.dbcli.example;

import com.dbcli.model.MetricResult;
import com.dbcli.service.EnhancedReportGeneratorFactory;
import com.dbcli.service.ReportGeneratorInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告生成优化示例
 * 演示流式Excel和分页HTML的使用
 */
public class ReportGenerationExample {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationExample.class);
    
    public static void main(String[] args) {
        try {
            // 演示不同数据量的报告生成
            demonstrateSmallDataSet();
            demonstrateMediumDataSet();
            demonstrateLargeDataSet();
            
            // 演示智能选择
            demonstrateIntelligentSelection();
            
            // 演示所有可用格式
            demonstrateAvailableFormats();
            
        } catch (Exception e) {
            logger.error("报告生成示例执行失败", e);
        }
    }
    
    /**
     * 演示小数据集报告生成
     */
    private static void demonstrateSmallDataSet() throws Exception {
        logger.info("=== 小数据集报告生成演示 ===");
        
        List<MetricResult> results = generateSampleResults(100);
        
        // 使用标准Excel生成器
        ReportGeneratorInterface excelGenerator = EnhancedReportGeneratorFactory.getGenerator("excel");
        excelGenerator.generateReport(results, "reports/small_dataset_excel.xlsx");
        logger.info("小数据集Excel报告生成完成");
        
        // 使用标准HTML生成器
        ReportGeneratorInterface htmlGenerator = EnhancedReportGeneratorFactory.getGenerator("html");
        htmlGenerator.generateReport(results, "reports/small_dataset_html.html");
        logger.info("小数据集HTML报告生成完成");
    }
    
    /**
     * 演示中等数据集报告生成
     */
    private static void demonstrateMediumDataSet() throws Exception {
        logger.info("=== 中等数据集报告生成演示 ===");
        
        List<MetricResult> results = generateSampleResults(2000);
        
        // 使用分页HTML生成器
        ReportGeneratorInterface paginatedHtmlGenerator = EnhancedReportGeneratorFactory.getGenerator("paginated_html");
        paginatedHtmlGenerator.generateReport(results, "reports/medium_dataset_paginated");
        logger.info("中等数据集分页HTML报告生成完成");
        
        // 使用流式Excel生成器
        ReportGeneratorInterface streamingExcelGenerator = EnhancedReportGeneratorFactory.getGenerator("streaming_excel");
        streamingExcelGenerator.generateReport(results, "reports/medium_dataset_streaming.xlsx");
        logger.info("中等数据集流式Excel报告生成完成");
    }
    
    /**
     * 演示大数据集报告生成
     */
    private static void demonstrateLargeDataSet() throws Exception {
        logger.info("=== 大数据集报告生成演示 ===");
        
        List<MetricResult> results = generateSampleResults(15000);
        
        // 使用流式Excel生成器处理大数据量
        ReportGeneratorInterface streamingExcelGenerator = EnhancedReportGeneratorFactory.getGenerator("streaming_excel");
        long startTime = System.currentTimeMillis();
        streamingExcelGenerator.generateReport(results, "reports/large_dataset_streaming.xlsx");
        long duration = System.currentTimeMillis() - startTime;
        logger.info("大数据集流式Excel报告生成完成，耗时: {}ms", duration);
        
        // 使用分页HTML生成器
        ReportGeneratorInterface paginatedHtmlGenerator = EnhancedReportGeneratorFactory.getGenerator("paginated_html");
        startTime = System.currentTimeMillis();
        paginatedHtmlGenerator.generateReport(results, "reports/large_dataset_paginated");
        duration = System.currentTimeMillis() - startTime;
        logger.info("大数据集分页HTML报告生成完成，耗时: {}ms", duration);
    }
    
    /**
     * 演示智能选择功能
     */
    private static void demonstrateIntelligentSelection() throws Exception {
        logger.info("=== 智能选择功能演示 ===");
        
        // 测试不同数据量的智能选择
        int[] testSizes = {500, 2000, 8000, 25000, 60000};
        
        for (int size : testSizes) {
            logger.info("--- 测试数据量: {} ---", size);
            
            // Excel格式智能选择
            ReportGeneratorInterface excelOptimal = EnhancedReportGeneratorFactory.getOptimalGenerator("excel", size);
            logger.info("Excel格式最优选择: {} - {}", excelOptimal.getReportFormat(), excelOptimal.getDescription());
            
            // HTML格式智能选择
            ReportGeneratorInterface htmlOptimal = EnhancedReportGeneratorFactory.getOptimalGenerator("html", size);
            logger.info("HTML格式最优选择: {} - {}", htmlOptimal.getReportFormat(), htmlOptimal.getDescription());
            
            // 性能优先选择
            ReportGeneratorInterface performanceOptimal = EnhancedReportGeneratorFactory.getOptimalGenerator("excel", size, true);
            logger.info("性能优先选择: {} - {}", performanceOptimal.getReportFormat(), performanceOptimal.getDescription());
            
            // 获取推荐格式
            String[] recommended = EnhancedReportGeneratorFactory.getRecommendedFormats(size);
            logger.info("推荐格式顺序: {}", String.join(" > ", recommended));
            
            logger.info("");
        }
    }
    
    /**
     * 演示所有可用格式
     */
    private static void demonstrateAvailableFormats() {
        logger.info("=== 所有可用格式演示 ===");
        
        // 打印所有可用生成器
        EnhancedReportGeneratorFactory.printAvailableGenerators();
        
        // 测试格式支持情况
        String[] testFormats = {"excel", "html", "streaming_excel", "paginated_html", "unknown_format"};
        
        for (String format : testFormats) {
            boolean supported = EnhancedReportGeneratorFactory.isFormatSupported(format);
            logger.info("格式 {} 支持状态: {}", format, supported ? "支持" : "不支持");
            
            if (supported) {
                String description = EnhancedReportGeneratorFactory.getFormatDescription(format);
                int maxResults = EnhancedReportGeneratorFactory.getFormatMaxResults(format);
                logger.info("  描述: {}", description);
                logger.info("  最大支持记录数: {}", maxResults == Integer.MAX_VALUE ? "无限制" : maxResults);
                
                // 测试不同数据量的支持情况
                int[] testCounts = {1000, 10000, 100000};
                for (int count : testCounts) {
                    boolean canHandle = EnhancedReportGeneratorFactory.canHandleResultCount(format, count);
                    logger.info("  支持{}条记录: {}", count, canHandle ? "是" : "否");
                }
            }
            logger.info("");
        }
    }
    
    /**
     * 生成示例数据
     */
    private static List<MetricResult> generateSampleResults(int count) {
        List<MetricResult> results = new ArrayList<>();
        
        String[] systems = {"生产系统A", "生产系统B", "测试系统", "开发系统"};
        String[] dbTypes = {"MySQL", "PostgreSQL", "Oracle", "SQL Server"};
        String[] metrics = {"CPU使用率", "内存使用率", "磁盘使用率", "连接数", "QPS", "响应时间"};
        String[] ips = {"192.168.1.10", "192.168.1.11", "192.168.1.12", "192.168.1.13"};
        
        for (int i = 0; i < count; i++) {
            MetricResult result = new MetricResult();
            result.setSystemName(systems[i % systems.length]);
            result.setDatabaseType(dbTypes[i % dbTypes.length]);
            result.setNodeIp(ips[i % ips.length]);
            result.setMetricName(metrics[i % metrics.length]);
            result.setDescription("指标描述 " + (i + 1));
            result.setType("SINGLE");
            result.setValue(Math.random() * 100);
            result.setUnit("%");
            result.setThresholdLevel(Math.random() > 0.1 ? "正常" : "警告");
            result.setCollectTime(LocalDateTime.now().minusMinutes(i));
            result.setSuccess(Math.random() > 0.05); // 95%成功率
            
            if (!result.isSuccess()) {
                result.setErrorMessage("模拟错误信息 " + (i + 1));
            }
            
            // 添加一些多值指标
            if (i % 10 == 0) {
                result.setType("MULTI");
                result.setValue(null);
                
                List<Map<String, Object>> multiValues = new ArrayList<>();
                for (int j = 0; j < 5; j++) {
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