package com.dbcli.metrics;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 通过 Pushgateway 推送指标（可选）
 * 配置方式：设置环境变量 DBCLI_PUSHGATEWAY_URL，例如：
 *   http://localhost:9091    或完整路径 http://localhost:9091/metrics/job/dbcli
 * 若仅提供主机，将自动使用默认路径 /metrics/job/dbcli
 */
public final class PrometheusPushUtil {

    private static final String DEFAULT_JOB_PATH = "/metrics/job/dbcli";

    private PrometheusPushUtil() {}

    public static void pushIfConfigured(String prometheusBody) {
        try {
            String base = System.getenv("DBCLI_PUSHGATEWAY_URL");
            if (base == null || base.isBlank()) {
                return; // 未配置则跳过
            }
            String target = base.endsWith("/metrics") || base.contains("/metrics/job")
                    ? base
                    : base.replaceAll("/+$", "") + DEFAULT_JOB_PATH;

            URL url = new URL(target);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/plain; version=0.0.4; charset=utf-8");

            byte[] data = prometheusBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int code = conn.getResponseCode();
            // 2xx 视为成功，其它忽略（避免影响主流程）
            if (code / 100 != 2) {
                // 静默失败，不抛异常
            }
            conn.disconnect();
        } catch (Exception ignored) {
            // 静默处理，避免影响主路径
        }
    }
}