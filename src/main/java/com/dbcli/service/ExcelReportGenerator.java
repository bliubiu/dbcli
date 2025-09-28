package com.dbcli.service;

import com.dbcli.model.MetricResult;
import com.dbcli.util.FileUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel报告生成器
 * - 优先按 YAML 阈值配置逐单元格高亮（支持 value: map 与 column + value 两种写法）
 * - 无配置时回退启发式规则
 * - 启用单元格自动换行与列宽自适应边界
 */
public class ExcelReportGenerator implements ReportGeneratorInterface {
    private static final Logger logger = LoggerFactory.getLogger(ExcelReportGenerator.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 列宽边界（Excel宽度单位为1/256字符）
    private static final int MAX_COL_WIDTH = 12000; // ≈ 47 字符
    private static final int MIN_COL_WIDTH = 3000;  // ≈ 12 字符

    // 阈值缓存：dbType -> (metricName/description -> 阈值定义)
    private Map<String, Map<String, ThresholdSpec>> thresholdsCache = new HashMap<>();
    private boolean thresholdsLoaded = false;

    public void generate(List<MetricResult> results, String outputPath, String metricsPath) throws IOException {
        java.nio.file.Path outPath = java.nio.file.Paths.get(outputPath);
        FileUtil.createDirectoryIfNotExists(outPath.toString());

        // 懒加载指标阈值（优先使用传入的指标目录，否则回退到默认 metrics）
        ensureThresholdsLoaded(metricsPath != null && !metricsPath.trim().isEmpty() ? metricsPath.trim() : "metrics");

        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        java.nio.file.Path filePath = outPath.resolve("db_metrics_report_" + date + ".xlsx").normalize();

        try (Workbook workbook = new XSSFWorkbook()) {
            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);
            CellStyle highRiskStyle = createHighRiskStyle(workbook);
            CellStyle mediumRiskStyle = createMediumRiskStyle(workbook);
            CellStyle summaryHeaderStyle = createSummaryHeaderStyle(workbook);
            CellStyle summaryStyle = createSummaryStyle(workbook);

            // 创建摘要工作表
            createSummarySheet(workbook, results, summaryHeaderStyle, summaryStyle, highRiskStyle, mediumRiskStyle);

            // 按数据库类型分组
            Map<String, List<MetricResult>> resultsByDbType = results.stream()
                    .collect(Collectors.groupingBy(MetricResult::getDbType));

            for (Map.Entry<String, List<MetricResult>> entry : resultsByDbType.entrySet()) {
                String dbType = entry.getKey() == null ? "unknown" : entry.getKey().toUpperCase();
                List<MetricResult> dbResults = entry.getValue();

                // 创建单值指标工作表
                createSingleValueSheet(workbook, dbType, dbResults, headerStyle, normalStyle, highRiskStyle, mediumRiskStyle);

                // 创建多值指标工作表
                createMultiValueSheets(workbook, dbType, dbResults, headerStyle, normalStyle, highRiskStyle, mediumRiskStyle);
            }

            // 保存文件
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }

            logger.info("Excel报告生成完成: {}", filePath.toString());
        }
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
        return "excel";
    }
    
    @Override
    public String getFileExtension() {
        return ".xlsx";
    }
    
    @Override
    public boolean supportsResultCount(int resultCount) {
        return resultCount <= 65536; // Excel行数限制
    }
    
    @Override
    public int getMaxSupportedResults() {
        return 65536;
    }
    
    @Override
    public String getDescription() {
        return "Excel报告生成器，支持阈值高亮和多工作表";
    }

    private void createSingleValueSheet(Workbook workbook, String dbType, List<MetricResult> results,
                                        CellStyle headerStyle, CellStyle normalStyle,
                                        CellStyle highRiskStyle, CellStyle mediumRiskStyle) {

        List<MetricResult> singleResults = results.stream()
                .filter(r -> "SINGLE".equals(r.getMetricType()) && r.isSuccess())
                .collect(Collectors.toList());

        if (singleResults.isEmpty()) {
            return;
        }

        Sheet sheet = workbook.createSheet(dbType);

        // 现在所有单值指标都有columns和multiValues，统一使用columns方式处理
        createSingleValueSheetWithColumns(sheet, singleResults, headerStyle, normalStyle, highRiskStyle, mediumRiskStyle, dbType);

        // 列宽自适应 + 边界 - 修复版本
        Row headerRow = sheet.getRow(0);
        if (headerRow != null) {
            int lastColIndex = headerRow.getLastCellNum();
            for (int i = 0; i < lastColIndex; i++) {
                try {
                    sheet.autoSizeColumn(i);
                    int w = sheet.getColumnWidth(i);
                    if (w > MAX_COL_WIDTH) {
                        sheet.setColumnWidth(i, MAX_COL_WIDTH);
                    } else if (w < MIN_COL_WIDTH) {
                        sheet.setColumnWidth(i, MIN_COL_WIDTH);
                    }
                    logger.debug("列 {} 宽度调整为: {}", i, sheet.getColumnWidth(i));
                } catch (Exception e) {
                    logger.warn("调整列 {} 宽度失败: {}", i, e.getMessage());
                    // 设置默认宽度
                    sheet.setColumnWidth(i, 4000);
                }
            }
            logger.info("Excel列宽自适应完成，共调整 {} 列", lastColIndex);
        }
    }

    /**
     * 创建支持columns的单值指标工作表
     */
    private void createSingleValueSheetWithColumns(Sheet sheet, List<MetricResult> singleResults,
                                                   CellStyle headerStyle, CellStyle normalStyle,
                                                   CellStyle highRiskStyle, CellStyle mediumRiskStyle, String dbType) {
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        int colIndex = 0;

        createCell(headerRow, colIndex++, "系统名称", headerStyle);
        createCell(headerRow, colIndex++, "数据库名称", headerStyle);
        createCell(headerRow, colIndex++, "节点IP", headerStyle);

        // 收集所有columns定义的列名
        Set<String> allColumns = new LinkedHashSet<>();
        Map<String, List<String>> metricColumnsMap = new HashMap<>();
        
        for (MetricResult result : singleResults) {
            if (result.getColumns() != null && !result.getColumns().isEmpty()) {
                List<String> columns = result.getColumns();
                metricColumnsMap.put(result.getMetricName(), columns);
                allColumns.addAll(columns);
            } else {
                // 无 columns 的单值指标：使用 description 或 name 作为列名
                String singleCol = (result.getMetricDescription() != null && !result.getMetricDescription().isEmpty())
                        ? result.getMetricDescription()
                        : result.getMetricName();
                if (singleCol != null && !singleCol.isEmpty()) {
                    List<String> derived = java.util.Collections.singletonList(singleCol);
                    metricColumnsMap.put(result.getMetricName(), derived);
                    allColumns.add(singleCol);
                }
            }
        }

        // 添加所有列名到表头
        for (String columnName : allColumns) {
            createCell(headerRow, colIndex++, columnName, headerStyle);
        }

        createCell(headerRow, colIndex++, "执行时间", headerStyle);

        // 按系统、数据库、节点分组
        Map<String, List<MetricResult>> groupedResults = singleResults.stream()
                .collect(Collectors.groupingBy(r -> r.getSystemName() + "|" + r.getDatabaseName() + "|" + r.getNodeIp()));

        int rowIndex = 1;
        for (Map.Entry<String, List<MetricResult>> entry : groupedResults.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            List<MetricResult> groupResults = entry.getValue();

            Row dataRow = sheet.createRow(rowIndex++);
            colIndex = 0;

            createCell(dataRow, colIndex++, keys[0], normalStyle); // 系统名称
            createCell(dataRow, colIndex++, keys[1], normalStyle); // 数据库名称
            createCell(dataRow, colIndex++, keys[2], normalStyle); // 节点IP

            // 处理每个columns列的数据
            for (String columnName : allColumns) {
                String cellValue = "";
                CellStyle cellStyle = normalStyle;
                
                // 查找包含此列的指标结果
                for (MetricResult result : groupResults) {
                    List<String> resultColumns = metricColumnsMap.get(result.getMetricName());
                    if (resultColumns != null && resultColumns.contains(columnName)) {
                        // 如果指标有多值数据，从multiValues中获取
                        if (result.getMultiValues() != null && !result.getMultiValues().isEmpty()) {
                            Map<String, Object> firstRow = result.getMultiValues().get(0);
                            Object v = firstRow.get(columnName);
                            if (v != null) {
                                cellValue = v.toString();
                                cellStyle = determineMultiValueStyle(dbType, result.getMetricName(), 
                                    result.getMetricDescription(), columnName, v,
                                    normalStyle, highRiskStyle, mediumRiskStyle);
                            }
                        } else if (result.getValue() != null) {
                            // 单值情况，如果只有一列则使用该值
                            if (resultColumns.size() == 1) {
                                cellValue = result.getValue().toString();
                                cellStyle = determineValueStyle(dbType, result, result.getValue(), 
                                    normalStyle, highRiskStyle, mediumRiskStyle);
                            }
                        }
                        break;
                    }
                }
                
                createCell(dataRow, colIndex++, cellValue, cellStyle);
            }

            // 执行时间
            String executeTime = groupResults.get(0).getExecuteTime().format(TIME_FORMATTER);
            createCell(dataRow, colIndex++, executeTime, normalStyle);
        }
    }

    /**
     * 创建传统单值指标工作表
     */
    private void createTraditionalSingleValueSheet(Sheet sheet, List<MetricResult> singleResults,
                                                   CellStyle headerStyle, CellStyle normalStyle,
                                                   CellStyle highRiskStyle, CellStyle mediumRiskStyle, String dbType) {
        
        // 获取所有唯一的指标名称
        Set<String> metricNames = singleResults.stream()
                .map(MetricResult::getMetricName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 创建表头
        Row headerRow = sheet.createRow(0);
        int colIndex = 0;

        createCell(headerRow, colIndex++, "系统名称", headerStyle);
        createCell(headerRow, colIndex++, "数据库名称", headerStyle);
        createCell(headerRow, colIndex++, "节点IP", headerStyle);

        for (String metricName : metricNames) {
            String description = singleResults.stream()
                    .filter(r -> metricName.equals(r.getMetricName()))
                    .map(MetricResult::getMetricDescription)
                    .findFirst()
                    .orElse(metricName);
            createCell(headerRow, colIndex++, description, headerStyle);
        }

        createCell(headerRow, colIndex++, "执行时间", headerStyle);

        // 按系统、数据库分组（忽略节点IP，避免重复行）
        Map<String, List<MetricResult>> groupedResults = singleResults.stream()
                .collect(Collectors.groupingBy(r -> r.getSystemName() + "|" + r.getDatabaseName()));

        int rowIndex = 1;
        for (Map.Entry<String, List<MetricResult>> entry : groupedResults.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            List<MetricResult> groupResults = entry.getValue();

            Row dataRow = sheet.createRow(rowIndex++);
            colIndex = 0;

            createCell(dataRow, colIndex++, keys[0], normalStyle); // 系统名称
            createCell(dataRow, colIndex++, keys[1], normalStyle); // 数据库名称
            String nodeIp = (groupResults != null && !groupResults.isEmpty() && groupResults.get(0).getNodeIp() != null)
                    ? groupResults.get(0).getNodeIp() : "unknown";
            createCell(dataRow, colIndex++, nodeIp, normalStyle); // 节点IP

            // 指标值
            Map<String, MetricResult> resultMap = groupResults.stream()
                    .collect(Collectors.toMap(MetricResult::getMetricName, r -> r));

            for (String metricName : metricNames) {
                MetricResult result = resultMap.get(metricName);
                if (result != null) {
                    Object value = result.getValue();
                    CellStyle cellStyle = determineValueStyle(dbType, result, value, normalStyle, highRiskStyle, mediumRiskStyle);
                    createCell(dataRow, colIndex++, value != null ? value.toString() : "", cellStyle);
                } else {
                    createCell(dataRow, colIndex++, "", normalStyle);
                }
            }

            // 执行时间
            String executeTime = groupResults.get(0).getExecuteTime().format(TIME_FORMATTER);
            createCell(dataRow, colIndex++, executeTime, normalStyle);
        }
    }

    private void createMultiValueSheets(Workbook workbook, String dbType, List<MetricResult> results,
                                        CellStyle headerStyle, CellStyle normalStyle,
                                        CellStyle highRiskStyle, CellStyle mediumRiskStyle) {

        List<MetricResult> multiResults = results.stream()
                .filter(r -> "MULTI".equals(r.getMetricType()) && r.isSuccess())
                .collect(Collectors.toList());

        // 按指标名称分组
        Map<String, List<MetricResult>> resultsByMetric = multiResults.stream()
                .collect(Collectors.groupingBy(MetricResult::getMetricName));

        for (Map.Entry<String, List<MetricResult>> entry : resultsByMetric.entrySet()) {
            String metricName = entry.getKey();
            List<MetricResult> metricResults = entry.getValue();
            if (metricResults.isEmpty()) continue;

            String description = metricResults.get(0).getMetricDescription();
            String sheetName = (dbType + "-" + description);
            if (sheetName.length() > 31) sheetName = sheetName.substring(0, 28) + "...";

            Sheet sheet = workbook.createSheet(sheetName);

            // 计算列名（优先使用指标定义的 columns，其次回退扫描数据行的 key）
            List<String> columnNames = null;
            for (MetricResult r : metricResults) {
                if (r.getColumns() != null && !r.getColumns().isEmpty()) {
                    columnNames = new ArrayList<>(r.getColumns());
                    break;
                }
            }
            if (columnNames == null) {
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (MetricResult r : metricResults) {
                    if (r.getMultiValues() != null) {
                        for (Map<String, Object> row : r.getMultiValues()) {
                            set.addAll(row.keySet());
                        }
                    }
                }
                columnNames = new ArrayList<>(set);
            }

            // 表头
            Row headerRow = sheet.createRow(0);
            int colIndex = 0;
            createCell(headerRow, colIndex++, "系统名称", headerStyle);
            createCell(headerRow, colIndex++, "数据库名称", headerStyle);
            createCell(headerRow, colIndex++, "节点IP", headerStyle);
            for (String columnName : columnNames) createCell(headerRow, colIndex++, columnName, headerStyle);
            createCell(headerRow, colIndex++, "执行时间", headerStyle);

            // 数据
            int rowIndex = 1;
            for (MetricResult result : metricResults) {
                if (result.getMultiValues() == null) continue;

                for (Map<String, Object> dataRowMap : result.getMultiValues()) {
                    Row excelRow = sheet.createRow(rowIndex++);
                    colIndex = 0;

                    createCell(excelRow, colIndex++, result.getSystemName(), normalStyle);
                    createCell(excelRow, colIndex++, result.getDatabaseName(), normalStyle);
                    createCell(excelRow, colIndex++, result.getNodeIp(), normalStyle);

                    for (String columnName : columnNames) {
                        Object value = dataRowMap.get(columnName);
                        CellStyle cellStyle = determineMultiValueStyle(dbType, metricName, description, columnName, value,
                                normalStyle, highRiskStyle, mediumRiskStyle);
                        createCell(excelRow, colIndex++, value != null ? value.toString() : "", cellStyle);
                    }

                    String executeTime = result.getExecuteTime().format(TIME_FORMATTER);
                    createCell(excelRow, colIndex++, executeTime, normalStyle);
                }
            }

            // 列宽自适应 + 边界
            for (int i = 0; i < colIndex; i++) {
                sheet.autoSizeColumn(i);
                int w = sheet.getColumnWidth(i);
                if (w > MAX_COL_WIDTH) sheet.setColumnWidth(i, MAX_COL_WIDTH);
                else if (w < MIN_COL_WIDTH) sheet.setColumnWidth(i, MIN_COL_WIDTH);
            }
        }
    }

    // 计算单值样式（支持 threshold.rules；无阈值时不高亮）
    private CellStyle determineValueStyle(String dbType, MetricResult result, Object value,
                                          CellStyle normalStyle, CellStyle highRiskStyle, CellStyle mediumRiskStyle) {
        ThresholdSpec spec = findThresholdSpec(dbType, result.getMetricName(), result.getMetricDescription());
        if (spec == null) {
            return normalStyle;
        }

        // 1) rules 优先：按顺序匹配，命中即着色
        if (spec.rules != null && !spec.rules.isEmpty()) {
            Double v = parseNumber(value);
            if (v != null) {
                for (ThresholdSpec.Rule r : spec.rules) {
                    Double th = parseNumber(r.value);
                    if (th != null && compare(v, r.operator, th)) {
                        return chooseStyleByLevel(r.level, highRiskStyle, mediumRiskStyle, normalStyle);
                    }
                }
            }
            return normalStyle;
        }

        // 2) 兼容旧写法：仅标量阈值适用于单值
        if (!(spec.value instanceof Map)) {
            Double v = parseNumber(value);
            Double th = parseNumber(spec.value);
            if (v != null && th != null && compare(v, spec.operator, th)) {
                return chooseStyleByLevel(spec.level, highRiskStyle, mediumRiskStyle, normalStyle);
            }
        }
        return normalStyle;
    }

    // 计算多值样式（支持 threshold.rules；无阈值时不高亮）
    private CellStyle determineMultiValueStyle(String dbType, String metricName, String metricDescription,
                                               String columnName, Object value,
                                               CellStyle normalStyle, CellStyle highRiskStyle, CellStyle mediumRiskStyle) {
        ThresholdSpec spec = findThresholdSpec(dbType, metricName, metricDescription);
        if (spec == null) {
            return normalStyle;
        }
        String colKey = normKey(columnName);

        // 1) 优先 rules：仅对匹配列生效，按顺序匹配（命中即着色）
        if (spec.rules != null && !spec.rules.isEmpty()) {
            for (ThresholdSpec.Rule r : spec.rules) {
                String effCol = (r.column != null) ? r.column : spec.column;
                if (effCol == null) {
                    // 未指定列则不对任意列生效
                    continue;
                }
                if (normKey(effCol).equals(colKey)) {
                    Double v = parseNumber(value);
                    Double th = parseNumber(r.value);
                    if (v != null && th != null && compare(v, r.operator, th)) {
                        return chooseStyleByLevel(r.level, highRiskStyle, mediumRiskStyle, normalStyle);
                    }
                    // 不要提前返回，继续检查后续规则（例如 high 未命中，继续匹配 medium）
                    continue;
                }
            }
            return normalStyle;
        }

        // 2) 兼容旧写法：value 为 Map（逐列阈值）
        if (spec.value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) spec.value;
            Double th = null;
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                if (e.getKey() == null) continue;
                String k = normKey(e.getKey().toString());
                if (k.equals(colKey)) {
                    th = parseNumber(e.getValue());
                    break;
                }
            }
            if (th != null) {
                Double v = parseNumber(value);
                if (v != null && compare(v, spec.operator, th)) {
                    return chooseStyleByLevel(spec.level, highRiskStyle, mediumRiskStyle, normalStyle);
                }
                return normalStyle;
            }
        }

        // 3) 兼容旧写法：column + 单值
        if (spec.column != null && normKey(spec.column).equals(colKey)) {
            Double v = parseNumber(value);
            Double th = parseNumber(spec.value);
            if (v != null && th != null && compare(v, spec.operator, th)) {
                return chooseStyleByLevel(spec.level, highRiskStyle, mediumRiskStyle, normalStyle);
            }
            return normalStyle;
        }

        // 存在阈值但不匹配当前列，不高亮
        return normalStyle;
    }

    // 启发式（保留原逻辑，作为兜底）
    private CellStyle heuristicSingle(Object value, MetricResult result,
                                      CellStyle normalStyle, CellStyle highRiskStyle, CellStyle mediumRiskStyle) {
        String level = result.getThresholdLevel();
        if ("high".equalsIgnoreCase(level)) return highRiskStyle;
        if ("medium".equalsIgnoreCase(level)) return mediumRiskStyle;

        if (value instanceof Number) {
            double num = ((Number) value).doubleValue();
            String metricName = result.getMetricName() != null ? result.getMetricName().toLowerCase() : "";
            if (metricName.contains("session") || metricName.contains("connection")) {
                if (num > 100) return highRiskStyle;
                if (num > 50) return mediumRiskStyle;
            } else if (metricName.contains("cpu") || metricName.contains("memory")) {
                if (num > 80) return highRiskStyle;
                if (num > 60) return mediumRiskStyle;
            } else if (metricName.contains("tablespace") || metricName.contains("disk")) {
                if (num > 85) return highRiskStyle;
                if (num > 70) return mediumRiskStyle;
            }
        }
        return normalStyle;
    }

    private CellStyle heuristicMulti(Object value, String columnName,
                                     CellStyle normalStyle, CellStyle highRiskStyle, CellStyle mediumRiskStyle) {
        if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            String lower = columnName.toLowerCase();
            if (lower.contains("percent") || lower.contains("usage") || lower.contains("rate") || lower.contains("ratio")) {
                if (numValue > 80) return highRiskStyle;
                if (numValue > 60) return mediumRiskStyle;
            } else if (lower.contains("count") || lower.contains("number")) {
                if (numValue > 1000) return highRiskStyle;
                if (numValue > 500) return mediumRiskStyle;
            } else if (lower.contains("size") || lower.contains("bytes")) {
                if (numValue > 10240) return highRiskStyle; // 10GB 假设单位MB
                if (numValue > 5120) return mediumRiskStyle; // 5GB
            }
        }
        return normalStyle;
    }

    private void createCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createHighRiskStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createMediumRiskStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createSummaryHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createSummaryStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    /**
     * 创建摘要工作表
     */
    private void createSummarySheet(Workbook workbook, List<MetricResult> results,
                                    CellStyle summaryHeaderStyle, CellStyle summaryStyle,
                                    CellStyle highRiskStyle, CellStyle mediumRiskStyle) {
        Sheet sheet = workbook.createSheet("执行摘要");

        int rowIndex = 0;

        // 标题
        Row titleRow = sheet.createRow(rowIndex++);
        createCell(titleRow, 0, "数据库指标收集报告摘要", summaryHeaderStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));

        rowIndex++; // 空行

        // 基本统计信息
        createSummarySection(sheet, rowIndex, "基本统计", results, summaryHeaderStyle, summaryStyle);
        rowIndex += 8;

        // 按数据库类型统计
        createDbTypeSection(sheet, rowIndex, "按数据库类型统计", results, summaryHeaderStyle, summaryStyle);
        rowIndex += 10;

        // 按系统统计
        createSystemSection(sheet, rowIndex, "按系统统计", results, summaryHeaderStyle, summaryStyle);
        rowIndex += 15;

        // 风险指标统计
        createRiskSection(sheet, rowIndex, "风险指标统计", results, summaryHeaderStyle, summaryStyle, highRiskStyle, mediumRiskStyle);

        // 列宽自适应 + 边界
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            if (w > MAX_COL_WIDTH) sheet.setColumnWidth(i, MAX_COL_WIDTH);
            else if (w < MIN_COL_WIDTH) sheet.setColumnWidth(i, MIN_COL_WIDTH);
        }
    }

    private void createSummarySection(Sheet sheet, int startRow, String title, List<MetricResult> results,
                                      CellStyle headerStyle, CellStyle normalStyle) {
        Row titleRow = sheet.createRow(startRow);
        createCell(titleRow, 0, title, headerStyle);

        long totalMetrics = results.size();
        long successCount = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        long failedCount = totalMetrics - successCount;
        long thresholdExceeded = results.stream().mapToLong(r -> r.getThresholdLevel() != null ? 1 : 0).sum();

        String executeTime = results.isEmpty() ? "N/A" :
                results.get(0).getExecuteTime().format(TIME_FORMATTER);

        Row[] dataRows = new Row[6];
        for (int i = 0; i < 6; i++) {
            dataRows[i] = sheet.createRow(startRow + 1 + i);
        }

        createCell(dataRows[0], 0, "执行时间:", normalStyle);
        createCell(dataRows[0], 1, executeTime, normalStyle);

        createCell(dataRows[1], 0, "总指标数:", normalStyle);
        createCell(dataRows[1], 1, String.valueOf(totalMetrics), normalStyle);

        createCell(dataRows[2], 0, "成功数:", normalStyle);
        createCell(dataRows[2], 1, String.valueOf(successCount), normalStyle);

        createCell(dataRows[3], 0, "失败数:", normalStyle);
        createCell(dataRows[3], 1, String.valueOf(failedCount), failedCount > 0 ? sheet.getWorkbook().createCellStyle() : normalStyle);

        createCell(dataRows[4], 0, "超阈值数:", normalStyle);
        createCell(dataRows[4], 1, String.valueOf(thresholdExceeded), thresholdExceeded > 0 ? sheet.getWorkbook().createCellStyle() : normalStyle);

        createCell(dataRows[5], 0, "成功率:", normalStyle);
        String successRate = totalMetrics > 0 ? String.format("%.2f%%", (double) successCount / totalMetrics * 100) : "0%";
        createCell(dataRows[5], 1, successRate, normalStyle);
    }

    private void createDbTypeSection(Sheet sheet, int startRow, String title, List<MetricResult> results,
                                     CellStyle headerStyle, CellStyle normalStyle) {
        Row titleRow = sheet.createRow(startRow);
        createCell(titleRow, 0, title, headerStyle);

        Map<String, Long> dbTypeStats = results.stream()
                .collect(Collectors.groupingBy(MetricResult::getDbType, Collectors.counting()));

        Row headerRow = sheet.createRow(startRow + 1);
        createCell(headerRow, 0, "数据库类型", headerStyle);
        createCell(headerRow, 1, "指标数量", headerStyle);
        createCell(headerRow, 2, "成功数", headerStyle);
        createCell(headerRow, 3, "失败数", headerStyle);

        int rowIndex = startRow + 2;
        for (Map.Entry<String, Long> entry : dbTypeStats.entrySet()) {
            String dbType = entry.getKey();
            long total = entry.getValue();

            long success = results.stream().filter(r -> Objects.equals(dbType, r.getDbType()) && r.isSuccess()).count();
            long failed = total - success;

            Row dataRow = sheet.createRow(rowIndex++);
            createCell(dataRow, 0, dbType != null ? dbType.toUpperCase() : "UNKNOWN", normalStyle);
            createCell(dataRow, 1, String.valueOf(total), normalStyle);
            createCell(dataRow, 2, String.valueOf(success), normalStyle);
            createCell(dataRow, 3, String.valueOf(failed), normalStyle);
        }
    }

    private void createSystemSection(Sheet sheet, int startRow, String title, List<MetricResult> results,
                                     CellStyle headerStyle, CellStyle normalStyle) {
        Row titleRow = sheet.createRow(startRow);
        createCell(titleRow, 0, title, headerStyle);

        Map<String, Long> systemStats = results.stream()
                .collect(Collectors.groupingBy(MetricResult::getSystemName, Collectors.counting()));

        Row headerRow = sheet.createRow(startRow + 1);
        createCell(headerRow, 0, "系统名称", headerStyle);
        createCell(headerRow, 1, "指标数量", headerStyle);
        createCell(headerRow, 2, "成功数", headerStyle);
        createCell(headerRow, 3, "超阈值数", headerStyle);

        int rowIndex = startRow + 2;
        for (Map.Entry<String, Long> entry : systemStats.entrySet()) {
            String systemName = entry.getKey();
            long total = entry.getValue();

            long success = results.stream().filter(r -> Objects.equals(systemName, r.getSystemName()) && r.isSuccess()).count();
            long thresholdExceeded = results.stream().filter(r -> Objects.equals(systemName, r.getSystemName()) && r.getThresholdLevel() != null).count();

            Row dataRow = sheet.createRow(rowIndex++);
            createCell(dataRow, 0, systemName, normalStyle);
            createCell(dataRow, 1, String.valueOf(total), normalStyle);
            createCell(dataRow, 2, String.valueOf(success), normalStyle);
            createCell(dataRow, 3, String.valueOf(thresholdExceeded), normalStyle);
        }
    }

    private void createRiskSection(Sheet sheet, int startRow, String title, List<MetricResult> results,
                                   CellStyle headerStyle, CellStyle normalStyle,
                                   CellStyle highRiskStyle, CellStyle mediumRiskStyle) {
        Row titleRow = sheet.createRow(startRow);
        createCell(titleRow, 0, title, headerStyle);

        List<MetricResult> riskResults = results.stream().filter(r -> r.getThresholdLevel() != null).collect(Collectors.toList());
        if (riskResults.isEmpty()) {
            Row noRiskRow = sheet.createRow(startRow + 1);
            createCell(noRiskRow, 0, "未发现超过阈值的指标", normalStyle);
            return;
        }

        Row headerRow = sheet.createRow(startRow + 1);
        createCell(headerRow, 0, "系统名称", headerStyle);
        createCell(headerRow, 1, "指标名称", headerStyle);
        createCell(headerRow, 2, "指标值", headerStyle);
        createCell(headerRow, 3, "风险级别", headerStyle);
        createCell(headerRow, 4, "节点IP", headerStyle);

        int rowIndex = startRow + 2;
        for (MetricResult result : riskResults) {
            Row dataRow = sheet.createRow(rowIndex++);
            createCell(dataRow, 0, result.getSystemName(), normalStyle);
            createCell(dataRow, 1, result.getMetricName(), normalStyle);
            String value = result.getValue() != null ? result.getValue().toString() : "N/A";
            createCell(dataRow, 2, value, normalStyle);
            String level = result.getThresholdLevel();
            CellStyle levelStyle = "high".equalsIgnoreCase(level) ? highRiskStyle :
                    "medium".equalsIgnoreCase(level) ? mediumRiskStyle : normalStyle;
            createCell(dataRow, 3, level != null ? level.toUpperCase() : "UNKNOWN", levelStyle);
            createCell(dataRow, 4, result.getNodeIp(), normalStyle);
        }
    }

    /* ========================== 阈值加载与比较 ========================== */

    private void ensureThresholdsLoaded(String metricsDir) {
        if (thresholdsLoaded) return;
        try {
            thresholdsCache = loadThresholdsFromDir(metricsDir);
            thresholdsLoaded = true;
            logger.info("已加载阈值配置条目（按库类型/指标映射）: {}", thresholdsCache.values().stream().mapToInt(Map::size).sum());
        } catch (Exception e) {
            logger.warn("加载指标阈值配置失败（将使用启发式规则）: {}", e.getMessage());
            thresholdsCache = new HashMap<>();
        }
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

    private Map<String, Map<String, ThresholdSpec>> loadThresholdsFromDir(String metricsDir) throws IOException {
        Map<String, Map<String, ThresholdSpec>> result = new HashMap<>();
        Path dir = Paths.get(metricsDir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            logger.warn("阈值加载：指标目录不存在或不是目录: {}", metricsDir);
            return result;
        }

        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith("-metrics.yml") || name.endsWith("-metrics.yaml"));
        if (files == null) return result;

        for (File f : files) {
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
                        //noinspection unchecked
                        metricsList.addAll((List<Map<String, Object>>) metricsObj);
                    }
                } else if (data instanceof List) {
                    //noinspection unchecked
                    metricsList.addAll((List<Map<String, Object>>) data);
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
                        // 兼容旧写法：单阈值或 value 为 Map、column+value
                        spec.level = asString(thr.get("level"));
                        spec.operator = asString(thr.get("operator"));
                        spec.column = asString(thr.get("column"));
                        spec.value = thr.get("value");
                    }

                    // 指标名与描述都建立映射（规避名称/描述二选一的情况）
                    if (name != null) result.get(dbKey).put(normKey(name), spec);
                    if (desc != null) result.get(dbKey).put(normKey(desc), spec);
                }
            } catch (Exception ex) {
                logger.warn("解析指标阈值文件失败：{}，原因：{}", f.getName(), ex.getMessage());
            }
        }

        return result;
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
        // 保留所有字母与数字（包含中文等 Unicode 字符），仅移除空白与标点等非字母数字字符
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}]", "");
        return normalized;
    }

    private Double parseNumber(Object v) {
        if (v == null) return null;
        try {
            String s = v.toString().trim();
            // 移除末尾%与千分位逗号等
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
                // 未知操作符按 ">" 处理
                return v > th;
        }
    }

    private CellStyle chooseStyleByLevel(String level, CellStyle high, CellStyle medium, CellStyle normal) {
        if (level == null) return high; // 未指定时倾向高亮
        String lv = level.toLowerCase(Locale.ROOT);
        if ("high".equals(lv)) return high;
        if ("medium".equals(lv)) return medium;
        return high; // 其他值默认按 high
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
}