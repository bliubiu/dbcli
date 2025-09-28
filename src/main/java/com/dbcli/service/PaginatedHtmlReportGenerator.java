package com.dbcli.service;

import com.dbcli.model.MetricResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分页HTML报告生成器
 * 支持大数据量的分页显示和交互式功能
 */
public class PaginatedHtmlReportGenerator implements ReportGeneratorInterface {
    private static final Logger logger = LoggerFactory.getLogger(PaginatedHtmlReportGenerator.class);
    
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final int pageSize;
    
    public PaginatedHtmlReportGenerator() {
        this.pageSize = DEFAULT_PAGE_SIZE;
    }
    
    public PaginatedHtmlReportGenerator(int pageSize) {
        this.pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
    }
    
    @Override
    public void generateReport(List<MetricResult> results, String outputPath) throws Exception {
        generateReport(results, null, outputPath);
    }
    
    @Override
    public void generateReport(List<MetricResult> results, String metricsPath, String outputPath) throws Exception {
        if (results == null || results.isEmpty()) {
            logger.warn("没有数据可生成报告");
            return;
        }
        
        logger.info("开始生成分页HTML报告，数据量: {}, 页面大小: {}", results.size(), pageSize);
        long startTime = System.currentTimeMillis();
        
        try {
            Path outputDir = createOutputDirectory(outputPath);
            
            List<MetricResult> singleValueResults = results.stream()
                .filter(r -> "SINGLE".equals(r.getType()))
                .collect(Collectors.toList());
                
            List<MetricResult> multiValueResults = results.stream()
                .filter(r -> "MULTI".equals(r.getType()) && r.getMultiValues() != null && !r.getMultiValues().isEmpty())
                .collect(Collectors.toList());
            
            generateIndexPage(outputDir, results, singleValueResults, multiValueResults);
            
            if (!singleValueResults.isEmpty()) {
                generateSingleValuePages(outputDir, singleValueResults);
            }
            
            if (!multiValueResults.isEmpty()) {
                generateMultiValuePages(outputDir, multiValueResults);
            }
            
            generateSummaryPage(outputDir, results);
            copyStaticResources(outputDir);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("分页HTML报告生成完成，耗时: {}ms", duration);
            
        } catch (Exception e) {
            logger.error("生成分页HTML报告失败", e);
            throw e;
        }
    }
    
    private Path createOutputDirectory(String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        if (path.toString().endsWith(".html")) {
            String dirName = path.getFileName().toString().replaceAll("\\.html$", "");
            path = path.getParent().resolve(dirName);
        }
        Files.createDirectories(path);
        return path;
    }
    
    private void generateIndexPage(Path outputDir, List<MetricResult> allResults,
                                 List<MetricResult> singleValueResults, 
                                 List<MetricResult> multiValueResults) throws IOException {
        
        Path indexPath = outputDir.resolve("index.html");
        
        try (FileWriter writer = new FileWriter(indexPath.toFile())) {
            writer.write(generateHtmlHeader("数据库指标收集报告"));
            writer.write(generateNavigation());
            
            writer.write("<div class='container'>\n");
            writer.write("<h1>数据库指标收集报告</h1>\n");
            writer.write(generateOverviewStats(allResults));
            
            writer.write("<div class='quick-nav'>\n");
            writer.write("<h2>快速导航</h2>\n");
            
            if (!singleValueResults.isEmpty()) {
                java.util.Set<String> groupKeys = new java.util.LinkedHashSet<>();
                for (MetricResult r : singleValueResults) {
                    String key = (r.getSystemName()==null?"":r.getSystemName()) + "|" +
                            (r.getDatabaseType()==null?"":r.getDatabaseType()) + "|" +
                            (r.getNodeIp()==null?"":r.getNodeIp());
                    groupKeys.add(key);
                }
                int singleValuePages = (int) Math.ceil((double) groupKeys.size() / pageSize);
                writer.write(String.format(
                    "<div class='nav-item'><a href='single-value-1.html'>单值指标 (%d 组，%d 页)</a></div>\n",
                    groupKeys.size(), singleValuePages));
            }
            
            if (!multiValueResults.isEmpty()) {
                writer.write(String.format(
                    "<div class='nav-item'><a href='multi-value.html'>多值指标 (%d 个指标)</a></div>\n",
                    multiValueResults.size()));
            }
            
            writer.write("<div class='nav-item'><a href='summary.html'>执行汇总</a></div>\n");
            writer.write("</div>\n");
            
            writer.write(generateRecentMetrics(allResults));
            writer.write("</div>\n");
            writer.write(generateHtmlFooter());
        }
        
        logger.info("主页面生成完成: {}", indexPath);
    }
    
