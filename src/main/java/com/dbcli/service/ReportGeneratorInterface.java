package com.dbcli.service;

import com.dbcli.model.MetricResult;

import java.util.List;

/**
 * 报告生成器接口
 * 定义报告生成的核心方法
 */
public interface ReportGeneratorInterface {
    
    /**
     * 生成报告
     * 
     * @param results 指标结果列表
     * @param outputPath 输出路径
     * @throws Exception 生成异常
     */
    void generateReport(List<MetricResult> results, String outputPath) throws Exception;
    
    /**
     * 生成报告（带指标路径）
     * 
     * @param results 指标结果列表
     * @param metricsPath 指标配置路径
     * @param outputPath 输出路径
     * @throws Exception 生成异常
     */
    void generateReport(List<MetricResult> results, String metricsPath, String outputPath) throws Exception;
    
    /**
     * 获取报告格式
     * 
     * @return 报告格式（如 "excel", "html"）
     */
    String getReportFormat();
    
    /**
     * 获取报告文件扩展名
     * 
     * @return 文件扩展名（如 ".xlsx", ".html"）
     */
    String getFileExtension();
    
    /**
     * 检查是否支持指定的结果数量
     * 
     * @param resultCount 结果数量
     * @return 是否支持
     */
    boolean supportsResultCount(int resultCount);
    
    /**
     * 获取最大支持的结果数量
     * 
     * @return 最大结果数量，-1表示无限制
     */
    int getMaxSupportedResults();
    
    /**
     * 获取报告生成器描述
     * 
     * @return 描述信息
     */
    String getDescription();
}