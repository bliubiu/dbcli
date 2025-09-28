package com.dbcli.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A factory for creating report generators based on the specified format.
 */
public class ReportGeneratorFactory {

    /**
     * Creates a list of report generators for the given format.
     *
     * @param format The desired report format (e.g., "excel", "html", "both").
     * @return A list of {@link ReportGenerator} instances.
     */
    public List<ReportGenerator> createGenerators(String format) {
        List<ReportGenerator> generators = new ArrayList<>();
        String lowerFormat = format != null ? format.toLowerCase(Locale.ROOT) : "excel";

        if ("excel".equals(lowerFormat) || "both".equals(lowerFormat)) {
            generators.add(new ExcelReportGeneratorAdapter());
        }
        if ("html".equals(lowerFormat) || "both".equals(lowerFormat)) {
            generators.add(new HtmlReportGeneratorAdapter());
        }
        
        return generators;
    }
    
    /**
     * Adapter for ExcelReportGenerator to match ReportGenerator interface
     */
    private static class ExcelReportGeneratorAdapter implements ReportGenerator {
        private final ExcelReportGenerator generator = new ExcelReportGenerator();
        
        @Override
        public void generate(List<com.dbcli.model.MetricResult> results, String outputPath, String metricsPath) throws Exception {
            generator.generate(results, outputPath, metricsPath);
        }
        
        @Override
        public String getFormat() {
            return "excel";
        }
    }
    
    /**
     * Adapter for HtmlReportGenerator to match ReportGenerator interface
     */
    private static class HtmlReportGeneratorAdapter implements ReportGenerator {
        private final HtmlReportGenerator generator = new HtmlReportGenerator();
        
        @Override
        public void generate(List<com.dbcli.model.MetricResult> results, String outputPath, String metricsPath) throws Exception {
            generator.generate(results, outputPath, metricsPath);
        }
        
        @Override
        public String getFormat() {
            return "html";
        }
    }
}