package com.dbcli.service;

import com.dbcli.model.MetricResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 流式Excel报告生成器
 * 支持大数据量的内存友好型Excel生成
 */
public class StreamingExcelReportGenerator implements ReportGeneratorInterface {
    private static final Logger logger = LoggerFactory.getLogger(StreamingExcelReportGenerator.class);
    
    private static final int DEFAULT_ROW_ACCESS_WINDOW_SIZE = 1000; // 内存中保持的行数
    private static final int BATCH_SIZE = 5000; // 批处理大小
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final int rowAccessWindowSize;
    
    public StreamingExcelReportGenerator() {
        this.rowAccessWindowSize = DEFAULT_ROW_ACCESS_WINDOW_SIZE;
    }
    
    public StreamingExcelReportGenerator(int rowAccessWindowSize) {
        this.rowAccessWindowSize = rowAccessWindowSize;
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
        
        logger.info("开始生成流式Excel报告，数据量: {}, 输出路径: {}", results.size(), outputPath);
        long startTime = System.currentTimeMillis();
        
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize);
             FileOutputStream fileOut = new FileOutputStream(outputPath)) {
            
            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            
            // 按指标类型分组生成工作表
            generateSingleValueSheet(workbook, results, headerStyle, dataStyle, numberStyle, dateStyle);
            generateMultiValueSheet(workbook, results, headerStyle, dataStyle, numberStyle, dateStyle);
            generateSummarySheet(workbook, results, headerStyle, dataStyle, numberStyle);
            
            // 写入文件
            workbook.write(fileOut);
            
            // 清理临时文件
            workbook.dispose();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("流式Excel报告生成完成，耗时: {}ms, 文件大小: {} KB", 
                duration, new java.io.File(outputPath).length() / 1024);
                
        } catch (Exception e) {
            logger.error("生成流式Excel报告失败", e);
            throw e;
        }
    }
    
    /**
     * 生成单值指标工作表
     */
    private void generateSingleValueSheet(SXSSFWorkbook workbook, List<MetricResult> results,
                                        CellStyle headerStyle, CellStyle dataStyle, 
                                        CellStyle numberStyle, CellStyle dateStyle) {
        
        List<MetricResult> singleValueResults = results.stream()
            .filter(r -> "SINGLE".equals(r.getMetricType()))
            .toList();
            
        if (singleValueResults.isEmpty()) {
            return;
        }
        
        logger.info("生成单值指标工作表，数据量: {}", singleValueResults.size());
        
        // 按数据库类型分组创建工作表
        Map<String, List<MetricResult>> resultsByDbType = singleValueResults.stream()
            .collect(java.util.stream.Collectors.groupingBy(r -> 
                r.getDbType() != null ? r.getDbType().toUpperCase() : "UNKNOWN"));
        
        for (Map.Entry<String, List<MetricResult>> entry : resultsByDbType.entrySet()) {
            String dbType = entry.getKey();
            List<MetricResult> dbResults = entry.getValue();
            
            // 检查是否有指标定义了columns（支持多列单值指标）
            boolean hasColumnsMetrics = dbResults.stream()
                .anyMatch(r -> r.getColumns() != null && !r.getColumns().isEmpty());
            
            Sheet sheet = workbook.createSheet(dbType);
            
            if (hasColumnsMetrics) {
                generateSingleValueSheetWithColumns(sheet, dbResults, headerStyle, dataStyle, numberStyle, dateStyle);
            } else {
                generateTraditionalSingleValueSheet(sheet, dbResults, headerStyle, dataStyle, numberStyle, dateStyle);
            }
        }
        
        logger.info("单值指标工作表生成完成");
    }
    
    /**
     * 生成支持columns的单值指标工作表
     */
    private void generateSingleValueSheetWithColumns(Sheet sheet, List<MetricResult> singleValueResults,
                                                   CellStyle headerStyle, CellStyle dataStyle, 
                                                   CellStyle numberStyle, CellStyle dateStyle) {
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        int colIndex = 0;
        
        setCellValue(headerRow, colIndex++, "系统名称", headerStyle);
        setCellValue(headerRow, colIndex++, "数据库名称", headerStyle);
        setCellValue(headerRow, colIndex++, "节点IP", headerStyle);
        
        // 收集所有columns定义的列名
        java.util.Set<String> allColumns = new java.util.LinkedHashSet<>();
        java.util.Map<String, java.util.List<String>> metricColumnsMap = new java.util.HashMap<>();
        
        for (MetricResult result : singleValueResults) {
            if (result.getColumns() != null && !result.getColumns().isEmpty()) {
                java.util.List<String> columns = result.getColumns();
                metricColumnsMap.put(result.getMetricName(), columns);
                allColumns.addAll(columns);
            }
        }
        
        // 添加所有列名到表头
        for (String columnName : allColumns) {
            setCellValue(headerRow, colIndex++, columnName, headerStyle);
        }
        
        setCellValue(headerRow, colIndex++, "执行时间", headerStyle);
        
        // 按系统、数据库、节点分组
        java.util.Map<String, java.util.List<MetricResult>> groupedResults = singleValueResults.stream()
            .collect(java.util.stream.Collectors.groupingBy(r -> 
                r.getSystemName() + "|" + r.getDatabaseName() + "|" + r.getNodeIp()));
        
        int rowIndex = 1;
        int batchCount = 0;
        
        for (java.util.Map.Entry<String, java.util.List<MetricResult>> entry : groupedResults.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            java.util.List<MetricResult> groupResults = entry.getValue();
            
            Row dataRow = sheet.createRow(rowIndex++);
            colIndex = 0;
            
            setCellValue(dataRow, colIndex++, keys[0], dataStyle); // 系统名称
            setCellValue(dataRow, colIndex++, keys[1], dataStyle); // 数据库名称
            setCellValue(dataRow, colIndex++, keys[2], dataStyle); // 节点IP
            
            // 处理每个columns列的数据
            for (String columnName : allColumns) {
                String cellValue = "";
                CellStyle cellStyle = dataStyle;
                
                // 查找包含此列的指标结果
                for (MetricResult result : groupResults) {
                    java.util.List<String> resultColumns = metricColumnsMap.get(result.getMetricName());
                    if (resultColumns != null && resultColumns.contains(columnName)) {
                        // 如果指标有多值数据，从multiValues中获取
                        if (result.getMultiValues() != null && !result.getMultiValues().isEmpty()) {
                            java.util.Map<String, Object> firstRow = result.getMultiValues().get(0);
                            int columnIndex = resultColumns.indexOf(columnName);
                            if (columnIndex >= 0 && columnIndex < firstRow.size()) {
                                Object[] values = firstRow.values().toArray();
                                if (columnIndex < values.length && values[columnIndex] != null) {
                                    cellValue = values[columnIndex].toString();
                                    if (values[columnIndex] instanceof Number) {
                                        cellStyle = numberStyle;
                                    }
                                }
                            }
                        } else if (result.getValue() != null) {
                            // 单值情况，如果只有一列则使用该值
                            if (resultColumns.size() == 1) {
                                cellValue = result.getValue().toString();
                                if (result.getValue() instanceof Number) {
                                    cellStyle = numberStyle;
                                }
                            }
                        }
                        break;
                    }
                }
                
                setCellValue(dataRow, colIndex++, cellValue, cellStyle);
            }
            
            // 执行时间
            if (groupResults.get(0).getExecuteTime() != null) {
                String executeTime = groupResults.get(0).getExecuteTime().format(DATE_FORMATTER);
                setCellValue(dataRow, colIndex++, executeTime, dateStyle);
            } else {
                setCellValue(dataRow, colIndex++, "", dataStyle);
            }
            
            // 批处理进度日志
            if (++batchCount % BATCH_SIZE == 0) {
                logger.debug("已处理单值指标: {}/{}", batchCount, singleValueResults.size());
            }
        }
        
        // 自动调整列宽（仅对前几列，避免性能问题）
        // 对于SXSSFSheet，需要先跟踪列
        if (sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet) {
            ((org.apache.poi.xssf.streaming.SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
        }
        for (int i = 0; i < Math.min(colIndex, 6); i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * 生成传统单值指标工作表
     */
    private void generateTraditionalSingleValueSheet(Sheet sheet, List<MetricResult> singleValueResults,
                                                   CellStyle headerStyle, CellStyle dataStyle, 
                                                   CellStyle numberStyle, CellStyle dateStyle) {
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"系统名称", "数据库名称", "节点IP", "指标名称", "指标描述", 
                           "指标值", "单位", "阈值状态", "收集时间", "执行状态", "错误信息"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 批量写入数据
        int rowIndex = 1;
        int batchCount = 0;
        
        for (MetricResult result : singleValueResults) {
            Row row = sheet.createRow(rowIndex++);
            
            setCellValue(row, 0, result.getSystemName(), dataStyle);
            setCellValue(row, 1, result.getDatabaseType(), dataStyle);
            setCellValue(row, 2, result.getNodeIp(), dataStyle);
            setCellValue(row, 3, result.getMetricName(), dataStyle);
            setCellValue(row, 4, result.getDescription(), dataStyle);
            
            // 指标值
            if (result.getValue() != null) {
                if (result.getValue() instanceof Number) {
                    setCellValue(row, 5, ((Number) result.getValue()).doubleValue(), numberStyle);
                } else {
                    setCellValue(row, 5, result.getValue().toString(), dataStyle);
                }
            }
            
            setCellValue(row, 6, result.getUnit(), dataStyle);
            setCellValue(row, 7, result.getThresholdLevel(), dataStyle);
            
            if (result.getCollectTime() != null) {
                setCellValue(row, 8, result.getCollectTime().format(DATE_FORMATTER), dateStyle);
            }
            
            setCellValue(row, 9, result.isSuccess() ? "成功" : "失败", dataStyle);
            setCellValue(row, 10, result.getErrorMessage(), dataStyle);
            
            // 批处理进度日志
            if (++batchCount % BATCH_SIZE == 0) {
                logger.debug("已处理单值指标: {}/{}", batchCount, singleValueResults.size());
            }
        }
        
        // 自动调整列宽（仅对前几列，避免性能问题）
        // 对于SXSSFSheet，需要先跟踪列
        if (sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet) {
            ((org.apache.poi.xssf.streaming.SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
        }
        for (int i = 0; i < Math.min(headers.length, 6); i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * 生成多值指标工作表
     */
    private void generateMultiValueSheet(SXSSFWorkbook workbook, List<MetricResult> results,
                                       CellStyle headerStyle, CellStyle dataStyle, 
                                       CellStyle numberStyle, CellStyle dateStyle) {
        
        List<MetricResult> multiValueResults = results.stream()
            .filter(r -> "MULTI".equals(r.getType()) && r.getMultiValues() != null && !r.getMultiValues().isEmpty())
            .toList();
            
        if (multiValueResults.isEmpty()) {
            return;
        }
        
        logger.info("生成多值指标工作表，指标数量: {}", multiValueResults.size());
        
        Sheet sheet = workbook.createSheet("多值指标");
        int rowIndex = 0;
        
        for (MetricResult result : multiValueResults) {
            // 为每个指标创建一个数据块
            if (rowIndex > 0) {
                rowIndex++; // 空行分隔
            }
            
            // 指标信息行
            Row infoRow = sheet.createRow(rowIndex++);
            setCellValue(infoRow, 0, String.format("指标: %s (%s)", result.getMetricName(), result.getSystemName()), headerStyle);
            setCellValue(infoRow, 1, String.format("数据库: %s", result.getDatabaseType()), headerStyle);
            setCellValue(infoRow, 2, String.format("节点: %s", result.getNodeIp()), headerStyle);
            
            List<Map<String, Object>> multiValues = result.getMultiValues();
            if (multiValues.isEmpty()) {
                continue;
            }
            
            // 动态表头
            Map<String, Object> firstRow = multiValues.get(0);
            Row headerRow = sheet.createRow(rowIndex++);
            String[] columnNames = firstRow.keySet().toArray(new String[0]);
            
            for (int i = 0; i < columnNames.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnNames[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 数据行
            int dataRowCount = 0;
            for (Map<String, Object> dataRow : multiValues) {
                Row row = sheet.createRow(rowIndex++);
                
                for (int i = 0; i < columnNames.length; i++) {
                    Object value = dataRow.get(columnNames[i]);
                    if (value instanceof Number) {
                        setCellValue(row, i, ((Number) value).doubleValue(), numberStyle);
                    } else if (value != null) {
                        setCellValue(row, i, value.toString(), dataStyle);
                    }
                }
                
                dataRowCount++;
                
                // 大数据量时的进度日志
                if (dataRowCount % BATCH_SIZE == 0) {
                    logger.debug("指标 {} 已处理数据行: {}/{}", result.getMetricName(), dataRowCount, multiValues.size());
                }
            }
            
            logger.debug("指标 {} 完成，数据行数: {}", result.getMetricName(), dataRowCount);
        }
        
        logger.info("多值指标工作表生成完成");
    }
    
    /**
     * 生成汇总工作表
     */
    private void generateSummarySheet(SXSSFWorkbook workbook, List<MetricResult> results,
                                    CellStyle headerStyle, CellStyle dataStyle, CellStyle numberStyle) {
        
        Sheet sheet = workbook.createSheet("执行汇总");
        
        // 统计信息
        long totalMetrics = results.size();
        long successfulMetrics = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        long failedMetrics = totalMetrics - successfulMetrics;
        double successRate = totalMetrics > 0 ? (double) successfulMetrics / totalMetrics * 100 : 0;
        
        // 按数据库类型统计
        Map<String, Long> dbTypeStats = results.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                MetricResult::getDatabaseType,
                java.util.stream.Collectors.counting()
            ));
        
        // 按系统统计
        Map<String, Long> systemStats = results.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                MetricResult::getSystemName,
                java.util.stream.Collectors.counting()
            ));
        
        int rowIndex = 0;
        
        // 总体统计
        Row titleRow = sheet.createRow(rowIndex++);
        setCellValue(titleRow, 0, "执行汇总统计", headerStyle);
        
        rowIndex++; // 空行
        
        setCellValue(sheet.createRow(rowIndex++), 0, "总指标数", dataStyle);
        setCellValue(sheet.getRow(rowIndex - 1), 1, totalMetrics, numberStyle);
        
        setCellValue(sheet.createRow(rowIndex++), 0, "成功指标数", dataStyle);
        setCellValue(sheet.getRow(rowIndex - 1), 1, successfulMetrics, numberStyle);
        
        setCellValue(sheet.createRow(rowIndex++), 0, "失败指标数", dataStyle);
        setCellValue(sheet.getRow(rowIndex - 1), 1, failedMetrics, numberStyle);
        
        setCellValue(sheet.createRow(rowIndex++), 0, "成功率", dataStyle);
        setCellValue(sheet.getRow(rowIndex - 1), 1, String.format("%.2f%%", successRate), dataStyle);
        
        rowIndex++; // 空行
        
        // 数据库类型统计
        setCellValue(sheet.createRow(rowIndex++), 0, "按数据库类型统计", headerStyle);
        for (Map.Entry<String, Long> entry : dbTypeStats.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            setCellValue(row, 0, entry.getKey(), dataStyle);
            setCellValue(row, 1, entry.getValue(), numberStyle);
        }
        
        rowIndex++; // 空行
        
        // 系统统计
        setCellValue(sheet.createRow(rowIndex++), 0, "按系统统计", headerStyle);
        for (Map.Entry<String, Long> entry : systemStats.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            setCellValue(row, 0, entry.getKey(), dataStyle);
            setCellValue(row, 1, entry.getValue(), numberStyle);
        }
        
        // 自动调整列宽
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        
        logger.info("汇总工作表生成完成");
    }
    
    /**
     * 创建表头样式
     */
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
        return style;
    }
    
    /**
     * 创建数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
    
    /**
     * 创建数字样式
     */
    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    /**
     * 创建日期样式
     */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }
    
    /**
     * 设置单元格值
     */
    private void setCellValue(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value != null) {
            if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else {
                cell.setCellValue(value.toString());
            }
        }
        cell.setCellStyle(style);
    }
    
    @Override
    public String getReportFormat() {
        return "streaming_excel";
    }
    
    @Override
    public String getFileExtension() {
        return ".xlsx";
    }
    
    @Override
    public boolean supportsResultCount(int resultCount) {
        return true; // 流式处理支持任意数量
    }
    
    @Override
    public int getMaxSupportedResults() {
        return Integer.MAX_VALUE;
    }
    
    @Override
    public String getDescription() {
        return "流式Excel报告生成器，支持大数据量的内存友好型处理";
    }
}