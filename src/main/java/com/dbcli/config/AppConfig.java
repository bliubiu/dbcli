package com.dbcli.config;

import com.dbcli.model.DatabaseConfig;

import java.util.List;
import java.util.ArrayList;

/**
 * 应用配置类
 * 说明：
 * - 日志输出由 src/main/resources/logback.xml 管理；
 * - logFile 仅作为“可选的自定义追加文件”参数，默认不启用（null），避免与 logback 冲突；
 * - 其他运行参数保持兼容以便 CLI 与主流程按原逻辑工作。
 */
public class AppConfig {
    private String configPath = "configs/";
    private String metricsPath = "metrics/";
    private String outputPath = "reports/";
    private String format = "excel";
    private int threads = 7;
    private boolean encrypt = false;
    private boolean test = false;
    private boolean clean = false;
    private boolean template = false;
    private String logLevel = "INFO";
    private boolean help = false;
    private boolean verbose = false;
    private boolean quiet = false;
    private boolean interactiveTemplate = false;
    // 将默认日志文件从固定 "dbcli.log" 改为 null，完全由 logback 控制；仅当显式设置时才追加文件输出
    private String logFile = null;
    private boolean dryRun = false;
    private boolean webManagement = false;
    private int webPort = 8080;
    private List<DatabaseConfig> databases = new ArrayList<>();

    // Getters and Setters
    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public String getMetricsPath() {
        return metricsPath;
    }

    public void setMetricsPath(String metricsPath) {
        this.metricsPath = metricsPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public boolean isEncrypt() {
        return encrypt;
    }

    public void setEncrypt(boolean encrypt) {
        this.encrypt = encrypt;
    }

    public boolean isTest() {
        return test;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public boolean isClean() {
        return clean;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    // 新增的getter和setter方法（兼容调用）
    public String getOutputFormat() {
        return format;
    }

    public void setOutputFormat(String format) {
        this.format = format;
    }

    public int getConcurrency() {
        return threads;
    }

    public void setConcurrency(int threads) {
        this.threads = threads;
    }

    public boolean isEncryptConfig() {
        return encrypt;
    }

    public void setEncryptConfig(boolean encrypt) {
        this.encrypt = encrypt;
    }

    public boolean isTestConnection() {
        return test;
    }

    public void setTestConnection(boolean test) {
        this.test = test;
    }

    public boolean isGenerateTemplate() {
        return template;
    }

    public void setGenerateTemplate(boolean template) {
        this.template = template;
    }

    public boolean isShowHelp() {
        return help;
    }

    public void setShowHelp(boolean help) {
        this.help = help;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public boolean isInteractiveTemplate() {
        return interactiveTemplate;
    }

    public void setInteractiveTemplate(boolean interactiveTemplate) {
        this.interactiveTemplate = interactiveTemplate;
    }

    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        // 允许外部显式设置，非空时 LogConfigManager 会追加自定义文件 appender
        this.logFile = (logFile == null || logFile.trim().isEmpty()) ? null : logFile.trim();
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isWebManagement() {
        return webManagement;
    }

    public void setWebManagement(boolean webManagement) {
        this.webManagement = webManagement;
    }
    
    public int getWebPort() {
        return webPort;
    }

    public void setWebPort(int webPort) {
        this.webPort = webPort;
    }
    
    public List<DatabaseConfig> getDatabases() {
        return databases;
    }
    
    public void setDatabases(List<DatabaseConfig> databases) {
        this.databases = databases;
    }
}