    private String generateOverviewStats(List<MetricResult> results) {
        long total = results.size();
        long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        long failed = total - successful;
        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        
        return String.format(
            "<div class=\"stats-overview\">\n" +
            "    <div class=\"stat-card\">\n" +
            "        <div class=\"stat-number\">%d</div>\n" +
            "        <div class=\"stat-label\">总指标数</div>\n" +
            "    </div>\n" +
            "    <div class=\"stat-card success\">\n" +
            "        <div class=\"stat-number\">%d</div>\n" +
            "        <div class=\"stat-label\">成功</div>\n" +
            "    </div>\n" +
            "    <div class=\"stat-card error\">\n" +
            "        <div class=\"stat-number\">%d</div>\n" +
            "        <div class=\"stat-label\">失败</div>\n" +
            "    </div>\n" +
            "    <div class=\"stat-card\">\n" +
            "        <div class=\"stat-number\">%.1f%%</div>\n" +
            "        <div class=\"stat-label\">成功率</div>\n" +
            "    </div>\n" +
            "</div>\n", total, successful, failed, successRate);
    }
    
    private String generateRecentMetrics(List<MetricResult> results) {
        List<MetricResult> recentResults = results.stream()
            .sorted((a, b) -> {
                if (a.getCollectTime() == null && b.getCollectTime() == null) return 0;
                if (a.getCollectTime() == null) return 1;
                if (b.getCollectTime() == null) return -1;
                return b.getCollectTime().compareTo(a.getCollectTime());
            })
            .limit(10)
            .collect(Collectors.toList());
        
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='recent-metrics'>\n");
        sb.append("<h2>最近执行的指标</h2>\n");
        sb.append("<div class='metrics-list'>\n");
        
        for (MetricResult result : recentResults) {
            String statusClass = result.isSuccess() ? "success" : "error";
            String collectTime = result.getCollectTime() != null ? 
                result.getCollectTime().format(DATE_FORMATTER) : "未知";
            
            sb.append(String.format(
                "<div class='metric-item %s'>" +
                "<div class='metric-name'>%s</div>" +
                "<div class='metric-system'>%s</div>" +
                "<div class='metric-time'>%s</div>" +
                "<div class='metric-status'>%s</div>" +
                "</div>\n",
                statusClass, result.getMetricName(), result.getSystemName(), 
                collectTime, result.isSuccess() ? "成功" : "失败"));
        }
        
        sb.append("</div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }
    
    private void generateSingleValuePages(Path outputDir, List<MetricResult> results) throws IOException {
        // 1) 汇总所有列（有 columns 的多列 + 无 columns 的单列=description/name）
        java.util.LinkedHashSet<String> allColumns = new java.util.LinkedHashSet<>();
        // 分组：system|dbType|nodeIp -> 列值映射
        java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, String>> rowsMap = new java.util.LinkedHashMap<>();
        // 元信息：system, dbType, nodeIp, 收集时间(最大)
        java.util.LinkedHashMap<String, String[]> metaMap = new java.util.LinkedHashMap<>();

        for (MetricResult r : results) {
            java.util.List<String> cols;
            if (r.getColumns() != null && !r.getColumns().isEmpty()) {
                cols = r.getColumns();
            } else {
                String singleCol = (r.getDescription() != null && !r.getDescription().isEmpty())
                        ? r.getDescription() : r.getMetricName();
                cols = java.util.Collections.singletonList(singleCol);
            }
            allColumns.addAll(cols);

            String key = (r.getSystemName()==null?"":r.getSystemName()) + "|" +
                    (r.getDatabaseType()==null?"":r.getDatabaseType()) + "|" +
                    (r.getNodeIp()==null?"":r.getNodeIp());

            rowsMap.computeIfAbsent(key, k -> new java.util.LinkedHashMap<>());
            metaMap.putIfAbsent(key, new String[]{
                    r.getSystemName(),
                    r.getDatabaseType(),
                    r.getNodeIp(),
                    r.getCollectTime()!=null ? r.getCollectTime().format(DATE_FORMATTER) : ""
            });

            // 填充值：优先 multiValues 的第一行严格按列名取值；否则单列取 value
            if (r.getMultiValues()!=null && !r.getMultiValues().isEmpty()) {
                java.util.Map<String,Object> row0 = r.getMultiValues().get(0);
                for (String c : cols) {
                    Object v = row0.get(c);
                    if (v != null) {
                        rowsMap.get(key).put(c, v.toString());
                    }
                }
            } else if (r.getValue()!=null && cols.size()==1) {
                rowsMap.get(key).put(cols.get(0), r.getValue().toString());
            }

            // 更新时间为最大收集时间
            if (r.getCollectTime()!=null) {
                String newTime = r.getCollectTime().format(DATE_FORMATTER);
                String oldTime = metaMap.get(key)[3];
                if (oldTime==null || oldTime.isEmpty() || newTime.compareTo(oldTime) > 0) {
                    metaMap.get(key)[3] = newTime;
                }
            }
        }

        java.util.List<String> groupKeys = new java.util.ArrayList<>(rowsMap.keySet());
        int totalPages = (int) Math.ceil((double) groupKeys.size() / pageSize);

        for (int page = 1; page <= totalPages; page++) {
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, groupKeys.size());
            java.util.List<String> pageKeys = groupKeys.subList(startIndex, endIndex);

            Path pagePath = outputDir.resolve(String.format("single-value-%d.html", page));

            try (FileWriter writer = new FileWriter(pagePath.toFile())) {
                writer.write(generateHtmlHeader(String.format("单值指标 - 第%d页", page)));
                writer.write(generateNavigation());

                writer.write("<div class='container'>\n");
                writer.write(String.format("<h1>单值指标 - 第%d页 (共%d页)</h1>\n", page, totalPages));

                writer.write(generatePagination(page, totalPages, "single-value"));

                // 表格：系统名称 | 数据库名称 | 节点IP | allColumns... | 收集时间
                writer.write("<div class='table-container'>\n");
                writer.write("<table class='metrics-table'>\n");
                writer.write("<thead>\n<tr>");
                writer.write("<th>系统名称</th><th>数据库名称</th><th>节点IP</th>");
                for (String c : allColumns) {
                    writer.write("<th>" + escapeHtml(c) + "</th>");
                }
                writer.write("<th>收集时间</th></tr>\n</thead>\n<tbody>\n");

                for (String key : pageKeys) {
                    String[] meta = metaMap.get(key);
                    java.util.Map<String,String> values = rowsMap.get(key);

                    writer.write("<tr>");
                    writer.write("<td>" + escapeHtml(meta[0]) + "</td>");
                    writer.write("<td>" + escapeHtml(meta[1]) + "</td>");
                    writer.write("<td>" + escapeHtml(meta[2]) + "</td>");

                    for (String c : allColumns) {
                        String v = values.get(c);
                        writer.write("<td>" + (v!=null ? escapeHtml(v) : "") + "</td>");
                    }

                    writer.write("<td>" + escapeHtml(meta[3]) + "</td>");
                    writer.write("</tr>\n");
                }

                writer.write("</tbody>\n</table>\n</div>\n");

                writer.write(generatePagination(page, totalPages, "single-value"));

                writer.write("</div>\n");
                writer.write(generateHtmlFooter());
            }
        }

        logger.info("单值指标分页生成完成，共 {} 页", totalPages);
    }
    
    private String generatePagination(int currentPage, int totalPages, String pagePrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='pagination'>\n");
        
        if (currentPage > 1) {
            sb.append(String.format("<a href='%s-%d.html' class='page-link'>上一页</a>\n", 
                pagePrefix, currentPage - 1));
        }
        
        int startPage = Math.max(1, currentPage - 5);
        int endPage = Math.min(totalPages, currentPage + 5);
        
        if (startPage > 1) {
            sb.append(String.format("<a href='%s-1.html' class='page-link'>1</a>\n", pagePrefix));
            if (startPage > 2) {
                sb.append("<span class='page-ellipsis'>...</span>\n");
            }
        }
        
        for (int i = startPage; i <= endPage; i++) {
            if (i == currentPage) {
                sb.append(String.format("<span class='page-current'>%d</span>\n", i));
            } else {
                sb.append(String.format("<a href='%s-%d.html' class='page-link'>%d</a>\n", 
                    pagePrefix, i, i));
            }
        }
        
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                sb.append("<span class='page-ellipsis'>...</span>\n");
            }
            sb.append(String.format("<a href='%s-%d.html' class='page-link'>%d</a>\n", 
                pagePrefix, totalPages, totalPages));
        }
        
