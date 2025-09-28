package com.dbcli.service;

import com.dbcli.model.MetricResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HTML报告生成器
 * - 对齐 Excel 的阈值规则（threshold.rules/column/value 等）进行高亮评估
 * - 默认从 metrics 目录加载各库型的指标阈值定义
 * - 若未找到阈值定义，则回退到 MetricResult.thresholdLevel（执行层旧写法）或不高亮
 */
public class HtmlReportGenerator implements ReportGeneratorInterface {
    private static final Logger logger = LoggerFactory.getLogger(HtmlReportGenerator.class);

    // 阈值缓存：dbType -> (metricName/description -> 阈值定义)
    private Map<String, Map<String, ThresholdSpec>> thresholdsCache = new HashMap<>();
    private boolean thresholdsLoaded = false;

    public void generate(List<MetricResult> results, String outputPath, String metricsPath) throws IOException {
        // 懒加载指标阈值（优先使用传入的指标目录，否则回退到默认 metrics）
        ensureThresholdsLoaded(metricsPath != null && !metricsPath.trim().isEmpty() ? metricsPath.trim() : "metrics");

        logger.info("开始生成HTML报告: {}", outputPath);
        try {
            // 解析输出目录与首页文件（统一使用 Path.resolve，避免字符串拼接导致的双分隔符）
            String outDirPath;
            String indexPath;
            boolean isHtmlFile = outputPath != null && outputPath.toLowerCase().endsWith(".html");
            if (isHtmlFile) {
                java.nio.file.Path outPath = java.nio.file.Paths.get(outputPath);
                java.nio.file.Path parent = outPath.getParent();
                if (parent != null) {
                    com.dbcli.util.FileUtil.createDirectoryIfNotExists(parent.toString());
                    outDirPath = parent.toString();
                } else {
                    outDirPath = ".";
                }
                indexPath = outPath.normalize().toString();
            } else {
                java.nio.file.Path outPath = java.nio.file.Paths.get(outputPath);
                com.dbcli.util.FileUtil.createDirectoryIfNotExists(outPath.toString());
                String date = java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                String fileName = "db_metrics_report_" + date + ".html";
                java.nio.file.Path index = outPath.resolve(fileName).normalize();
                indexPath = index.toString();
                outDirPath = outPath.toString();
            }

            // 按类型拆分：SINGLE/MULTI
            java.util.Map<String, java.util.List<MetricResult>> singleByType = new java.util.HashMap<>();
            java.util.Map<String, java.util.Map<String, java.util.List<MetricResult>>> multiGrouped = new java.util.HashMap<>();
            for (MetricResult r : results) {
                String t = dbTypeKey(r.getDbType());
                String mt = r.getMetricType() != null ? r.getMetricType().toUpperCase(java.util.Locale.ROOT) : "";
                if ("MULTI".equals(mt)) {
                    String desc = (r.getMetricDescription() != null && !r.getMetricDescription().isEmpty())
                            ? r.getMetricDescription()
                            : (r.getMetricName() != null ? r.getMetricName() : "未命名指标");
                    multiGrouped.computeIfAbsent(t, k -> new java.util.HashMap<>())
                            .computeIfAbsent(desc, k -> new java.util.ArrayList<>())
                            .add(r);
                } else {
                    singleByType.computeIfAbsent(t, k -> new java.util.ArrayList<>()).add(r);
                }
            }

            // 生成单文件（标签页）报告：每个“单值/多值工作表”作为一个 HTML Sheet
            String indexHtml = renderTabbedReport(singleByType, multiGrouped);
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
                    java.nio.file.Paths.get(indexPath),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(indexHtml);
            }
            logger.info("HTML标签页报告生成完成: {}", indexPath);
        } catch (Exception e) {
            logger.error("生成HTML报告失败: {}", e.getMessage(), e);
            throw new IOException("生成HTML报告失败", e);
        }
    }

    /**
     * 生成HTML内容
     */
    private String generateHtmlContent(List<MetricResult> results) {
        StringBuilder html = new StringBuilder();

        // HTML头部
        html.append(generateHtmlHeader());

        // 报告标题和摘要
        html.append(generateReportHeader(results));

        // 数据库节点汇总
        html.append(generateNodeSummary(results));

        // 详细结果
        html.append(generateDetailedResults(results));

        // HTML尾部
        html.append(generateHtmlFooter());

        return html.toString();
    }

    /**
     * 生成HTML头部
     */
    private String generateHtmlHeader() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>数据库指标报告</title>\n" +
                "    <style>\n" +
                generateCssStyles() +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n";
    }

    /**
     * 生成CSS样式
     */
    private String generateCssStyles() {
        return "        * {\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            box-sizing: border-box;\n" +
                "        }\n" +
                "        \n" +
                "        body {\n" +
                "            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
                "            line-height: 1.6;\n" +
                "            color: #333;\n" +
                "            background-color: #f5f5f5;\n" +
                "        }\n" +
                "        \n" +
                "        .container {\n" +
                "            max-width: 100%;\n" +
                "            margin: 0 auto;\n" +
                "            padding: 20px;\n" +
                "        }\n" +
                "        \n" +
                "        .header {\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            color: white;\n" +
                "            padding: 30px;\n" +
                "            border-radius: 10px;\n" +
                "            margin-bottom: 30px;\n" +
                "            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);\n" +
                "        }\n" +
                "        \n" +
                "        .header h1 {\n" +
                "            font-size: 2.5em;\n" +
                "            margin-bottom: 10px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        \n" +
                "        .header .subtitle {\n" +
                "            text-align: center;\n" +
                "            font-size: 1.2em;\n" +
                "            opacity: 0.9;\n" +
                "        }\n" +
                "        \n" +
                "        .summary {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));\n" +
                "            gap: 20px;\n" +
                "            margin-bottom: 30px;\n" +
                "        }\n" +
                "        \n" +
                "        .summary-card {\n" +
                "            background: white;\n" +
                "            padding: 25px;\n" +
                "            border-radius: 10px;\n" +
                "            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);\n" +
                "            text-align: center;\n" +
                "            transition: transform 0.3s ease;\n" +
                "        }\n" +
                "        \n" +
                "        .summary-card:hover {\n" +
                "            transform: translateY(-5px);\n" +
                "        }\n" +
                "        \n" +
                "        .summary-card h3 {\n" +
                "            color: #667eea;\n" +
                "            margin-bottom: 10px;\n" +
                "            font-size: 1.1em;\n" +
                "        }\n" +
                "        \n" +
                "        .summary-card .number {\n" +
                "            font-size: 2.5em;\n" +
                "            font-weight: bold;\n" +
                "            color: #333;\n" +
                "            margin-bottom: 5px;\n" +
                "        }\n" +
                "        \n" +
                "        .summary-card .label {\n" +
                "            color: #666;\n" +
                "            font-size: 0.9em;\n" +
                "        }\n" +
                "        \n" +
                "        .section {\n" +
                "            background: white;\n" +
                "            margin-bottom: 30px;\n" +
                "            border-radius: 10px;\n" +
                "            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        \n" +
                "        .section-header {\n" +
                "            background: #f8f9fa;\n" +
                "            padding: 20px 30px;\n" +
                "            border-bottom: 1px solid #e9ecef;\n" +
                "        }\n" +
                "        \n" +
                "        .section-header h2 {\n" +
                "            color: #495057;\n" +
                "            font-size: 1.5em;\n" +
                "        }\n" +
                "        \n" +
                "        .section-content {\n" +
                "            padding: 30px;\n" +
                "            overflow-x: auto;\n" +
                "            overflow-y: hidden;\n" +
                "        }\n" +
                "        \n" +
                "        .node-card {\n" +
                "            border: 1px solid #e9ecef;\n" +
                "            border-radius: 8px;\n" +
                "            margin-bottom: 20px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        \n" +
                "        .node-header {\n" +
                "            background: #f8f9fa;\n" +
                "            padding: 15px 20px;\n" +
                "            border-bottom: 1px solid #e9ecef;\n" +
                "        }\n" +
                "        \n" +
                "        .node-title {\n" +
                "            font-weight: bold;\n" +
                "            color: #495057;\n" +
                "            margin-bottom: 5px;\n" +
                "        }\n" +
                "        \n" +
                "        .node-info {\n" +
                "            font-size: 0.9em;\n" +
                "            color: #6c757d;\n" +
                "        }\n" +
                "        \n" +
                "        .metrics-grid {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));\n" +
                "            gap: 20px;\n" +
                "            padding: 20px;\n" +
                "        }\n" +
                "        \n" +
                "        .metric-card {\n" +
                "            border: 1px solid #e9ecef;\n" +
                "            border-radius: 6px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        \n" +
                "        .metric-header {\n" +
                "            background: #667eea;\n" +
                "            color: white;\n" +
                "            padding: 12px 15px;\n" +
                "            font-weight: bold;\n" +
                "            font-size: 0.9em;\n" +
                "        }\n" +
                "        \n" +
                "        .metric-content {\n" +
                "            padding: 15px;\n" +
                "        }\n" +
                "        \n" +
                "        .metric-table {\n" +
                "            width: 100%;\n" +
                "            border-collapse: collapse;\n" +
                "            font-size: 0.85em;\n" +
                "        }\n" +
                "        \n" +
                "        .metric-table th,\n" +
                "        .metric-table td {\n" +
                "            padding: 8px 10px;\n" +
                "            text-align: left;\n" +
                "            border-bottom: 1px solid #e9ecef;\n" +
                "            white-space: nowrap;\n" +
                "            vertical-align: top;\n" +
                "        }\n" +
                "        \n" +
                "        .metric-table th {\n" +
                "            background: #f8f9fa;\n" +
                "            font-weight: bold;\n" +
                "            color: #495057;\n" +
                "            position: sticky;\n" +
                "            top: 0;\n" +
                "            z-index: 2;\n" +
                "        }\n" +
                "        \n" +
                "        .metric-table tr:hover {\n" +
                "            background: #f8f9fa;\n" +
                "        }\n" +
                "        \n" +
                "        .status-success {\n" +
                "            color: #28a745;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        \n" +
                "        .status-error {\n" +
                "            color: #dc3545;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        \n" +
                "        .risk-high {\n" +
                "            background: #ff0000;\n" +
                "            color: #ffffff;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        \n" +
                "        .risk-medium {\n" +
                "            background: #ffff00;\n" +
                "            color: #000000;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        \n" +
                "        .error-message {\n" +
                "            background: #f8d7da;\n" +
                "            color: #721c24;\n" +
                "            padding: 10px;\n" +
                "            border-radius: 4px;\n" +
                "            font-size: 0.9em;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        \n" +
                "        .footer {\n" +
                "            text-align: center;\n" +
                "            padding: 30px;\n" +
                "            color: #6c757d;\n" +
                "            font-size: 0.9em;\n" +
                "            border-top: 1px solid #e9ecef;\n" +
                "            margin-top: 30px;\n" +
                "        }\n" +
                "        \n" +
                "        .tabbar {\n" +
                "            display: flex;\n" +
                "            flex-wrap: wrap;\n" +
                "            gap: 8px;\n" +
                "            padding: 10px 20px;\n" +
                "            background: #f0f2f5;\n" +
                "            border-bottom: 1px solid #e9ecef;\n" +
                "            position: sticky;\n" +
                "            top: 0;\n" +
                "            z-index: 3;\n" +
                "        }\n" +
                "        .tab-btn {\n" +
                "            padding: 8px 14px;\n" +
                "            border: 1px solid #cfd3dc;\n" +
                "            border-bottom: none;\n" +
                "            border-top-left-radius: 6px;\n" +
                "            border-top-right-radius: 6px;\n" +
                "            background: #eaecef;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .tab-btn.active {\n" +
                "            background: #ffffff;\n" +
                "            color: #333;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        .sheet { display: none; }\n" +
                "        .sheet.active { display: block; }\n" +
                "        \n" +
                "        @media (max-width: 768px) {\n" +
                "            .container {\n" +
                "                padding: 10px;\n" +
                "            }\n" +
                "            \n" +
                "            .header {\n" +
                "                padding: 20px;\n" +
                "            }\n" +
                "            \n" +
                "            .header h1 {\n" +
                "                font-size: 2em;\n" +
                "            }\n" +
                "            \n" +
                "            .summary {\n" +
                "                grid-template-columns: 1fr;\n" +
                "            }\n" +
                "            \n" +
                "            .metrics-grid {\n" +
                "                grid-template-columns: 1fr;\n" +
                "                padding: 10px;\n" +
                "            }\n" +
                "            \n" +
                "            .section-content {\n" +
                "                padding: 15px;\n" +
                "            }\n" +
                "        }\n";
    }

    /**
     * 生成报告头部
     */
    private String generateReportHeader(List<MetricResult> results) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h1>数据库指标报告</h1>\n" +
                "            <div class=\"subtitle\">生成时间: " + timestamp + "</div>\n" +
                "        </div>\n";
    }

    /**
     * 生成节点汇总信息
     */
    private String generateNodeSummary(List<MetricResult> results) {
        int totalNodes = (int) results.stream().map(MetricResult::getSystemName).distinct().count();
        int successfulNodes = (int) results.stream().filter(MetricResult::isSuccess).map(MetricResult::getSystemName).distinct().count();
        int totalMetrics = results.size();
        int successfulMetrics = (int) results.stream().filter(MetricResult::isSuccess).count();

        return "        <div class=\"summary\">\n" +
                "            <div class=\"summary-card\">\n" +
                "                <h3>数据库节点</h3>\n" +
                "                <div class=\"number\">" + totalNodes + "</div>\n" +
                "                <div class=\"label\">总计</div>\n" +
                "            </div>\n" +
                "            <div class=\"summary-card\">\n" +
                "                <h3>成功连接</h3>\n" +
                "                <div class=\"number\">" + successfulNodes + "</div>\n" +
                "                <div class=\"label\">节点</div>\n" +
                "            </div>\n" +
                "            <div class=\"summary-card\">\n" +
                "                <h3>指标查询</h3>\n" +
                "                <div class=\"number\">" + totalMetrics + "</div>\n" +
                "                <div class=\"label\">总计</div>\n" +
                "            </div>\n" +
                "            <div class=\"summary-card\">\n" +
                "                <h3>成功查询</h3>\n" +
                "                <div class=\"number\">" + successfulMetrics + "</div>\n" +
                "                <div class=\"label\">指标</div>\n" +
                "            </div>\n" +
                "        </div>\n";
    }

    /**
     * 生成详细结果
     */
    private String generateDetailedResults(List<MetricResult> results) {
        StringBuilder content = new StringBuilder();

        content.append("        <div class=\"section\">\n")
                .append("            <div class=\"section-header\">\n")
                .append("                <h2>详细结果</h2>\n")
                .append("            </div>\n")
                .append("            <div class=\"section-content\">\n");

        // 按节点分组显示结果
        Map<String, List<MetricResult>> nodeResults = new HashMap<>();
        for (MetricResult result : results) {
            nodeResults.computeIfAbsent(result.getNodeName(), k -> new ArrayList<>()).add(result);
        }

        for (Map.Entry<String, List<MetricResult>> entry : nodeResults.entrySet()) {
            content.append(generateNodeResults(entry.getKey(), entry.getValue()));
        }

        content.append("            </div>\n")
                .append("        </div>\n");

        return content.toString();
    }

    /**
     * 生成单个节点的结果
     */
    private String generateNodeResults(String nodeName, List<MetricResult> nodeResults) {
        StringBuilder content = new StringBuilder();

        // 获取节点信息
        MetricResult firstResult = nodeResults.get(0);
        String nodeInfo = String.format("%s (%s)",
                firstResult.getDbType(),
                firstResult.getSystemName());

        content.append("                <div class=\"node-card\">\n")
                .append("                    <div class=\"node-header\">\n")
                .append("                        <div class=\"node-title\">").append(escapeHtml(nodeName)).append("</div>\n")
                .append("                        <div class=\"node-info\">").append(escapeHtml(nodeInfo)).append("</div>\n")
                .append("                    </div>\n")
                .append("                    <div class=\"metrics-grid\">\n");

        // 生成每个指标的结果
        for (MetricResult result : nodeResults) {
            content.append(generateMetricResult(result));
        }

        content.append("                    </div>\n")
                .append("                </div>\n");

        return content.toString();
    }

    /**
     * 生成单个指标结果（含阈值规则评估）
     */
    private String generateMetricResult(MetricResult result) {
        StringBuilder content = new StringBuilder();

        // 采用与 Excel 对齐的阈值规则评估：若无阈值定义则回退 MetricResult.thresholdLevel
        String evaluatedRisk = evaluateRiskLevel(result);
        String riskClass = "";
        if (evaluatedRisk != null) {
            if ("high".equalsIgnoreCase(evaluatedRisk)) {
                riskClass = "risk-high";
            } else if ("medium".equalsIgnoreCase(evaluatedRisk)) {
                riskClass = "risk-medium";
            }
        }

        content.append("                        <div class=\"metric-card");
        if (!riskClass.isEmpty()) {
            content.append(" ").append(riskClass);
        }
        content.append("\">\n")
                .append("                            <div class=\"metric-header\">\n")
                .append("                                ").append(escapeHtml(result.getMetricName() != null ? result.getMetricName() : "未命名指标")).append("\n")
                .append("                            </div>\n")
                .append("                            <div class=\"metric-content\">\n");

        if (result.isSuccess()) {
            content.append("                                <div class=\"status-success\">✓ 执行成功</div>\n");

            // 将数据统一转为 List<Map<String,Object>> 以便渲染
            List<Map<String, Object>> data = normalizeData(result);
            if (data != null && !data.isEmpty()) {
                content.append("                                <table class=\"metric-table\">\n");

                // 表头
                Map<String, Object> firstRow = data.get(0);
                content.append("                                    <thead>\n")
                        .append("                                        <tr>\n");
                for (String column : firstRow.keySet()) {
                    content.append("                                            <th>").append(escapeHtml(column)).append("</th>\n");
                }
                content.append("                                        </tr>\n")
                        .append("                                    </thead>\n");

                // 数据行
                content.append("                                    <tbody>\n");
                for (Map<String, Object> row : data) {
                    content.append("                                        <tr>\n");
                    for (String column : firstRow.keySet()) {
                        Object value = row.get(column);
                        String displayValue = value != null ? escapeHtml(value.toString()) : "";
                        content.append("                                            <td>").append(displayValue).append("</td>\n");
                    }
                    content.append("                                        </tr>\n");
                }
                content.append("                                    </tbody>\n")
                        .append("                                </table>\n");
            } else {
                content.append("                                <p>无数据返回</p>\n");
            }
        } else {
            content.append("                                <div class=\"status-error\">✗ 执行失败</div>\n");
            if (result.getErrorMessage() != null) {
                content.append("                                <div class=\"error-message\">\n")
                        .append("                                    ").append(escapeHtml(result.getErrorMessage())).append("\n")
                        .append("                                </div>\n");
            }
        }

        content.append("                            </div>\n")
                .append("                        </div>\n");

        return content.toString();
    }

    /**
     * HTML尾部
     */
    private String generateHtmlFooter() {
        return "        <div class=\"footer\">\n" +
                "            <p>报告由 dbcli 工具生成 | " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>\n";
    }

    /**
     * HTML转义
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&");
                    break;
                case '<':
                    sb.append("<");
                    break;
                case '>':
                    sb.append(">");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    // ========================== 多页面渲染（首页/单值/多值） ==========================

    private String renderIndexPage(String singlePageName, java.util.List<String[]> multiLinks) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>数据库指标报告 - 首页</title>\n<style>\n")
                .append(generateCssStyles())
                .append("        .link-list { padding: 20px; }\n")
                .append("        .link-list ul { list-style: none; }\n")
                .append("        .link-list li { margin: 8px 0; }\n")
                .append("        a { color: #3366cc; text-decoration: none; }\n")
                .append("        a:hover { text-decoration: underline; }\n")
                .append("</style>\n</head>\n<body>\n");

        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        html.append("<div class=\"container\">\n")
                .append("<div class=\"header\"><h1>数据库指标报告 - 首页</h1>")
                .append("<div class=\"subtitle\">生成时间: ").append(escapeHtml(timestamp)).append("</div></div>\n")
                .append("<div class=\"section\"><div class=\"section-header\"><h2>入口</h2></div>\n")
                .append("<div class=\"section-content link-list\">\n<ul>\n")
                .append("<li><a href=\"").append(escapeHtml(singlePageName)).append("\">单值指标汇总</a></li>\n");

        // 多值指标链接，按 dbType 分组排序
        multiLinks.sort((a, b) -> {
            int t = a[0].compareToIgnoreCase(b[0]);
            if (t != 0) return t;
            return a[1].compareToIgnoreCase(b[1]);
        });
        for (String[] link : multiLinks) {
            String type = link[0];
            String desc = link[1];
            String file = link[2];
            String label = type + "-" + desc;
            html.append("<li><a href=\"").append(escapeHtml(file)).append("\">")
                    .append(escapeHtml(label)).append("</a></li>\n");
        }

        html.append("</ul>\n</div></div>\n<div class=\"footer\"><p>报告由 dbcli 工具生成 | ")
                .append(escapeHtml(timestamp)).append("</p></div>\n</div>\n</body>\n</html>\n");

        return html.toString();
    }

    // 新增：首页渲染（按数据库类型列出单值指标页面入口）
    private String renderIndexPage(java.util.List<String[]> singleLinks, java.util.List<String[]> multiLinks) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>数据库指标报告 - 首页</title>\n<style>\n")
                .append(generateCssStyles())
                .append("        .link-list { padding: 20px; }\n")
                .append("        .link-list ul { list-style: none; }\n")
                .append("        .link-list li { margin: 8px 0; }\n")
                .append("        a { color: #3366cc; text-decoration: none; }\n")
                .append("        a:hover { text-decoration: underline; }\n")
                .append("</style>\n</head>\n<body>\n");

        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        html.append("<div class=\"container\">\n")
                .append("<div class=\"header\"><h1>数据库指标报告 - 首页</h1>")
                .append("<div class=\"subtitle\">生成时间: ").append(escapeHtml(timestamp)).append("</div></div>\n")
                .append("<div class=\"section\"><div class=\"section-header\"><h2>入口</h2></div>\n")
                .append("<div class=\"section-content link-list\">\n<ul>\n");

        // 单值指标入口（每类数据库一个页面）
        singleLinks.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        for (String[] link : singleLinks) {
            String type = link[0];
            String file = link[1];
            String label = "单值指标 - " + type;
            html.append("<li><a href=\"").append(escapeHtml(file)).append("\">")
                    .append(escapeHtml(label)).append("</a></li>\n");
        }

        // 多值指标链接，按 dbType 分组排序
        multiLinks.sort((a, b) -> {
            int t = a[0].compareToIgnoreCase(b[0]);
            if (t != 0) return t;
            return a[1].compareToIgnoreCase(b[1]);
        });
        for (String[] link : multiLinks) {
            String type = link[0];
            String desc = link[1];
            String file = link[2];
            String label = type + "-" + desc;
            html.append("<li><a href=\"").append(escapeHtml(file)).append("\">")
                    .append(escapeHtml(label)).append("</a></li>\n");
        }

        html.append("</ul>\n</div></div>\n<div class=\"footer\"><p>报告由 dbcli 工具生成 | ")
                .append(escapeHtml(timestamp)).append("</p></div>\n</div>\n</body>\n</html>\n");

        return html.toString();
    }

    private String renderSingleMetricsPage(java.util.List<MetricResult> results) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>单值指标汇总</title>\n<style>\n")
                .append(generateCssStyles())
                .append("</style>\n</head>\n<body>\n<div class=\"container\">\n")
                .append("<div class=\"header\"><h1>单值指标汇总</h1></div>\n")
                .append("<div class=\"section\"><div class=\"section-header\"><h2>单值指标</h2></div>\n")
                .append("<div class=\"section-content\">\n<table class=\"metric-table\">\n<thead>\n<tr>\n")
                .append("<th>系统名称</th><th>数据库名称</th><th>节点IP</th><th>单值指标描述</th><th>执行时间</th>\n")
                .append("</tr>\n</thead>\n<tbody>\n");

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (MetricResult r : results) {
            String mt = r.getMetricType() != null ? r.getMetricType().toUpperCase(java.util.Locale.ROOT) : "";
            if (!"SINGLE".equals(mt)) continue;
            if (!r.isSuccess()) continue; // 仅展示成功结果

            // 提取值：优先 value，其次从标准化数据中取第一值
            Object displayVal = r.getValue();
            if (displayVal == null) {
                java.util.List<java.util.Map<String, Object>> data = normalizeData(r);
                if (data != null) {
                    Object v = tryExtractSingleValue(data);
                    if (v != null) displayVal = v;
                }
            }
            String descWithVal = (r.getMetricDescription() != null ? r.getMetricDescription() : (r.getMetricName() != null ? r.getMetricName() : "未命名指标"))
                    + "：" + (displayVal != null ? displayVal.toString() : "");

            String execTime = formatTime(r.getExecuteTime(), fmt);
            html.append("<tr>")
                    .append("<td>").append(escapeHtml(nullToEmpty(r.getSystemName()))).append("</td>")
                    .append("<td>").append(escapeHtml(nullToEmpty(r.getDatabaseName()))).append("</td>")
                    .append("<td>").append(escapeHtml(nullToEmpty(r.getNodeIp()))).append("</td>")
                    .append("<td>").append(escapeHtml(descWithVal)).append("</td>")
                    .append("<td>").append(escapeHtml(execTime)).append("</td>")
                    .append("</tr>\n");
        }

        html.append("</tbody>\n</table>\n</div></div>\n")
                .append("<div class=\"footer\"><p>报告由 dbcli 工具生成 | ")
                .append(escapeHtml(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .append("</p></div>\n</div>\n</body>\n</html>\n");

        return html.toString();
    }

    // 新增：单值指标页面（单库型），表头“系统名称、数据库名称、节点IP、各单值指标描述、执行时间”
    // 行分组：系统名称 + 数据库名称 + 节点IP
    private String renderSingleMetricsPageForType(String dbType, java.util.List<MetricResult> results) {
        String t = dbTypeKey(dbType);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>单值指标汇总 - ").append(escapeHtml(t)).append("</title>\n<style>\n")
                .append(generateCssStyles())
                .append("</style>\n</head>\n<body>\n<div class=\"container\">\n")
                .append("<div class=\"header\"><h1>单值指标汇总 - ").append(escapeHtml(t)).append("</h1></div>\n");

        // 过滤：仅该类型、SINGLE、成功
        java.util.List<MetricResult> list = new java.util.ArrayList<>();
        for (MetricResult r : results) {
            String mt = (r.getMetricType() != null) ? r.getMetricType().toUpperCase(java.util.Locale.ROOT) : "";
            if (!"SINGLE".equals(mt)) continue;
            if (!r.isSuccess()) continue;
            if (!dbTypeKey(r.getDbType()).equals(t)) continue;
            list.add(r);
        }
        if (list.isEmpty()) {
            html.append("<div class=\"section\"><div class=\"section-content\"><p>无数据</p></div></div>\n")
                    .append("<div class=\"footer\"><p>报告由 dbcli 工具生成 | ")
                    .append(escapeHtml(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                    .append("</p></div>\n</div>\n</body>\n</html>\n");
            return html.toString();
        }

        // 计算列：唯一指标名顺序 + 描述映射
        java.util.LinkedHashSet<String> metricNames = new java.util.LinkedHashSet<>();
        java.util.Map<String, String> nameToDesc = new java.util.LinkedHashMap<>();
        for (MetricResult r : list) {
            String name = r.getMetricName();
            if (name == null) continue;
            if (!metricNames.contains(name)) {
                metricNames.add(name);
                String desc = (r.getMetricDescription() != null && !r.getMetricDescription().isEmpty())
                        ? r.getMetricDescription() : name;
                nameToDesc.put(name, desc);
            }
        }

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        html.append("<div class=\"section\"><div class=\"section-header\"><h2>")
                .append(escapeHtml(t)).append("</h2></div>\n")
                .append("<div class=\"section-content\">\n<table class=\"metric-table\">\n<thead>\n<tr>\n")
                .append("<th>系统名称</th><th>数据库名称</th><th>节点IP</th>");
        for (String metricName : metricNames) {
            String desc = nameToDesc.get(metricName);
            html.append("<th>").append(escapeHtml(desc != null ? desc : metricName)).append("</th>");
        }
        html.append("<th>执行时间</th>\n</tr>\n</thead>\n<tbody>\n");

        // 按 系统|数据库|IP 分组（保持插入顺序）
        java.util.Map<String, java.util.List<MetricResult>> grouped = new java.util.LinkedHashMap<>();
        for (MetricResult r : list) {
            String key = (r.getSystemName() != null ? r.getSystemName() : "")
                    + "|" + (r.getDatabaseName() != null ? r.getDatabaseName() : "")
                    + "|" + (r.getNodeIp() != null ? r.getNodeIp() : "");
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
        }

        for (java.util.Map.Entry<String, java.util.List<MetricResult>> e : grouped.entrySet()) {
            String key = e.getKey();
            java.util.List<MetricResult> grp = e.getValue();

            // 解析 key
            String sysName = "";
            String dbName = "";
            String nodeIp = "";
            String[] parts = key.split("\\|", -1);
            if (parts.length >= 1) sysName = parts[0];
            if (parts.length >= 2) dbName = parts[1];
            if (parts.length >= 3) nodeIp = parts[2];

            // map: metricName -> MetricResult
            java.util.Map<String, MetricResult> rmap = new java.util.HashMap<>();
            for (MetricResult r : grp) {
                if (r.getMetricName() != null) rmap.put(r.getMetricName(), r);
            }

            String execTime = "";
            if (!grp.isEmpty()) {
                execTime = formatTime(grp.get(0).getExecuteTime(), fmt);
            }

            html.append("<tr>")
                    .append("<td>").append(escapeHtml(sysName)).append("</td>")
                    .append("<td>").append(escapeHtml(dbName)).append("</td>")
                    .append("<td>").append(escapeHtml(nodeIp)).append("</td>");

            // 输出各指标列
            for (String metricName : metricNames) {
                MetricResult mr = rmap.get(metricName);
                if (mr != null) {
                    Object value = mr.getValue();
                    String cls = determineValueClass(t, mr, value);
                    String valStr = value != null ? value.toString() : "";
                    if (cls != null && !cls.isEmpty()) {
                        html.append("<td class=\"").append(escapeHtml(cls)).append("\">")
                                .append(escapeHtml(valStr)).append("</td>");
                    } else {
                        html.append("<td>").append(escapeHtml(valStr)).append("</td>");
                    }
                } else {
                    html.append("<td></td>");
                }
            }

            html.append("<td>").append(escapeHtml(execTime)).append("</td>")
                    .append("</tr>\n");
        }

        html.append("</tbody>\n</table>\n</div></div>\n")
                .append("<div class=\"footer\"><p>报告由 dbcli 工具生成 | ")
                .append(escapeHtml(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .append("</p></div>\n</div>\n</body>\n</html>\n");

        return html.toString();
    }

    private String renderMultiMetricPage(String dbType, String metricDesc, java.util.List<MetricResult> group) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>").append(escapeHtml(dbType)).append("-").append(escapeHtml(metricDesc)).append("</title>\n<style>\n")
                .append(generateCssStyles())
                .append("</style>\n</head>\n<body>\n<div class=\"container\">\n")
                .append("<div class=\"header\"><h1>").append(escapeHtml(dbType)).append("-").append(escapeHtml(metricDesc)).append("</h1></div>\n")
                .append("<div class=\"section\"><div class=\"section-header\"><h2>多值指标</h2></div>\n")
                .append("<div class=\"section-content\">\n<table class=\"metric-table\">\n<thead>\n<tr>\n")
                .append("<th>系统名称</th><th>数据库名称</th><th>节点IP</th>");

        // 计算列
        java.util.List<String> columns = null;
        for (MetricResult r : group) {
            if (r.getColumns() != null && !r.getColumns().isEmpty()) {
                columns = r.getColumns();
                break;
            }
        }
        if (columns == null) {
            // 回退：扫描首个有数据的行
            for (MetricResult r : group) {
                java.util.List<java.util.Map<String, Object>> rows = r.getMultiValues();
                if ((rows == null || rows.isEmpty()) && r.getRows() != null) rows = r.getRows();
                if (rows != null && !rows.isEmpty()) {
                    java.util.Map<String, Object> first = rows.get(0);
                    columns = new java.util.ArrayList<>(first.keySet());
                    break;
                }
            }
            if (columns == null) columns = java.util.Arrays.asList();
        }

        for (String c : columns) {
            html.append("<th>").append(escapeHtml(c)).append("</th>");
        }
        html.append("<th>执行时间</th>\n</tr>\n</thead>\n<tbody>\n");

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (MetricResult r : group) {
            if (!r.isSuccess()) continue;
            java.util.List<java.util.Map<String, Object>> rows = r.getMultiValues();
            if ((rows == null || rows.isEmpty()) && r.getRows() != null) rows = r.getRows();
            if (rows == null || rows.isEmpty()) continue;

            String execTime = formatTime(r.getExecuteTime(), fmt);

            for (java.util.Map<String, Object> row : rows) {
                html.append("<tr>")
                        .append("<td>").append(escapeHtml(nullToEmpty(r.getSystemName()))).append("</td>")
                        .append("<td>").append(escapeHtml(nullToEmpty(r.getDatabaseName()))).append("</td>")
                        .append("<td>").append(escapeHtml(nullToEmpty(r.getNodeIp()))).append("</td>");
                for (String c : columns) {
                    Object v = row.get(c);
                    String cls = evalCellClass(dbType, r.getMetricName(), metricDesc, c, v);
                    if (cls != null && !cls.isEmpty()) {
                        html.append("<td class=\"").append(escapeHtml(cls)).append("\">")
                                .append(escapeHtml(v != null ? v.toString() : "")).append("</td>");
                    } else {
                        html.append("<td>").append(escapeHtml(v != null ? v.toString() : "")).append("</td>");
                    }
                }
                html.append("<td>").append(escapeHtml(execTime)).append("</td>")
                        .append("</tr>\n");
            }
        }

        html.append("</tbody>\n</table>\n</div></div>\n")
                .append("<div class=\"footer\"><p>报告由 dbcli 工具生成 | ")
                .append(escapeHtml(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .append("</p></div>\n</div>\n</body>\n</html>\n");

        return html.toString();
    }

    // 生成单文件标签页报告：每个“单值/多值工作表”为一个 Sheet
    private String renderTabbedReport(Map<String, List<MetricResult>> singleByType,
                                      Map<String, Map<String, List<MetricResult>>> multiGrouped) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>数据库指标报告</title>\n<style>\n")
                .append(generateCssStyles())
                .append("</style>\n</head>\n<body>\n<div class=\"container\">\n");

        // 标题
        String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        html.append("<div class=\"header\"><h1>数据库指标报告</h1>")
                .append("<div class=\"subtitle\">生成时间: ").append(escapeHtml(ts)).append("</div></div>\n");

        // 组装 Sheet 元数据：id、标题与内容
        class SheetDef {
            String id, title, content;
            SheetDef(String id, String title, String content) { this.id = id; this.title = title; this.content = content; }
        }
        java.util.List<SheetDef> sheets = new java.util.ArrayList<>();

        // 单值：每个类型一个 Sheet（标题为类型名，如 dm/oracle/mysql/postgresql）
        java.util.List<String> singleTypes = new java.util.ArrayList<>(singleByType.keySet());
        singleTypes.sort(String::compareToIgnoreCase);
        for (String type : singleTypes) {
            String id = "sheet-single-" + safeFileName(type);
            String title = type;
            String content = buildSingleTableSectionForType(type, singleByType.get(type));
            sheets.add(new SheetDef(id, title, content));
        }

        // 多值：每个“类型-指标描述”一个 Sheet（标题同 Excel 工作表名）
        java.util.List<String> types = new java.util.ArrayList<>(multiGrouped.keySet());
        types.sort(String::compareToIgnoreCase);
        for (String type : types) {
            java.util.Map<String, java.util.List<MetricResult>> m = multiGrouped.get(type);
            java.util.List<String> descs = new java.util.ArrayList<>(m.keySet());
            descs.sort(String::compareToIgnoreCase);
            for (String desc : descs) {
                String id = "sheet-multi-" + safeFileName(type + "-" + desc);
                String title = type + "-" + desc;
                String content = buildMultiMetricSection(type, desc, m.get(desc));
                sheets.add(new SheetDef(id, title, content));
            }
        }

        // 标签栏
        html.append("<div class=\"tabbar\">\n");
        for (SheetDef s : sheets) {
            html.append("<button class=\"tab-btn\" data-target=\"").append(escapeHtml(s.id)).append("\">")
                    .append(escapeHtml(s.title)).append("</button>\n");
        }
        html.append("</div>\n");

        // 内容区域
        for (SheetDef s : sheets) {
            html.append("<div id=\"").append(escapeHtml(s.id)).append("\" class=\"sheet\">\n")
                    .append(s.content)
                    .append("\n</div>\n");
        }

        // 脚本（切换标签）
        html.append("<div class=\"footer\"><p>报告由 dbcli 工具生成 | ").append(escapeHtml(ts)).append("</p></div>\n")
                .append("</div>\n")
                .append("<script>\n")
                .append("document.addEventListener('DOMContentLoaded',function(){\n")
                .append(" const tabs=Array.from(document.querySelectorAll('.tab-btn'));\n")
                .append(" const sheets=Array.from(document.querySelectorAll('.sheet'));\n")
                .append(" function act(id){tabs.forEach(t=>t.classList.toggle('active',t.dataset.target===id));\n")
                .append(" sheets.forEach(s=>s.classList.toggle('active',s.id===id));\n")
                .append(" if(id){history.replaceState(null,'','#'+id);} }\n")
                .append(" let init=location.hash?location.hash.substring(1):(sheets[0]?sheets[0].id:null);\n")
                .append(" if(init) act(init);\n")
                .append(" tabs.forEach(t=>t.addEventListener('click',()=>act(t.dataset.target)));\n")
                .append("});\n")
                .append("</script>\n")
                .append("</body>\n</html>\n");

        return html.toString();
    }

    // 构建单值工作表片段（与 Excel 单值工作表一致：系统名称、数据库名称、节点IP、按列聚合的单值指标列、执行时间）
    private String buildSingleTableSectionForType(String dbType, java.util.List<MetricResult> results) {
        String t = dbTypeKey(dbType);
        // 仅保留该类型且成功的 SINGLE 结果
        java.util.List<MetricResult> list = new java.util.ArrayList<>();
        for (MetricResult r : results) {
            String mt = (r.getMetricType() != null) ? r.getMetricType().toUpperCase(java.util.Locale.ROOT) : "";
            if (!"SINGLE".equals(mt)) continue;
            if (!r.isSuccess()) continue;
            if (!dbTypeKey(r.getDbType()).equals(t)) continue;
            list.add(r);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section\"><div class=\"section-header\"><h2>")
          .append(escapeHtml(t)).append("</h2></div>\n<div class=\"section-content\">\n")
          .append("<table class=\"metric-table\"><thead><tr>")
          .append("<th>系统名称</th><th>数据库名称</th><th>节点IP</th>");

        // 汇总所有应展示的列：
        // - 有 columns 的单值指标：按定义列名展开（如“数据库版本/数据库目录/端口号”）
        // - 无 columns 的单值指标：使用 description（缺失则 name）作为单列
        java.util.LinkedHashSet<String> allColumns = new java.util.LinkedHashSet<>();
        for (MetricResult r : list) {
            java.util.List<String> cols;
            if (r.getColumns() != null && !r.getColumns().isEmpty()) {
                cols = r.getColumns();
            } else {
                String c = (r.getMetricDescription() != null && !r.getMetricDescription().isEmpty())
                        ? r.getMetricDescription() : (r.getMetricName() != null ? r.getMetricName() : "值");
                cols = java.util.Collections.singletonList(c);
            }
            allColumns.addAll(cols);
        }
        for (String c : allColumns) {
            sb.append("<th>").append(escapeHtml(c)).append("</th>");
        }
        sb.append("<th>执行时间</th></tr></thead><tbody>");

        // 分组：系统|数据库|IP
        java.util.Map<String, java.util.List<MetricResult>> grouped = new java.util.LinkedHashMap<>();
        for (MetricResult r : list) {
            String key = (r.getSystemName() != null ? r.getSystemName() : "")
                    + "|" + (r.getDatabaseName() != null ? r.getDatabaseName() : "")
                    + "|" + (r.getNodeIp() != null ? r.getNodeIp() : "");
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
        }

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (java.util.Map.Entry<String, java.util.List<MetricResult>> e : grouped.entrySet()) {
            String key = e.getKey();
            java.util.List<MetricResult> grp = e.getValue();
            String[] parts = key.split("\\|", -1);
            String sysName = parts.length > 0 ? parts[0] : "";
            String dbName  = parts.length > 1 ? parts[1] : "";
            String nodeIp  = parts.length > 2 ? parts[2] : "";

            // col -> value, col -> owner MetricResult（用于阈值着色）
            java.util.Map<String, String> colValues = new java.util.LinkedHashMap<>();
            java.util.Map<String, MetricResult> colOwner  = new java.util.HashMap<>();

            for (MetricResult r : grp) {
                java.util.List<String> cols;
                if (r.getColumns() != null && !r.getColumns().isEmpty()) {
                    cols = r.getColumns();
                } else {
                    String c = (r.getMetricDescription() != null && !r.getMetricDescription().isEmpty())
                            ? r.getMetricDescription() : (r.getMetricName() != null ? r.getMetricName() : "值");
                    cols = java.util.Collections.singletonList(c);
                }

                java.util.List<java.util.Map<String, Object>> rows = r.getMultiValues();
                if ((rows == null || rows.isEmpty()) && r.getRows() != null) rows = r.getRows();
                if (rows != null && !rows.isEmpty()) {
                    java.util.Map<String, Object> row0 = rows.get(0);
                    for (String c : cols) {
                        if (!colValues.containsKey(c)) {
                            Object v = row0.get(c);
                            if (v != null) {
                                colValues.put(c, v.toString());
                                colOwner.put(c, r);
                            }
                        }
                    }
                } else if (r.getValue() != null && cols.size() == 1) {
                    String c = cols.get(0);
                    if (!colValues.containsKey(c)) {
                        colValues.put(c, r.getValue().toString());
                        colOwner.put(c, r);
                    }
                }
            }

            String execTime = grp.isEmpty() ? "" : formatTime(grp.get(0).getExecuteTime(), fmt);

            sb.append("<tr>")
              .append("<td>").append(escapeHtml(sysName)).append("</td>")
              .append("<td>").append(escapeHtml(dbName)).append("</td>")
              .append("<td>").append(escapeHtml(nodeIp)).append("</td>");

            for (String c : allColumns) {
                String v = colValues.get(c);
                MetricResult owner = colOwner.get(c);
                String cls = (owner != null) ? determineValueClass(t, owner, v) : null;
                if (cls != null && !cls.isEmpty()) {
                    sb.append("<td class=\"").append(escapeHtml(cls)).append("\">")
                      .append(escapeHtml(v != null ? v : "")).append("</td>");
                } else {
                    sb.append("<td>").append(escapeHtml(v != null ? v : "")).append("</td>");
                }
            }

            sb.append("<td>").append(escapeHtml(execTime)).append("</td></tr>");
        }

        sb.append("</tbody></table>\n</div></div>");
        return sb.toString();
    }

    // 构建多值工作表片段（与 Excel 多值工作表一致）
    private String buildMultiMetricSection(String dbType, String metricDesc, java.util.List<MetricResult> group) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"section\"><div class=\"section-header\"><h2>")
                .append(escapeHtml(dbType)).append("-").append(escapeHtml(metricDesc))
                .append("</h2></div>\n<div class=\"section-content\">\n")
                .append("<table class=\"metric-table\"><thead><tr>")
                .append("<th>系统名称</th><th>数据库名称</th><th>节点IP</th>");

        // 列名（优先指标定义 columns）
        java.util.List<String> columns = null;
        for (MetricResult r : group) {
            if (r.getColumns() != null && !r.getColumns().isEmpty()) {
                columns = r.getColumns();
                break;
            }
        }
        if (columns == null) {
            for (MetricResult r : group) {
                java.util.List<java.util.Map<String, Object>> rows = r.getMultiValues();
                if ((rows == null || rows.isEmpty()) && r.getRows() != null) rows = r.getRows();
                if (rows != null && !rows.isEmpty()) {
                    java.util.Map<String, Object> first = rows.get(0);
                    columns = new java.util.ArrayList<>(first.keySet());
                    break;
                }
            }
            if (columns == null) columns = java.util.Arrays.asList();
        }

        for (String c : columns) sb.append("<th>").append(escapeHtml(c)).append("</th>");
        sb.append("<th>执行时间</th></tr></thead><tbody>");

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (MetricResult r : group) {
            if (!r.isSuccess()) continue;
            java.util.List<java.util.Map<String, Object>> rows = r.getMultiValues();
            if ((rows == null || rows.isEmpty()) && r.getRows() != null) rows = r.getRows();
            if (rows == null || rows.isEmpty()) continue;

            String execTime = formatTime(r.getExecuteTime(), fmt);
            for (java.util.Map<String, Object> row : rows) {
                sb.append("<tr>")
                  .append("<td>").append(escapeHtml(nullToEmpty(r.getSystemName()))).append("</td>")
                  .append("<td>").append(escapeHtml(nullToEmpty(r.getDatabaseName()))).append("</td>")
                  .append("<td>").append(escapeHtml(nullToEmpty(r.getNodeIp()))).append("</td>");
                for (String c : columns) {
                    Object v = row.get(c);
                    String cls = evalCellClass(dbType, r.getMetricName(), metricDesc, c, v);
                    if (cls != null && !cls.isEmpty()) {
                        sb.append("<td class=\"").append(escapeHtml(cls)).append("\">")
                          .append(escapeHtml(v != null ? v.toString() : "")).append("</td>");
                    } else {
                        sb.append("<td>").append(escapeHtml(v != null ? v.toString() : "")).append("</td>");
                    }
                }
                sb.append("<td>").append(escapeHtml(execTime)).append("</td></tr>");
            }
        }

        sb.append("</tbody></table>\n</div></div>");
        return sb.toString();
    }

    private String safeFileName(String s) {
        if (s == null || s.isEmpty()) return "metric";
        String name = s.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (name.length() > 80) name = name.substring(0, 80);
        return name;
    }

    private String formatTime(java.time.LocalDateTime t, java.time.format.DateTimeFormatter fmt) {
        if (t == null) return "";
        try { return t.format(fmt); } catch (Exception e) { return ""; }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // 按数据库类型分表渲染单值指标页面：表头为“系统名称、数据库名称、节点IP、各单值指标描述、执行时间”
    // 每行代表一个“系统名称+数据库名称”分组，节点IP取该组第一条记录；各指标列填对应值；单元格按阈值高亮
    private String renderSingleMetricsPageByType(java.util.List<MetricResult> results) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>单值指标汇总</title>\n<style>\n")
                .append(generateCssStyles())
                .append("</style>\n</head>\n<body>\n<div class=\"container\">\n")
                .append("<div class=\"header\"><h1>单值指标汇总</h1></div>\n");

        // 仅取成功的 SINGLE 指标，按 dbType 分组
        java.util.Map<String, java.util.List<MetricResult>> byType = new java.util.HashMap<>();
        for (MetricResult r : results) {
            String mt = (r.getMetricType() != null) ? r.getMetricType().toUpperCase(java.util.Locale.ROOT) : "";
            if (!"SINGLE".equals(mt)) continue;
            if (!r.isSuccess()) continue;
            String t = dbTypeKey(r.getDbType());
            byType.computeIfAbsent(t, k -> new java.util.ArrayList<>()).add(r);
        }

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 按类型输出表格
        java.util.List<String> types = new java.util.ArrayList<>(byType.keySet());
        types.sort(String::compareToIgnoreCase);
        for (String t : types) {
            java.util.List<MetricResult> list = byType.get(t);
            if (list == null || list.isEmpty()) continue;

            // 计算列：唯一的指标名集合（保持出现顺序），同时映射到描述文本（优先描述，缺失回退指标名）
            java.util.LinkedHashSet<String> metricNames = new java.util.LinkedHashSet<>();
            java.util.Map<String, String> nameToDesc = new java.util.LinkedHashMap<>();
            for (MetricResult r : list) {
                String name = r.getMetricName();
                if (name == null) continue;
                if (!metricNames.contains(name)) {
                    metricNames.add(name);
                    String desc = (r.getMetricDescription() != null && !r.getMetricDescription().isEmpty())
                            ? r.getMetricDescription() : name;
                    nameToDesc.put(name, desc);
                }
            }

            html.append("<div class=\"section\"><div class=\"section-header\"><h2>")
                    .append(escapeHtml(t)).append("</h2></div>\n")
                    .append("<div class=\"section-content\">\n<table class=\"metric-table\">\n<thead>\n<tr>\n")
                    .append("<th>系统名称</th><th>数据库名称</th><th>节点IP</th>");
            for (String metricName : metricNames) {
                String desc = nameToDesc.get(metricName);
                html.append("<th>").append(escapeHtml(desc != null ? desc : metricName)).append("</th>");
            }
            html.append("<th>执行时间</th>\n</tr>\n</thead>\n<tbody>\n");

            // 行分组：与 Excel 一致，按 系统名称|数据库名称 分组（节点IP取第一条）
            java.util.Map<String, java.util.List<MetricResult>> grouped = new java.util.LinkedHashMap<>();
            for (MetricResult r : list) {
                String key = (r.getSystemName() != null ? r.getSystemName() : "")
                        + "|" + (r.getDatabaseName() != null ? r.getDatabaseName() : "");
                grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
            }

            for (java.util.Map.Entry<String, java.util.List<MetricResult>> e : grouped.entrySet()) {
                String key = e.getKey();
                java.util.List<MetricResult> grp = e.getValue();
                String[] parts = key.split("\\\\|", -1); // this split is incorrect; we need to split by literal '|' in regex. Correct is "\\|". But in Java string, need "\\\\|"? Wait: In Java string literal, to represent regex \|, you need "\\|". Here we are in code; we must produce "\\|". In the code text, we should use "\\|". Fix it.
                // Fix split: use "\\|"
            }

            // 重新遍历 grouped 输出行（修正 split）
            for (java.util.Map.Entry<String, java.util.List<MetricResult>> e2 : grouped.entrySet()) {
                String key2 = e2.getKey();
                java.util.List<MetricResult> grp2 = e2.getValue();

                String[] keys = key2.split("\\\\|", -1); // needs to be "\\|"
                // We'll build sys/db manually to avoid split pitfalls:
                String sysName;
                String dbName;
                int sep = key2.indexOf('|');
                if (sep >= 0) {
                    sysName = key2.substring(0, sep);
                    dbName = key2.substring(sep + 1);
                } else {
                    sysName = key2;
                    dbName = "";
                }

                String nodeIp = (!grp2.isEmpty() && grp2.get(0).getNodeIp() != null) ? grp2.get(0).getNodeIp() : "";

                // map: metricName -> MetricResult
                java.util.Map<String, MetricResult> rmap = new java.util.HashMap<>();
                for (MetricResult r : grp2) {
                    if (r.getMetricName() != null) rmap.put(r.getMetricName(), r);
                }

                String execTime = "";
                if (!grp2.isEmpty()) {
                    execTime = formatTime(grp2.get(0).getExecuteTime(), fmt);
                }

                html.append("<tr>")
                        .append("<td>").append(escapeHtml(sysName)).append("</td>")
                        .append("<td>").append(escapeHtml(dbName)).append("</td>")
                        .append("<td>").append(escapeHtml(nodeIp)).append("</td>");

                // 输出各指标列
                for (String metricName : metricNames) {
                    MetricResult mr = rmap.get(metricName);
                    if (mr != null) {
                        Object value = mr.getValue();
                        String cls = determineValueClass(t, mr, value);
                        String valStr = value != null ? value.toString() : "";
                        if (cls != null && !cls.isEmpty()) {
                            html.append("<td class=\"").append(escapeHtml(cls)).append("\">")
                                    .append(escapeHtml(valStr)).append("</td>");
                        } else {
                            html.append("<td>").append(escapeHtml(valStr)).append("</td>");
                        }
                    } else {
                        html.append("<td></td>");
                    }
                }

                html.append("<td>").append(escapeHtml(execTime)).append("</td>")
                        .append("</tr>\n");
            }

            html.append("</tbody>\n</table>\n</div></div>\n");
        }

        html.append("<div class=\"footer\"><p>报告由 dbcli 工具生成 | ")
                .append(escapeHtml(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .append("</p></div>\n</div>\n</body>\n</html>\n");

        return html.toString();
    }

    // 评估多值页面的某格单元风险等级，返回 risk-high / risk-medium / ""（无高亮）
    private String evalCellClass(String dbType, String metricName, String metricDesc, String column, Object value) {
        if (column == null) return "";
        ThresholdSpec spec = findThresholdSpec(dbType, metricName, metricDesc);
        if (spec == null) return "";
        String highest = null;

        // 新写法：rules 优先（多值页需严格匹配列名；与 Excel 行为一致：命中第一条规则即返回）
        if (spec.rules != null && !spec.rules.isEmpty()) {
            for (ThresholdSpec.Rule r : spec.rules) {
                String effCol = (r.column != null) ? r.column : spec.column;
                // 多值列必须指定列名才生效；未指定列名的规则在多值表格中忽略（与 Excel 一致）
                if (effCol == null) continue;
                if (!normKey(effCol).equals(normKey(column))) continue;

                Double v = parseNumber(value);
                Double th = parseNumber(r.value);
                if (v != null && th != null && compare(v, r.operator, th)) {
                    String lvl = safeLevel(r.level);
                    if ("high".equalsIgnoreCase(lvl)) return "risk-high";
                    if ("medium".equalsIgnoreCase(lvl)) return "risk-medium";
                    return "risk-high"; // 其他值默认视为 high（与 Excel 的 chooseStyleByLevel 一致）
                }
            }
            // 无任何规则命中则不高亮
            return "";
        } else {
            // 兼容旧写法：column + 单值 或按列 Map
            if (spec.column != null) {
                if (normKey(spec.column).equals(normKey(column))) {
                    Double v = parseNumber(value);
                    Double th = parseNumber(spec.value);
                    if (v != null && th != null && compare(v, spec.operator, th)) {
                        highest = maxLevel(highest, spec.level);
                    }
                }
            } else if (spec.value instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) spec.value;
                Object tv = findByKeyNormalized(map, column);
                if (tv != null) {
                    Double v = parseNumber(value);
                    Double th = parseNumber(tv);
                    if (v != null && th != null && compare(v, spec.operator, th)) {
                        highest = maxLevel(highest, spec.level);
                    }
                }
            }
        }

        if ("high".equalsIgnoreCase(highest)) return "risk-high";
        if ("medium".equalsIgnoreCase(highest)) return "risk-medium";
        return "";
    }

    // 计算单值指标单元格的高亮 CSS 类（与 Excel determineValueStyle 对齐）
    private String determineValueClass(String dbType, MetricResult result, Object value) {
        ThresholdSpec spec = findThresholdSpec(dbType, result.getMetricName(), result.getMetricDescription());
        if (spec == null) {
            return "";
        }

        // 1) rules 优先：按顺序匹配，命中即返回对应级别类
        if (spec.rules != null && !spec.rules.isEmpty()) {
            Double v = parseNumber(value);
            if (v != null) {
                for (ThresholdSpec.Rule r : spec.rules) {
                    Double th = parseNumber(r.value);
                    if (th != null && compare(v, r.operator, th)) {
                        return chooseClassByLevel(r.level);
                    }
                }
            }
            return "";
        }

        // 2) 兼容旧写法：仅标量阈值适用于单值
        if (!(spec.value instanceof java.util.Map)) {
            Double v = parseNumber(value);
            Double th = parseNumber(spec.value);
            if (v != null && th != null && compare(v, spec.operator, th)) {
                return chooseClassByLevel(spec.level);
            }
        }
        return "";
    }

    private String chooseClassByLevel(String level) {
        if (level == null) return "risk-high";
        String lv = level.toLowerCase(java.util.Locale.ROOT);
        if ("high".equals(lv)) return "risk-high";
        if ("medium".equals(lv)) return "risk-medium";
        return "risk-high";
    }

    /* ========================== 阈值加载与评估（对齐 Excel） ========================== */

    private void ensureThresholdsLoaded(String metricsDir) {
        if (thresholdsLoaded) return;
        try {
            thresholdsCache = loadThresholdsFromDir(metricsDir);
            thresholdsLoaded = true;
            int total = thresholdsCache.values().stream().mapToInt(Map::size).sum();
            logger.info("HTML 阈值映射加载完成，条目数: {}", total);
        } catch (Exception e) {
            logger.warn("加载 HTML 阈值映射失败，将回退执行层阈值: {}", e.getMessage());
            thresholdsCache = new HashMap<>();
        }
    }

    private Map<String, Map<String, ThresholdSpec>> loadThresholdsFromDir(String metricsDir) throws IOException {
        Map<String, Map<String, ThresholdSpec>> result = new HashMap<>();
        Path dir = Paths.get(metricsDir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            logger.warn("阈值加载：指标目录不存在或不是目录: {}", metricsDir);
            return result;
        }

        java.io.File[] files = dir.toFile().listFiles((d, name) -> name.endsWith("-metrics.yml") || name.endsWith("-metrics.yaml"));
        if (files == null) return result;

        for (java.io.File f : files) {
            String type = inferDbTypeFromFilename(f.getName());
            String dbKey = dbTypeKey(type);
            result.computeIfAbsent(dbKey, k -> new HashMap<>());

            try (InputStream is = Files.newInputStream(f.toPath())) {
                Yaml yaml = new Yaml();
                Object data = yaml.load(is);
                if (data == null) continue;

                List<Map<String, Object>> metricsList = new ArrayList<>();
                if (data instanceof Map) {
                    Object metricsObj = ((Map<?, ?>) data).get("metrics");
                    if (metricsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> ml = (List<Map<String, Object>>) metricsObj;
                        metricsList.addAll(ml);
                    }
                } else if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> ml = (List<Map<String, Object>>) data;
                    metricsList.addAll(ml);
                }

                for (Map<String, Object> m : metricsList) {
                    String name = asString(m.get("name"));
                    String desc = asString(m.get("description"));
                    Object thrObj = m.get("threshold");
                    if (!(thrObj instanceof Map)) continue;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> thr = (Map<String, Object>) thrObj;

                    ThresholdSpec spec = new ThresholdSpec();
                    Object rulesObj = thr.get("rules");
                    if (rulesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> rulesList = (List<Map<String, Object>>) rulesObj;
                        for (Map<String, Object> r : rulesList) {
                            ThresholdSpec.Rule rule = new ThresholdSpec.Rule();
                            rule.level = asString(r.get("level"));
                            rule.operator = asString(r.get("operator"));
                            rule.value = r.get("value");
                            String ruleColumn = asString(r.get("column"));
                            rule.column = (ruleColumn != null) ? ruleColumn : asString(thr.get("column")); // 继承父级column
                            spec.rules.add(rule);
                        }
                    } else {
                        // 兼容旧写法
                        spec.level = asString(thr.get("level"));
                        spec.operator = asString(thr.get("operator"));
                        spec.column = asString(thr.get("column"));
                        spec.value = thr.get("value");
                    }

                    if (name != null) result.get(dbKey).put(normKey(name), spec);
                    if (desc != null) result.get(dbKey).put(normKey(desc), spec);
                }
            } catch (Exception ex) {
                logger.warn("解析指标阈值文件失败：{}，原因：{}", f.getName(), ex.getMessage());
            }
        }

        return result;
    }

    private String evaluateRiskLevel(MetricResult result) {
        // 查找 spec
        ThresholdSpec spec = findThresholdSpec(result.getDbType(), result.getMetricName(), result.getMetricDescription());
        if (spec == null) {
            // 回退执行层阈值
            String tl = result.getThresholdLevel();
            return tl != null ? tl.toLowerCase(Locale.ROOT) : null;
        }

        // 统一数据为多行形式
        List<Map<String, Object>> data = normalizeData(result);
        if (data == null || data.isEmpty()) {
            return null;
        }

        // 优先按 rules（命中即返回该 rule 的 level；若多条命中，取最高等级：high > medium）
        String highest = null;

        if (spec.rules != null && !spec.rules.isEmpty()) {
            // 逐行逐列检查
            for (Map<String, Object> row : data) {
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    String col = e.getKey();
                    Object val = e.getValue();
                    for (ThresholdSpec.Rule r : spec.rules) {
                        String effCol = (r.column != null) ? r.column : spec.column;
                        // 对单值指标（无列定义）也允许 rules 不带 column
                        boolean columnMatch = (effCol == null) || normKey(effCol).equals(normKey(col));
                        if (!columnMatch) continue;

                        Double v = parseNumber(val);
                        Double th = parseNumber(r.value);
                        if (v != null && th != null && compare(v, r.operator, th)) {
                            highest = maxLevel(highest, r.level);
                            if ("high".equalsIgnoreCase(highest)) {
                                return "high";
                            }
                        }
                    }
                }
            }
            return highest;
        }

        // 兼容旧写法：value 为 Map（逐列），或 column + 单值，或单阈值（仅单值适用）
        // 逐列 Map
        if (spec.value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) spec.value;
            for (Map<String, Object> row : data) {
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    String col = e.getKey();
                    Object val = e.getValue();
                    Object tv = findByKeyNormalized(map, col);
                    if (tv == null) continue;

                    Double v = parseNumber(val);
                    Double th = parseNumber(tv);
                    if (v != null && th != null && compare(v, spec.operator, th)) {
                        highest = maxLevel(highest, spec.level);
                        if ("high".equalsIgnoreCase(highest)) return "high";
                    }
                }
            }
            return highest;
        }

        // column + 单值
        if (spec.column != null) {
            for (Map<String, Object> row : data) {
                Object val = row.get(spec.column);
                if (val == null) continue;
                Double v = parseNumber(val);
                Double th = parseNumber(spec.value);
                if (v != null && th != null && compare(v, spec.operator, th)) {
                    highest = maxLevel(highest, spec.level);
                    if ("high".equalsIgnoreCase(highest)) return "high";
                }
            }
            return highest;
        }

        // 单值：取第一行第一列（或名为“值”的列）
        Object single = tryExtractSingleValue(data);
        if (single != null) {
            Double v = parseNumber(single);
            Double th = parseNumber(spec.value);
            if (v != null && th != null && compare(v, spec.operator, th)) {
                return safeLevel(spec.level);
            }
        }
        return highest;
    }

    private List<Map<String, Object>> normalizeData(MetricResult result) {
        // 如果 MetricResult 提供了 multiValues，直接使用
        if (result.getMultiValues() != null && !result.getMultiValues().isEmpty()) {
            return result.getMultiValues();
        }
        // 否则使用 data 字段（HtmlReportGenerator 原有逻辑）
        Object dataObj = result.getData();
        List<Map<String, Object>> data = null;
        if (dataObj instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
                data = dataList;
            } catch (ClassCastException e) {
                data = new ArrayList<>();
                Map<String, Object> singleRow = new HashMap<>();
                singleRow.put("值", String.valueOf(dataObj));
                data.add(singleRow);
            }
        } else if (dataObj != null) {
            data = new ArrayList<>();
            Map<String, Object> singleRow = new HashMap<>();
            singleRow.put("值", String.valueOf(dataObj));
            data.add(singleRow);
        } else if (result.getValue() != null) {
            data = new ArrayList<>();
            Map<String, Object> singleRow = new HashMap<>();
            singleRow.put("值", String.valueOf(result.getValue()));
            data.add(singleRow);
        }
        return data;
    }

    private Object tryExtractSingleValue(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) return null;
        Map<String, Object> row = data.get(0);
        if (row.containsKey("值")) {
            return row.get("值");
        }
        if (!row.isEmpty()) {
            return row.values().iterator().next();
        }
        return null;
    }

    private ThresholdSpec findThresholdSpec(String dbType, String metricName, String metricDescription) {
        if (dbType == null) return null;
        String key = dbTypeKey(dbType);
        Map<String, ThresholdSpec> byMetric = thresholdsCache.get(key);
        if (byMetric == null) return null;
        if (metricName != null) {
            ThresholdSpec s = byMetric.get(normKey(metricName));
            if (s != null) return s;
        }
        if (metricDescription != null) {
            return byMetric.get(normKey(metricDescription));
        }
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String dbTypeKey(String type) {
        if (type == null) return "unknown";
        String t = type.toLowerCase(Locale.ROOT).trim();
        switch (t) {
            case "pg":
            case "postgres":
            case "postgresql":
                return "postgresql";
            case "dameng":
            case "dm":
                return "dm";
            case "oracle":
            case "mysql":
                return t;
            default:
                return t;
        }
    }

    private String inferDbTypeFromFilename(String filename) {
        String low = filename.toLowerCase(Locale.ROOT);
        if (low.startsWith("oracle-")) return "oracle";
        if (low.startsWith("mysql-")) return "mysql";
        if (low.startsWith("pg-") || low.startsWith("postgresql-")) return "postgresql";
        if (low.startsWith("dm-") || low.startsWith("dameng-")) return "dm";
        return "unknown";
    }

    private String normKey(String s) {
        if (s == null) return "";
        String normalized = s.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}]", "");
        return normalized;
    }

    private Double parseNumber(Object v) {
        if (v == null) return null;
        try {
            String s = v.toString().trim();
            s = s.replace("%", "").replace(",", "");
            return Double.parseDouble(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean compare(double v, String op, double th) {
        String o = (op == null) ? ">" : op.trim();
        switch (o) {
            case ">":
                return v > th;
            case ">=":
                return v >= th;
            case "<":
                return v < th;
            case "<=":
                return v <= th;
            case "==":
            case "=":
                return v == th;
            case "!=":
                return v != th;
            default:
                return v > th;
        }
    }

    private String maxLevel(String a, String b) {
        String la = safeLevel(a);
        String lb = safeLevel(b);
        if ("high".equals(la) || "high".equals(lb)) return "high";
        if ("medium".equals(la) || "medium".equals(lb)) return "medium";
        if (la != null) return la;
        return lb;
    }

    private String safeLevel(String level) {
        if (level == null) return "high";
        String lv = level.toLowerCase(Locale.ROOT);
        if ("high".equals(lv) || "medium".equals(lv)) return lv;
        return "high";
    }

    /* ========================== 内部阈值结构 ========================== */
    private static class ThresholdSpec {
        String level;      // 兼容旧写法：单阈值的级别
        String operator;   // 兼容旧写法：单阈值操作符
        Object value;      // 兼容旧写法：数字或 Map[column->数字]
        String column;     // 兼容旧写法：仅对某列生效
        List<Rule> rules = new ArrayList<>(); // 新写法：多级阈值规则列表

        static class Rule {
            String level;    // high/medium/low
            String operator; // >, >=, <, <=, ==, !=
            Object value;    // 数值或可解析为数值的字符串（支持百分号）
            String column;   // 可选；为空则依赖父级 column 或仅用于单值
        }
    }

    private Object findByKeyNormalized(Map<Object, Object> map, String key) {
        String k = normKey(key);
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            if (e.getKey() == null) continue;
            if (normKey(e.getKey().toString()).equals(k)) {
                return e.getValue();
            }
        }
        return null;
    }

    @Override
    public void generateReport(List<MetricResult> results, String outputPath) throws Exception {
        generateReport(results, null, outputPath);
    }
    
    @Override
    public void generateReport(List<MetricResult> results, String metricsPath, String outputPath) throws Exception {
        generate(results, outputPath, metricsPath);
    }
    
    @Override
    public String getReportFormat() {
        return "html";
    }
    
    @Override
    public String getFileExtension() {
        return ".html";
    }
    
    @Override
    public boolean supportsResultCount(int resultCount) {
        return resultCount <= 50000; // HTML性能考虑
    }
    
    @Override
    public int getMaxSupportedResults() {
        return 50000;
    }
    
    @Override
    public String getDescription() {
        return "HTML报告生成器，支持阈值高亮和响应式设计";
    }

    // 移除了getFormat方法，因为ReportGeneratorInterface不包含此方法
}
