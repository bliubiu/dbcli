package com.dbcli.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 增强的报告生成器工厂
 * 支持多种报告格式，包括流式Excel和分页HTML
 */
public class EnhancedReportGeneratorFactory {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedReportGeneratorFactory.class);
    
    private static final Map<String, ReportGeneratorInterface> generators = new HashMap<>();
    
    static {
        // 注册所有可用的报告生成器
        registerGenerator(new ExcelReportGenerator());
        registerGenerator(new HtmlReportGenerator());
        registerGenerator(new StreamingExcelReportGenerator());
        registerGenerator(new PaginatedHtmlReportGenerator());
        registerGenerator("paginated_html_small", new PaginatedHtmlReportGenerator(50));
        registerGenerator("paginated_html_large", new PaginatedHtmlReportGenerator(200));
    }
    
    /**
     * 注册报告生成器
     */
    public static void registerGenerator(ReportGeneratorInterface generator) {
        String format = generator.getReportFormat();
        generators.put(format, generator);
        logger.debug("注册报告生成器: {} - {}", format, generator.getDescription());
    }
    
    /**
     * 注册报告生成器（指定格式名称）
     */
    public static void registerGenerator(String format, ReportGeneratorInterface generator) {
        generators.put(format, generator);
        logger.debug("注册报告生成器: {} - {}", format, generator.getDescription());
    }
    
    /**
     * 获取报告生成器
     */
    public static ReportGeneratorInterface getGenerator(String format) {
        ReportGeneratorInterface generator = generators.get(format);
        if (generator == null) {
            logger.warn("未找到格式为 {} 的报告生成器，使用默认Excel生成器", format);
            return generators.get("excel");
        }
        return generator;
    }
    
    /**
     * 根据数据量智能选择报告生成器
     */
    public static ReportGeneratorInterface getOptimalGenerator(String preferredFormat, int resultCount) {
        logger.info("为 {} 条记录选择最优报告生成器，首选格式: {}", resultCount, preferredFormat);
        
        // 根据数据量和格式选择最优生成器
        if ("excel".equalsIgnoreCase(preferredFormat)) {
            if (resultCount > 10000) {
                logger.info("数据量较大({}条)，选择流式Excel生成器", resultCount);
                return getGenerator("streaming_excel");
            } else {
                logger.info("数据量适中({}条)，选择标准Excel生成器", resultCount);
                return getGenerator("excel");
            }
        } else if ("html".equalsIgnoreCase(preferredFormat)) {
            if (resultCount > 1000) {
                logger.info("数据量较大({}条)，选择分页HTML生成器", resultCount);
                return getGenerator("paginated_html");
            } else {
                logger.info("数据量适中({}条)，选择标准HTML生成器", resultCount);
                return getGenerator("html");
            }
        }
        
        // 默认智能选择
        if (resultCount > 50000) {
            logger.info("超大数据量({}条)，选择流式Excel生成器", resultCount);
            return getGenerator("streaming_excel");
        } else if (resultCount > 5000) {
            logger.info("大数据量({}条)，选择分页HTML生成器", resultCount);
            return getGenerator("paginated_html");
        } else if (resultCount > 1000) {
            logger.info("中等数据量({}条)，选择流式Excel生成器", resultCount);
            return getGenerator("streaming_excel");
        } else {
            logger.info("小数据量({}条)，选择标准Excel生成器", resultCount);
            return getGenerator("excel");
        }
    }
    
    /**
     * 根据数据量和性能要求智能选择
     */
    public static ReportGeneratorInterface getOptimalGenerator(String preferredFormat, int resultCount, 
                                                             boolean prioritizePerformance) {
        if (prioritizePerformance) {
            logger.info("优先考虑性能，为 {} 条记录选择最优生成器", resultCount);
            
            if (resultCount > 20000) {
                return getGenerator("streaming_excel");
            } else if (resultCount > 2000) {
                return getGenerator("paginated_html");
            } else {
                return getGenerator(preferredFormat != null ? preferredFormat : "excel");
            }
        } else {
            return getOptimalGenerator(preferredFormat, resultCount);
        }
    }
    
    /**
     * 获取所有可用的报告格式
     */
    public static Set<String> getAvailableFormats() {
        return generators.keySet();
    }
    
    /**
     * 检查格式是否支持
     */
    public static boolean supportsFormat(String format) {
        return generators.containsKey(format);
    }
    
    /**
     * 检查格式是否支持（别名方法）
     */
    public static boolean isFormatSupported(String format) {
        return generators.containsKey(format);
    }
    
    /**
     * 获取所有支持的格式
     */
    public static Set<String> getSupportedFormats() {
        return generators.keySet();
    }
    
    /**
     * 获取最佳生成器
     */
    public static ReportGeneratorInterface getBestGenerator(String format, int resultCount) {
        return getOptimalGenerator(format, resultCount);
    }
    
    /**
     * 获取格式的详细信息
     */
    public static String getFormatDescription(String format) {
        ReportGeneratorInterface generator = generators.get(format);
        return generator != null ? generator.getDescription() : "未知格式";
    }
    
    /**
     * 获取格式支持的最大记录数
     */
    public static int getFormatMaxResults(String format) {
        ReportGeneratorInterface generator = generators.get(format);
        return generator != null ? generator.getMaxSupportedResults() : 0;
    }
    
    /**
     * 检查格式是否支持指定数量的记录
     */
    public static boolean canHandleResultCount(String format, int resultCount) {
        ReportGeneratorInterface generator = generators.get(format);
        return generator != null && generator.supportsResultCount(resultCount);
    }
    
    /**
     * 获取推荐的报告格式列表（按数据量排序）
     */
    public static String[] getRecommendedFormats(int resultCount) {
        if (resultCount > 50000) {
            return new String[]{"streaming_excel", "paginated_html", "excel"};
        } else if (resultCount > 10000) {
            return new String[]{"streaming_excel", "paginated_html", "excel", "html"};
        } else if (resultCount > 1000) {
            return new String[]{"paginated_html", "excel", "streaming_excel", "html"};
        } else {
            return new String[]{"excel", "html", "paginated_html", "streaming_excel"};
        }
    }
    
    /**
     * 打印所有可用生成器的信息
     */
    public static void printAvailableGenerators() {
        logger.info("可用的报告生成器:");
        for (Map.Entry<String, ReportGeneratorInterface> entry : generators.entrySet()) {
            ReportGeneratorInterface generator = entry.getValue();
            logger.info("  {} - {} (最大支持: {} 条记录)", 
                entry.getKey(), 
                generator.getDescription(),
                generator.getMaxSupportedResults() == Integer.MAX_VALUE ? "无限制" : generator.getMaxSupportedResults());
        }
    }
    
    /**
     * 清理所有注册的生成器
     */
    public static void clearGenerators() {
        generators.clear();
        logger.info("已清理所有注册的报告生成器");
    }
    
    /**
     * 重新初始化默认生成器
     */
    public static void reinitializeDefaults() {
        clearGenerators();
        
        // 重新注册默认生成器
        registerGenerator(new ExcelReportGenerator());
        registerGenerator(new HtmlReportGenerator());
        registerGenerator(new StreamingExcelReportGenerator());
        registerGenerator(new PaginatedHtmlReportGenerator());
        registerGenerator("paginated_html_small", new PaginatedHtmlReportGenerator(50));
        registerGenerator("paginated_html_large", new PaginatedHtmlReportGenerator(200));
        
        logger.info("已重新初始化默认报告生成器");
    }
}