        if (currentPage < totalPages) {
            sb.append(String.format("<a href='%s-%d.html' class='page-link'>下一页</a>\n", 
                pagePrefix, currentPage + 1));
        }
        
        sb.append("</div>\n");
        return sb.toString();
    }
    
    private String generateSingleValueTable(List<MetricResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='table-container'>\n");
        sb.append("<table class='metrics-table'>\n");
        sb.append("<thead>\n");
        sb.append("<tr><th>系统名称</th><th>数据库类型</th><th>节点IP</th><th>指标名称</th>");
        sb.append("<th>指标描述</th><th>指标值</th><th>阈值状态</th><th>收集时间</th><th>状态</th></tr>\n");
        sb.append("</thead>\n");
        sb.append("<tbody>\n");
        
        for (MetricResult result : results) {
            String statusClass = result.isSuccess() ? "success" : "error";
            String collectTime = result.getCollectTime() != null ? 
                result.getCollectTime().format(DATE_FORMATTER) : "";
            String value;
            if (result.getColumns() != null && !result.getColumns().isEmpty()
                    && result.getMultiValues() != null && !result.getMultiValues().isEmpty()) {
                Map<String, Object> row = result.getMultiValues().get(0);
                StringBuilder sbVals = new StringBuilder();
                for (String col : result.getColumns()) {
                    if (sbVals.length() > 0) sbVals.append(" | ");
                    Object v = row.get(col);
                    sbVals.append(col).append(": ").append(v != null ? v.toString() : "");
                }
                value = sbVals.toString();
            } else {
                value = result.getValue() != null ? result.getValue().toString() : "";
            }
            
            sb.append(String.format(
                "<tr class='%s'>\n" +
                "<td>%s</td><td>%s</td><td>%s</td><td>%s</td>" +
                "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>\n" +
                "</tr>\n",
                statusClass,
                escapeHtml(result.getSystemName()),
                escapeHtml(result.getDatabaseType()),
                escapeHtml(result.getNodeIp()),
                escapeHtml(result.getMetricName()),
                escapeHtml(result.getDescription()),
                escapeHtml(value),
                escapeHtml(result.getThresholdLevel()),
                escapeHtml(collectTime),
                result.isSuccess() ? "成功" : "失败"));
        }
        
        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</div>\n");
        return sb.toString();
    }
    
    private void generateMultiValuePages(Path outputDir, List<MetricResult> results) throws IOException {
        Path multiValuePath = outputDir.resolve("multi-value.html");
        
        try (FileWriter writer = new FileWriter(multiValuePath.toFile())) {
            writer.write(generateHtmlHeader("多值指标"));
            writer.write(generateNavigation());
            
            writer.write("<div class='container'>\n");
            writer.write("<h1>多值指标</h1>\n");
            
            writer.write("<div class='metric-index'>\n");
            writer.write("<h2>指标索引</h2>\n");
            for (int i = 0; i < results.size(); i++) {
                MetricResult result = results.get(i);
                writer.write(String.format(
                    "<div class='index-item'><a href='#metric-%d'>%s (%s)</a></div>\n",
                    i, result.getMetricName(), result.getSystemName()));
            }
            writer.write("</div>\n");
            
            for (int i = 0; i < results.size(); i++) {
                MetricResult result = results.get(i);
                writer.write(generateMultiValueSection(result, i));
            }
            
            writer.write("</div>\n");
            writer.write(generateHtmlFooter());
        }
        
        logger.info("多值指标页面生成完成");
    }
    
    private String generateMultiValueSection(MetricResult result, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<div class='multi-value-section' id='metric-%d'>\n", index));
        sb.append(String.format("<h3>%s</h3>\n", escapeHtml(result.getMetricName())));
        
        sb.append("<div class='metric-info'>\n");
        sb.append(String.format("<div class='info-item'><strong>系统:</strong> %s</div>\n", 
            escapeHtml(result.getSystemName())));
        sb.append(String.format("<div class='info-item'><strong>数据库:</strong> %s</div>\n", 
            escapeHtml(result.getDatabaseType())));
        sb.append(String.format("<div class='info-item'><strong>节点:</strong> %s</div>\n", 
            escapeHtml(result.getNodeIp())));
        sb.append(String.format("<div class='info-item'><strong>描述:</strong> %s</div>\n", 
            escapeHtml(result.getDescription())));
        
        if (result.getCollectTime() != null) {
            sb.append(String.format("<div class='info-item'><strong>收集时间:</strong> %s</div>\n", 
                result.getCollectTime().format(DATE_FORMATTER)));
        }
        sb.append("</div>\n");
        
        List<Map<String, Object>> multiValues = result.getMultiValues();
        if (multiValues != null && !multiValues.isEmpty()) {
            sb.append("<div class='multi-value-table-container'>\n");
            sb.append("<table class='multi-value-table'>\n");
            
            Map<String, Object> firstRow = multiValues.get(0);
            sb.append("<thead><tr>\n");
            for (String column : firstRow.keySet()) {
                sb.append(String.format("<th>%s</th>\n", escapeHtml(column)));
            }
            sb.append("</tr></thead>\n");
            
            sb.append("<tbody>\n");
            for (Map<String, Object> row : multiValues) {
                sb.append("<tr>\n");
                for (String column : firstRow.keySet()) {
                    Object value = row.get(column);
                    String valueStr = value != null ? value.toString() : "";
                    sb.append(String.format("<td>%s</td>\n", escapeHtml(valueStr)));
                }
                sb.append("</tr>\n");
            }
            sb.append("</tbody>\n");
            sb.append("</table>\n");
            sb.append("</div>\n");
        }
        
        sb.append("</div>\n");
        return sb.toString();
    }
    
    private void generateSummaryPage(Path outputDir, List<MetricResult> results) throws IOException {
        Path summaryPath = outputDir.resolve("summary.html");
        
        try (FileWriter writer = new FileWriter(summaryPath.toFile())) {
            writer.write(generateHtmlHeader("执行汇总"));
            writer.write(generateNavigation());
            
            writer.write("<div class='container'>\n");
            writer.write("<h1>执行汇总</h1>\n");
            
            writer.write("<div class='charts-container'>\n");
            writer.write(generateChartsSection(results));
            writer.write("</div>\n");
            
            writer.write(generateDetailedStats(results));
            
            writer.write("</div>\n");
            writer.write(generateHtmlFooter());
        }
        
        logger.info("汇总页面生成完成");
    }
    
    private String generateChartsSection(List<MetricResult> results) {
        long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        long failed = results.size() - successful;
        
        return String.format(
            "<div class=\"chart-grid\">\n" +
            "    <div class=\"chart-item\">\n" +
            "        <canvas id=\"successChart\"></canvas>\n" +
            "    </div>\n" +
            "</div>\n" +
            "<script>\n" +
            "    const successCtx = document.getElementById('successChart').getContext('2d');\n" +
            "    new Chart(successCtx, {\n" +
            "        type: 'pie',\n" +
            "        data: {\n" +
            "            labels: ['成功', '失败'],\n" +
            "            datasets: [{\n" +
            "                data: [%d, %d],\n" +
            "                backgroundColor: ['#28a745', '#dc3545']\n" +
            "            }]\n" +
            "        },\n" +
            "        options: {\n" +
            "            responsive: true,\n" +
            "            plugins: {\n" +
            "                title: {\n" +
            "                    display: true,\n" +
            "                    text: '执行成功率'\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    });\n" +
            "</script>\n", successful, failed);
    }
    
    private String generateDetailedStats(List<MetricResult> results) {
        Map<String, Long> dbTypeStats = results.stream()
            .collect(Collectors.groupingBy(
                MetricResult::getDatabaseType,
                Collectors.counting()
            ));
        
        Map<String, Long> systemStats = results.stream()
            .collect(Collectors.groupingBy(
                MetricResult::getSystemName,
                Collectors.counting()
            ));
        
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='detailed-stats'>\n");
        
        sb.append("<div class='stats-section'>\n");
        sb.append("<h3>按数据库类型统计</h3>\n");
        sb.append("<table class='stats-table'>\n");
        sb.append("<thead><tr><th>数据库类型</th><th>指标数量</th></tr></thead>\n");
        sb.append("<tbody>\n");
        for (Map.Entry<String, Long> entry : dbTypeStats.entrySet()) {
            sb.append(String.format("<tr><td>%s</td><td>%d</td></tr>\n", 
                escapeHtml(entry.getKey()), entry.getValue()));
        }
        sb.append("</tbody></table>\n");
        sb.append("</div>\n");
        
        sb.append("<div class='stats-section'>\n");
        sb.append("<h3>按系统统计</h3>\n");
        sb.append("<table class='stats-table'>\n");
        sb.append("<thead><tr><th>系统名称</th><th>指标数量</th></tr></thead>\n");
        sb.append("<tbody>\n");
        for (Map.Entry<String, Long> entry : systemStats.entrySet()) {
            sb.append(String.format("<tr><td>%s</td><td>%d</td></tr>\n", 
                escapeHtml(entry.getKey()), entry.getValue()));
        }
        sb.append("</tbody></table>\n");
        sb.append("</div>\n");
        
        sb.append("</div>\n");
        return sb.toString();
    }
    
    private void copyStaticResources(Path outputDir) throws IOException {
        Path cssPath = outputDir.resolve("styles.css");
        try (FileWriter writer = new FileWriter(cssPath.toFile())) {
            writer.write(generateCSS());
        }
        logger.info("静态资源复制完成");
    }
    
    private String generateCSS() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; background-color: #f5f5f5; }\n" +
            ".navbar { background-color: #2c3e50; color: white; padding: 1rem 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n" +
            ".nav-container { max-width: 1200px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center; padding: 0 2rem; }\n" +
            ".nav-brand { font-size: 1.5rem; font-weight: bold; }\n" +
            ".nav-menu { display: flex; gap: 2rem; }\n" +
            ".nav-link { color: white; text-decoration: none; padding: 0.5rem 1rem; border-radius: 4px; transition: background-color 0.3s; }\n" +
            ".nav-link:hover { background-color: rgba(255,255,255,0.1); }\n" +
            ".container { max-width: 1200px; margin: 2rem auto; padding: 0 2rem; }\n" +
            "h1, h2, h3 { margin-bottom: 1rem; color: #2c3e50; }\n" +
            ".stats-overview { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 2rem; }\n" +
            ".stat-card { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); text-align: center; }\n" +
            ".stat-card.success { border-left: 4px solid #28a745; }\n" +
            ".stat-card.error { border-left: 4px solid #dc3545; }\n" +
            ".stat-number { font-size: 2rem; font-weight: bold; margin-bottom: 0.5rem; }\n" +
            ".stat-label { color: #666; font-size: 0.9rem; }\n" +
            ".quick-nav, .recent-metrics { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 2rem; }\n" +
            ".nav-item, .index-item { padding: 0.5rem 0; border-bottom: 1px solid #eee; }\n" +
            ".nav-item:last-child, .index-item:last-child { border-bottom: none; }\n" +
            ".nav-item a, .index-item a { color: #007bff; text-decoration: none; font-weight: 500; }\n" +
            ".nav-item a:hover, .index-item a:hover { text-decoration: underline; }\n" +
            ".metrics-list { display: flex; flex-direction: column; gap: 0.5rem; }\n" +
            ".metric-item { display: grid; grid-template-columns: 2fr 1fr 1fr 80px; gap: 1rem; padding: 0.75rem; border-radius: 4px; border-left: 4px solid #ddd; }\n" +
            ".metric-item.success { border-left-color: #28a745; background-color: #f8fff9; }\n" +
            ".metric-item.error { border-left-color: #dc3545; background-color: #fff8f8; }\n" +
            ".pagination { display: flex; justify-content: center; align-items: center; gap: 0.5rem; margin: 2rem 0; }\n" +
            ".page-link, .page-current { padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; text-decoration: none; color: #007bff; }\n" +
            ".page-current { background-color: #007bff; color: white; border-color: #007bff; }\n" +
            ".page-link:hover { background-color: #f8f9fa; }\n" +
            ".page-ellipsis { padding: 0.5rem; color: #666; }\n" +
            ".table-container { background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); overflow-x: auto; }\n" +
            ".metrics-table, .multi-value-table, .stats-table { width: 100%; border-collapse: collapse; }\n" +
            ".metrics-table th, .multi-value-table th, .stats-table th { background-color: #f8f9fa; padding: 1rem; text-align: left; border-bottom: 2px solid #dee2e6; font-weight: 600; }\n" +
            ".metrics-table td, .multi-value-table td, .stats-table td { padding: 0.75rem 1rem; border-bottom: 1px solid #dee2e6; }\n" +
            ".metrics-table tr.success { background-color: #f8fff9; }\n" +
            ".metrics-table tr.error { background-color: #fff8f8; }\n" +
            ".multi-value-section { background: white; margin-bottom: 2rem; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n" +
            ".metric-info { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1rem; padding: 1rem; background-color: #f8f9fa; border-radius: 4px; }\n" +
            ".metric-index { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 2rem; }\n" +
            ".charts-container { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 2rem; }\n" +
            ".chart-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; }\n" +
            ".chart-item { height: 300px; }\n" +
            ".detailed-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 2rem; }\n" +
            ".stats-section { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n" +
            "@media (max-width: 768px) {\n" +
            "    .nav-container { flex-direction: column; gap: 1rem; }\n" +
            "    .nav-menu { flex-wrap: wrap; justify-content: center; }\n" +
            "    .container { padding: 0 1rem; }\n" +
            "    .stats-overview { grid-template-columns: 1fr; }\n" +
            "    .metric-item { grid-template-columns: 1fr; gap: 0.5rem; }\n" +
            "    .metric-info { grid-template-columns: 1fr; }\n" +
            "    .detailed-stats { grid-template-columns: 1fr; }\n" +
            "}";
    }
    
    private String generateHtmlHeader(String title) {
        return String.format(
            "<!DOCTYPE html>\n" +
            "<html lang=\"zh-CN\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>%s</title>\n" +
            "    <link rel=\"stylesheet\" href=\"styles.css\">\n" +
            "    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n" +
            "</head>\n" +
            "<body>\n", title);
    }
    
    private String generateNavigation() {
        return "<nav class=\"navbar\">\n" +
            "    <div class=\"nav-container\">\n" +
            "        <div class=\"nav-brand\">数据库指标报告</div>\n" +
            "        <div class=\"nav-menu\">\n" +
            "            <a href=\"index.html\" class=\"nav-link\">首页</a>\n" +
            "            <a href=\"single-value-1.html\" class=\"nav-link\">单值指标</a>\n" +
            "            <a href=\"multi-value.html\" class=\"nav-link\">多值指标</a>\n" +
            "            <a href=\"summary.html\" class=\"nav-link\">执行汇总</a>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</nav>\n";
    }
    
    private String generateHtmlFooter() {
        return String.format(
            "<footer style=\"text-align: center; padding: 2rem; color: #666; border-top: 1px solid #eee; margin-top: 2rem;\">\n" +
            "    <p>数据库指标收集报告 - 生成时间: %s</p>\n" +
            "</footer>\n" +
            "</body>\n" +
            "</html>\n", LocalDateTime.now().format(DATE_FORMATTER));
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    @Override
    public String getReportFormat() {
        return "paginated_html";
    }
    
    @Override
    public String getFileExtension() {
        return ".html";
    }
    
    @Override
    public boolean supportsResultCount(int resultCount) {
        return true;
    }
    
    @Override
    public int getMaxSupportedResults() {
        return Integer.MAX_VALUE;
    }
    
    @Override
    public String getDescription() {
        return String.format("分页HTML报告生成器，每页显示%d条记录，支持交互式浏览", pageSize);
    }
}
