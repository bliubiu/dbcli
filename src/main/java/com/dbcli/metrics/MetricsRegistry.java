package com.dbcli.metrics;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量内存指标注册表，支持导出 Prometheus 文本格式（v0.0.4）
 */
public final class MetricsRegistry {

    private static final MetricsRegistry INSTANCE = new MetricsRegistry();

    // 简单的 gauge/counter 存储（不区分类型，按名称直接覆盖/累加）
    private final ConcurrentHashMap<String, Double> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> labels = new ConcurrentHashMap<>();

    private MetricsRegistry() {}

    public static MetricsRegistry getInstance() {
        return INSTANCE;
    }

    // Gauge: set
    public void setGauge(String name, double value) {
        gauges.put(name, value);
    }

    public void setGauge(String name, double value, Map<String, String> labelSet) {
        gauges.put(name, value);
        if (labelSet != null && !labelSet.isEmpty()) {
            labels.put(name, labelSet);
        }
    }

    // Counter: add
    public void addCounter(String name, double delta) {
        counters.merge(name, delta, Double::sum);
    }

    public void addCounter(String name, double delta, Map<String, String> labelSet) {
        counters.merge(name, delta, Double::sum);
        if (labelSet != null && !labelSet.isEmpty()) {
            labels.put(name, labelSet);
        }
    }

    public void setNowEpochGauge(String name) {
        setGauge(name, (double) Instant.now().getEpochSecond());
    }

    /**
     * 导出为 Prometheus 文本格式
     * - 为简化起见，HELP/TYPE 仅对常用指标给出基本声明
     */
    public String renderPrometheus() {
        StringBuilder sb = new StringBuilder();

        // 可选的帮助与类型声明（常见我们自己维护的指标前缀）
        appendType(sb, "dbcli_connection_test_total", "gauge");
        appendType(sb, "dbcli_connection_test_success_total", "gauge");
        appendType(sb, "dbcli_connection_test_failed_total", "gauge");
        appendType(sb, "dbcli_last_connection_test_timestamp_seconds", "gauge");

        appendType(sb, "dbcli_report_generation_success", "gauge");
        appendType(sb, "dbcli_last_report_generated_timestamp_seconds", "gauge");
        appendType(sb, "dbcli_last_report_format_info", "gauge");

        // Dump gauges
        for (Map.Entry<String, Double> e : gauges.entrySet()) {
            String name = sanitizeName(e.getKey());
            Map<String, String> lbl = labels.get(name);
            sb.append(name);
            if (lbl != null && !lbl.isEmpty()) {
                sb.append("{").append(renderLabels(lbl)).append("}");
            }
            sb.append(" ").append(renderNumber(e.getValue())).append("\n");
        }

        // Dump counters（同样以数值形式导出）
        for (Map.Entry<String, Double> e : counters.entrySet()) {
            String name = sanitizeName(e.getKey());
            Map<String, String> lbl = labels.get(name);
            sb.append(name);
            if (lbl != null && !lbl.isEmpty()) {
                sb.append("{").append(renderLabels(lbl)).append("}");
            }
            sb.append(" ").append(renderNumber(e.getValue())).append("\n");
        }

        return sb.toString();
    }

    private static void appendType(StringBuilder sb, String metric, String type) {
        sb.append("# TYPE ").append(metric).append(" ").append(type).append("\n");
    }

    private static String renderLabels(Map<String, String> labels) {
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (!first) b.append(",");
            first = false;
            b.append(sanitizeLabelName(e.getKey())).append("=\"").append(escapeLabelValue(e.getValue())).append("\"");
        }
        return b.toString();
    }

    private static String escapeLabelValue(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sanitizeName(String n) {
        if (n == null) return "";
        // Prometheus 指标名字符限制：使用字母数字和下划线
        return n.replaceAll("[^a-zA-Z0-9_:]", "_");
    }

    private static String sanitizeLabelName(String n) {
        if (n == null) return "";
        return n.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String renderNumber(Double d) {
        if (d == null || d.isNaN() || d.isInfinite()) return "0";
        // 避免科学计数
        if (Math.floor(d) == d) {
            return String.format("%.0f", d);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", d);
    }
}