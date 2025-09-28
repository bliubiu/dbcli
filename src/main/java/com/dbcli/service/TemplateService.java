package com.dbcli.service;

import com.dbcli.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * 增强版模板生成服务
 * 支持交互式模板生成向导和完整的配置模板
 */
public class TemplateService {
    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 生成所有数据库类型的配置和指标模板
     */
    public void generateTemplates(String configPath, String metricsPath) throws IOException {
        FileUtil.createDirectoryIfNotExists(configPath);
        FileUtil.createDirectoryIfNotExists(metricsPath);
        FileUtil.createDirectoryIfNotExists("reports");
        
        logger.info("开始生成配置模板...");
        
        // 生成数据库配置模板
        generateOracleConfigTemplate(configPath);
        generateMysqlConfigTemplate(configPath);
        generatePostgresConfigTemplate(configPath);
        generateDmConfigTemplate(configPath);
        
        // 生成指标配置模板
        generateOracleMetricsTemplate(metricsPath);
        generateMysqlMetricsTemplate(metricsPath);
        generatePostgresMetricsTemplate(metricsPath);
        generateDmMetricsTemplate(metricsPath);
        
        // 生成README文档
        generateReadmeTemplate(configPath);
        generateMetricsReadmeTemplate(metricsPath);
        
        logger.info("配置模板生成完成！");
        logger.info("配置文件目录: {}", configPath);
        logger.info("指标文件目录: {}", metricsPath);
    }
    
    /**
     * 交互式模板生成向导
     */
    public void generateInteractiveTemplate(String configPath, String metricsPath) throws IOException {
        Scanner scanner = new Scanner(System.in);
        
        logger.info("=== DBCli 配置模板生成向导 ===");
        logger.info("");

        // 选择数据库类型
        logger.info("请选择要生成的数据库类型模板:");
        logger.info("1. Oracle");
        logger.info("2. MySQL");
        logger.info("3. PostgreSQL");
        logger.info("4. 达梦(DM)");
        logger.info("5. 全部");
        System.out.print("请输入选择 (1-5): ");
        
        int choice = scanner.nextInt();
        scanner.nextLine(); // 消费换行符
        
        FileUtil.createDirectoryIfNotExists(configPath);
        FileUtil.createDirectoryIfNotExists(metricsPath);
        
        switch (choice) {
            case 1:
                generateInteractiveOracleTemplate(scanner, configPath, metricsPath);
                break;
            case 2:
                generateInteractiveMysqlTemplate(scanner, configPath, metricsPath);
                break;
            case 3:
                generateInteractivePostgresTemplate(scanner, configPath, metricsPath);
                break;
            case 4:
                generateInteractiveDmTemplate(scanner, configPath, metricsPath);
                break;
            case 5:
                generateTemplates(configPath, metricsPath);
                break;
            default:
                logger.warn("无效选择，生成所有模板...");
                generateTemplates(configPath, metricsPath);
        }
        
        logger.info("");
        logger.info("模板生成完成！");
        logger.info("请根据实际环境修改配置文件中的连接信息。");
    }
    
    /**
     * 交互式生成Oracle模板
     */
    private void generateInteractiveOracleTemplate(Scanner scanner, String configPath, String metricsPath) throws IOException {
        System.out.println();
        System.out.println("=== Oracle 数据库配置 ===");
        
        System.out.print("请输入系统名称 (默认: 财务测试数据库): ");
        String systemName = scanner.nextLine();
        if (systemName.trim().isEmpty()) {
            systemName = "财务测试数据库";
        }
        
        System.out.print("请输入数据库端口 (默认: 1521): ");
        String port = scanner.nextLine();
        if (port.trim().isEmpty()) {
            port = "1521";
        }
        
        System.out.print("请输入数据库用户名: ");
        String username = scanner.nextLine();
        
        System.out.print("请输入数据库密码: ");
        String password = scanner.nextLine();
        
        System.out.print("请输入主机IP地址: ");
        String host = scanner.nextLine();
        
        System.out.print("请输入实例名称 (默认: orcl): ");
        String instanceName = scanner.nextLine();
        if (instanceName.trim().isEmpty()) {
            instanceName = "orcl";
        }
        
        generateCustomOracleTemplate(configPath, metricsPath, systemName, port, username, password, host, instanceName);
    }
    
    /**
     * 交互式生成MySQL模板
     */
    private void generateInteractiveMysqlTemplate(Scanner scanner, String configPath, String metricsPath) throws IOException {
        System.out.println();
        System.out.println("=== MySQL 数据库配置 ===");
        
        System.out.print("请输入系统名称 (默认: 业务数据库): ");
        String systemName = scanner.nextLine();
        if (systemName.trim().isEmpty()) {
            systemName = "业务数据库";
        }
        
        System.out.print("请输入数据库端口 (默认: 3306): ");
        String port = scanner.nextLine();
        if (port.trim().isEmpty()) {
            port = "3306";
        }
        
        System.out.print("请输入数据库用户名: ");
        String username = scanner.nextLine();
        
        System.out.print("请输入数据库密码: ");
        String password = scanner.nextLine();
        
        System.out.print("请输入主机IP地址: ");
        String host = scanner.nextLine();
        
        System.out.print("请输入数据库名称 (默认: mysql): ");
        String dbName = scanner.nextLine();
        if (dbName.trim().isEmpty()) {
            dbName = "mysql";
        }
        
        generateCustomMysqlTemplate(configPath, metricsPath, systemName, port, username, password, host, dbName);
    }
    
    /**
     * 交互式生成PostgreSQL模板
     */
    private void generateInteractivePostgresTemplate(Scanner scanner, String configPath, String metricsPath) throws IOException {
        System.out.println();
        System.out.println("=== PostgreSQL 数据库配置 ===");
        
        System.out.print("请输入系统名称 (默认: 数据仓库): ");
        String systemName = scanner.nextLine();
        if (systemName.trim().isEmpty()) {
            systemName = "数据仓库";
        }
        
        System.out.print("请输入数据库端口 (默认: 5432): ");
        String port = scanner.nextLine();
        if (port.trim().isEmpty()) {
            port = "5432";
        }
        
        System.out.print("请输入数据库用户名: ");
        String username = scanner.nextLine();
        
        System.out.print("请输入数据库密码: ");
        String password = scanner.nextLine();
        
        System.out.print("请输入主机IP地址: ");
        String host = scanner.nextLine();
        
        System.out.print("请输入数据库名称 (默认: postgres): ");
        String dbName = scanner.nextLine();
        if (dbName.trim().isEmpty()) {
            dbName = "postgres";
        }
        
        generateCustomPostgresTemplate(configPath, metricsPath, systemName, port, username, password, host, dbName);
    }
    
    /**
     * 交互式生成达梦模板
     */
    private void generateInteractiveDmTemplate(Scanner scanner, String configPath, String metricsPath) throws IOException {
        System.out.println();
        System.out.println("=== 达梦数据库配置 ===");
        
        System.out.print("请输入系统名称 (默认: 核心系统): ");
        String systemName = scanner.nextLine();
        if (systemName.trim().isEmpty()) {
            systemName = "核心系统";
        }
        
        System.out.print("请输入数据库端口 (默认: 5236): ");
        String port = scanner.nextLine();
        if (port.trim().isEmpty()) {
            port = "5236";
        }
        
        System.out.print("请输入数据库用户名: ");
        String username = scanner.nextLine();
        
        System.out.print("请输入数据库密码: ");
        String password = scanner.nextLine();
        
        System.out.print("请输入主机IP地址: ");
        String host = scanner.nextLine();
        
        System.out.print("请输入实例名称 (默认: dmserver): ");
        String instanceName = scanner.nextLine();
        if (instanceName.trim().isEmpty()) {
            instanceName = "dmserver";
        }
        
        generateCustomDmTemplate(configPath, metricsPath, systemName, port, username, password, host, instanceName);
    }
    
    private void generateOracleConfigTemplate(String configPath) throws IOException {
        String template = generateOracleConfigContent("财务测试数据库", "1521", "your_username", "your_password", 
                                                     "192.168.10.100", "orcl1");
        
        Files.write(Paths.get(configPath, "oracle-config.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成Oracle配置模板: {}/oracle-config.template.yml", configPath);
    }
    
    private void generateCustomOracleTemplate(String configPath, String metricsPath, String systemName, 
                                            String port, String username, String password, String host, String instanceName) throws IOException {
        String configTemplate = generateOracleConfigContent(systemName, port, username, password, host, instanceName);
        Files.write(Paths.get(configPath, "oracle-config.template.yml"), configTemplate.getBytes("UTF-8"));
        
        generateOracleMetricsTemplate(metricsPath);
        
        logger.info("生成自定义Oracle配置模板: {}/oracle-config.template.yml", configPath);
    }
    
    private String generateOracleConfigContent(String systemName, String port, String username, String password, String host, String instanceName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        return String.format("# Oracle数据库配置文件\n" +
                "# 生成时间: %s\n" +
                "# 使用说明:\n" +
                "# 1. 修改实际的连接信息\n" +
                "# 2. 使用 --encrypt 参数加密敏感信息\n" +
                "# 3. enable: false 可以临时禁用某个系统的数据收集\n" +
                "\n" +
                "%s:\n" +
                "  enable: true                    # true为开启数据收集，false即忽略执行SQL收集数据\n" +
                "  port: %s                        # 默认端口\n" +
                "  username: %s                    # 默认数据库用户名\n" +
                "  password: %s                    # 默认数据库密码\n" +
                "  nodes:\n" +
                "    - host: %s                    # 数据库主机地址\n" +
                "      svc_name: %s                # 服务名称\n" +
                "      role: master               # 节点角色: master/standby\n" +
                "    # - host: 192.168.10.101      # RAC环境可添加更多节点\n" +
                "    #   sid_name: orcl2\n" +
                "    #   role: master\n" +
                "    # - host: 192.168.10.102      # DG备库示例\n" +
                "    #   svc_name: orcldg\n" +
                "    #   port: 1523                # 可覆盖默认端口\n" +
                "    #   role: standby\n" +
                "\n" +
                "# 可以添加更多系统配置\n" +
                "# 资产数据库:\n" +
                "#   enable: true\n" +
                "#   port: 1522\n" +
                "#   username: ENC(encrypted_username)  # 加密后的用户名\n" +
                "#   password: ENC(encrypted_password)  # 加密后的密码\n" +
                "#   nodes:\n" +
                "#     - host: 192.168.11.100\n" +
                "#       svc_name: asset_db\n" +
                "#       role: master\n",
                timestamp, systemName, port, username, password, host, instanceName);
    }
    
    private void generateMysqlConfigTemplate(String configPath) throws IOException {
        String template = generateMysqlConfigContent("业务数据库", "3306", "your_username", "your_password", 
                                                   "192.168.10.200", "mysql");
        
        Files.write(Paths.get(configPath, "mysql-config.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成MySQL配置模板: {}/mysql-config.template.yml", configPath);
    }
    
    private void generateCustomMysqlTemplate(String configPath, String metricsPath, String systemName, 
                                           String port, String username, String password, String host, String dbName) throws IOException {
        String configTemplate = generateMysqlConfigContent(systemName, port, username, password, host, dbName);
        Files.write(Paths.get(configPath, "mysql-config.template.yml"), configTemplate.getBytes("UTF-8"));
        
        generateMysqlMetricsTemplate(metricsPath);
        
        logger.info("生成自定义MySQL配置模板: {}/mysql-config.template.yml", configPath);
    }
    
    private String generateMysqlConfigContent(String systemName, String port, String username, String password, String host, String dbName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        return String.format("# MySQL数据库配置文件\n" +
                "# 生成时间: %s\n" +
                "# 使用说明:\n" +
                "# 1. 修改实际的连接信息\n" +
                "# 2. 使用 --encrypt 参数加密敏感信息\n" +
                "# 3. enable: false 可以临时禁用某个系统的数据收集\n" +
                "\n" +
                "%s:\n" +
                "  enable: true                    # true为开启数据收集，false即忽略执行SQL收集数据\n" +
                "  port: %s                        # 默认端口\n" +
                "  username: %s                    # 默认数据库用户名\n" +
                "  password: %s                    # 默认数据库密码\n" +
                "  nodes:\n" +
                "    - host: %s                    # 数据库主机地址\n" +
                "      svc_name: %s                # 数据库名称\n" +
                "      role: master                # 节点角色: master/standby\n" +
                "    # - host: 192.168.10.201      # 从库示例\n" +
                "    #   svc_name: %s\n" +
                "    #   role: standby\n" +
                "\n" +
                "# 可以添加更多系统配置\n" +
                "# 其他业务系统:\n" +
                "#   enable: true\n" +
                "#   port: 3307\n" +
                "#   username: ENC(encrypted_username)  # 加密后的用户名\n" +
                "#   password: ENC(encrypted_password)  # 加密后的密码\n" +
                "#   nodes:\n" +
                "#     - host: 192.168.11.200\n" +
                "#       svc_name: other_db\n" +
                "#       role: master\n", timestamp, systemName, port, username, password, host, dbName, dbName);
    }
    
    private void generatePostgresConfigTemplate(String configPath) throws IOException {
        String template = generatePostgresConfigContent("数据仓库", "5432", "your_username", "your_password", 
                                                       "192.168.10.300", "postgres");
        
        Files.write(Paths.get(configPath, "pg-config.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成PostgreSQL配置模板: {}/pg-config.template.yml", configPath);
    }
    
    private void generateCustomPostgresTemplate(String configPath, String metricsPath, String systemName, 
                                              String port, String username, String password, String host, String dbName) throws IOException {
        String configTemplate = generatePostgresConfigContent(systemName, port, username, password, host, dbName);
        Files.write(Paths.get(configPath, "pg-config.template.yml"), configTemplate.getBytes("UTF-8"));
        
        generatePostgresMetricsTemplate(metricsPath);
        
        logger.info("生成自定义PostgreSQL配置模板: {}/pg-config.template.yml", configPath);
    }
    
    private String generatePostgresConfigContent(String systemName, String port, String username, String password, String host, String dbName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        return String.format("# PostgreSQL数据库配置文件\n" +
                "# 生成时间: %s\n" +
                "# 使用说明:\n" +
                "# 1. 修改实际的连接信息\n" +
                "# 2. 使用 --encrypt 参数加密敏感信息\n" +
                "# 3. enable: false 可以临时禁用某个系统的数据收集\n" +
                "\n" +
                "%s:\n" +
                "  enable: true                    # true为开启数据收集，false即忽略执行SQL收集数据\n" +
                "  port: %s                        # 默认端口\n" +
                "  username: %s                    # 默认数据库用户名\n" +
                "  password: %s                    # 默认数据库密码\n" +
                "  nodes:\n" +
                "    - host: %s                    # 数据库主机地址\n" +
                "      svc_name: %s                # 数据库名称\n" +
                "      role: master               # 节点角色: master/standby\n" +
                "    # - host: 192.168.10.301      # 备库示例\n" +
                "    #   svc_name: %s\n" +
                "    #   role: standby\n" +
                "\n" +
                "# 可以添加更多系统配置\n" +
                "# 分析系统:\n" +
                "#   enable: true\n" +
                "#   port: 5433\n" +
                "#   username: ENC(encrypted_username)  # 加密后的用户名\n" +
                "#   password: ENC(encrypted_password)  # 加密后的密码\n" +
                "#   nodes:\n" +
                "#     - host: 192.168.11.300\n" +
                "#       svc_name: analytics_db\n" +
                "#       role: master\n", timestamp, systemName, port, username, password, host, dbName, dbName);
    }
    
    private void generateDmConfigTemplate(String configPath) throws IOException {
        String template = generateDmConfigContent("核心系统", "5236", "your_username", "your_password", 
                                                 "192.168.10.400", "dmserver");
        
        Files.write(Paths.get(configPath, "dm-config.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成达梦配置模板: {}/dm-config.template.yml", configPath);
    }
    
    private void generateCustomDmTemplate(String configPath, String metricsPath, String systemName, 
                                        String port, String username, String password, String host, String instanceName) throws IOException {
        String configTemplate = generateDmConfigContent(systemName, port, username, password, host, instanceName);
        Files.write(Paths.get(configPath, "dm-config.template.yml"), configTemplate.getBytes("UTF-8"));
        
        generateDmMetricsTemplate(metricsPath);
        
        logger.info("生成自定义达梦配置模板: {}/dm-config.template.yml", configPath);
    }
    
    private String generateDmConfigContent(String systemName, String port, String username, String password, String host, String instanceName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        return String.format("# 达梦数据库配置文件\n" +
                "# 生成时间: %s\n" +
                "# 使用说明:\n" +
                "# 1. 修改实际的连接信息\n" +
                "# 2. 使用 --encrypt 参数加密敏感信息\n" +
                "# 3. enable: false 可以临时禁用某个系统的数据收集\n" +
                "\n" +
                "%s:\n" +
                "  enable: true                    # true为开启数据收集，false即忽略执行SQL收集数据\n" +
                "  port: %s                        # 默认端口\n" +
                "  username: %s                    # 默认数据库用户名\n" +
                "  password: %s                    # 默认数据库密码\n" +
                "  nodes:\n" +
                "    - host: %s                    # 数据库主机地址\n" +
                "      svc_name: %s                # 实例名称\n" +
                "      role: master                # 节点角色: master/standby\n" +
                "    # - host: 192.168.10.401      # 备库示例\n" +
                "    #   svc_name: %s_standby\n" +
                "    #   role: standby\n" +
                "\n" +
                "# 可以添加更多系统配置\n" +
                "# 备份系统:\n" +
                "#   enable: true\n" +
                "#   port: 5237\n" +
                "#   username: ENC(encrypted_username)  # 加密后的用户名\n" +
                "#   password: ENC(encrypted_password)  # 加密后的密码\n" +
                "#   nodes:\n" +
                "#     - host: 192.168.11.400\n" +
                "#       svc_name: backup_dm\n" +
                "#       role: master\n", timestamp, systemName, port, username, password, host, instanceName, instanceName);
    }
    
    private void generateOracleMetricsTemplate(String metricsPath) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        String template = String.format("# Oracle数据库指标配置文件\n" +
                "# 生成时间: " + timestamp + "\n" +
                "# 使用说明:\n" +
                "# 1. type: SINGLE - 单值指标，返回一个值; MULTI - 多值指标，返回多行数据\n" +
                "# 2. execution_strategy.mode: all - 所有节点执行; first - 仅第一个节点; role - 按指定角色master/standby执行\n" +
                "# 3. threshold.level: high - 红色高亮; medium - 黄色高亮; low - 正常显示\n" +
                "\n" +
                "# 基础性能指标\n" +
                "- type: SINGLE\n" +
                "  name: active_sessions\n" +
                "  description: 活跃会话数\n" +
                "  sql: SELECT COUNT(*) AS sess_count FROM V$SESSION WHERE STATUS='ACTIVE'\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n" +
                "  threshold:\n" +
                "    level: high\n" +
                "    operator: '>'\n" +
                "    value: 100\n" +
                "\n" +
                "# 表空间使用情况\n" +
                "- type: MULTI\n" +
                "  name: tablespace_usage\n" +
                "  description: 表空间使用率\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      tablespace_name,\n" +
                "      ROUND(used_space/1024/1024, 2) AS used_mb,\n" +
                "      ROUND(tablespace_size/1024/1024, 2) AS total_mb,\n" +
                "      ROUND((used_space/tablespace_size)*100, 2) AS usage_percent\n" +
                "    FROM dba_tablespace_usage_metrics\n" +
                "    WHERE tablespace_name NOT LIKE '%%TEMP%%'\n" +
                "    ORDER BY usage_percent DESC\n" +
                "  columns: [\"tablespace_name\", \"used_mb\", \"total_mb\", \"usage_percent\"]\n" +
                "  threshold:\n" +
                "    level: high\n" +
                "    operator: '>'\n" +
                "    usage_percent: 80\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n", timestamp);
        
        Files.write(Paths.get(metricsPath, "oracle-metrics.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成Oracle指标模板: {}/oracle-metrics.template.yml", metricsPath);
    }
    
    private void generateMysqlMetricsTemplate(String metricsPath) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        String template = String.format("# MySQL数据库指标配置文件\n" +
                "# 生成时间: %s\n" +
                "# 使用说明:\n" +
                "# 1. 根据MySQL版本调整SQL语句（支持5.7+和8.0+）\n" +
                "# 2. 可根据业务需求调整阈值\n" +
                "# 3. 主从环境建议在主库执行统计类指标\n" +
                "\n" +
                "# 连接和会话指标\n" +
                "- type: SINGLE\n" +
                "  name: current_connections\n" +
                "  description: 当前连接数\n" +
                "  sql: SELECT COUNT(*) AS conn_count FROM information_schema.processlist\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n" +
                "  threshold:\n" +
                "    level: high\n" +
                "    operator: '>'\n" +
                "    value: 200\n" +
                "\n" +
                "- type: SINGLE\n" +
                "  name: active_connections\n" +
                "  description: 活跃连接数\n" +
                "  sql: SELECT COUNT(*) AS active_conn FROM information_schema.processlist WHERE command != 'Sleep'\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n" +
                "  threshold:\n" +
                "    level: medium\n" +
                "    operator: '>'\n" +
                "    value: 50\n" +
                "\n" +
                "# 数据库大小统计\n" +
                "- type: MULTI\n" +
                "  name: database_size\n" +
                "  description: 数据库大小统计\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      schema_name AS database_name,\n" +
                "      ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS size_mb,\n" +
                "      COUNT(*) AS table_count\n" +
                "    FROM information_schema.tables \n" +
                "    WHERE schema_name NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')\n" +
                "    GROUP BY schema_name\n" +
                "    ORDER BY size_mb DESC\n" +
                "  columns: [\"database_name\", \"size_mb\", \"table_count\"]\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n" +
                "\n" +
                "# 表大小统计（前10大表）\n" +
                "- type: MULTI\n" +
                "  name: largest_tables\n" +
                "  description: 最大的表\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      table_schema AS database_name,\n" +
                "      table_name,\n" +
                "      ROUND((data_length + index_length) / 1024 / 1024, 2) AS size_mb,\n" +
                "      table_rows\n" +
                "    FROM information_schema.tables \n" +
                "    WHERE table_schema NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')\n" +
                "    AND table_type = 'BASE TABLE'\n" +
                "    ORDER BY (data_length + index_length) DESC\n" +
                "    LIMIT 10\n" +
                "  columns: [\"database_name\", \"table_name\", \"size_mb\", \"table_rows\"]\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n" +
                "\n" +
                "# 主从复制状态（仅在从库执行）\n" +
                "- type: MULTI\n" +
                "  name: replication_status\n" +
                "  description: 主从复制状态\n" +
                "  sql: SHOW SLAVE STATUS\n" +
                "  execution_strategy:\n" +
                "    mode: standby\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n", timestamp);
        
        Files.write(Paths.get(metricsPath, "mysql-metrics.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成MySQL指标模板: {}/mysql-metrics.template.yml", metricsPath);
    }
    
    private void generatePostgresMetricsTemplate(String metricsPath) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        String template = String.format("# PostgreSQL数据库指标配置文件\n" +
                "# 生成时间: %s\n" +
                "# 使用说明:\n" +
                "# 1. 支持PostgreSQL 10+版本\n" +
                "# 2. 需要相应的监控权限\n" +
                "# 3. 主从环境建议在主库执行统计类指标\n" +
                "\n" +
                "# 连接和会话指标\n" +
                "- type: SINGLE\n" +
                "  name: total_connections\n" +
                "  description: 总连接数\n" +
                "  sql: SELECT count(*) AS conn_count FROM pg_stat_activity\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n" +
                "  threshold:\n" +
                "    level: high\n" +
                "    operator: '>'\n" +
                "    value: 100\n" +
                "\n" +
                "- type: SINGLE\n" +
                "  name: active_connections\n" +
                "  description: 活跃连接数\n" +
                "  sql: SELECT count(*) AS active_conn FROM pg_stat_activity WHERE state = 'active'\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n" +
                "  threshold:\n" +
                "    level: medium\n" +
                "    operator: '>'\n" +
                "    value: 50\n" +
                "\n" +
                "# 数据库大小统计\n" +
                "- type: MULTI\n" +
                "  name: database_size\n" +
                "  description: 数据库大小统计\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      datname AS database_name,\n" +
                "      pg_size_pretty(pg_database_size(datname)) AS size_pretty,\n" +
                "      ROUND(pg_database_size(datname)/1024/1024.0, 2) AS size_mb\n" +
                "    FROM pg_database \n" +
                "    WHERE datistemplate = false\n" +
                "    ORDER BY pg_database_size(datname) DESC\n" +
                "  columns: [\"database_name\", \"size_pretty\", \"size_mb\"]\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n" +
                "\n" +
                "# 表大小统计（前10大表）\n" +
                "- type: MULTI\n" +
                "  name: largest_tables\n" +
                "  description: 最大的表\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      schemaname,\n" +
                "      tablename,\n" +
                "      pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size_pretty,\n" +
                "      ROUND(pg_total_relation_size(schemaname||'.'||tablename)/1024/1024.0, 2) AS size_mb\n" +
                "    FROM pg_tables \n" +
                "    WHERE schemaname NOT IN ('information_schema', 'pg_catalog')\n" +
                "    ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC\n" +
                "    LIMIT 10\n" +
                "  columns: [\"schemaname\", \"tablename\", \"size_pretty\", \"size_mb\"]\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n" +
                "\n" +
                "# 复制状态（仅在从库执行）\n" +
                "- type: MULTI\n" +
                "  name: replication_status\n" +
                "  description: 流复制状态\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      client_addr,\n" +
                "      client_hostname,\n" +
                "      state,\n" +
                "      sent_lsn,\n" +
                "      write_lsn,\n" +
                "      flush_lsn,\n" +
                "      replay_lsn,\n" +
                "      sync_state\n" +
                "    FROM pg_stat_replication\n" +
                "  columns: [\"client_addr\", \"client_hostname\", \"state\", \"sent_lsn\", \"write_lsn\", \"flush_lsn\", \"replay_lsn\", \"sync_state\"]\n" +
                "  execution_strategy:\n" +
                "    mode: standby  # 仅在standby角色节点执行\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n", timestamp);
        
        Files.write(Paths.get(metricsPath, "pg-metrics.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成PostgreSQL指标模板: {}/pg-metrics.template.yml", metricsPath);
    }
    
    private void generateDmMetricsTemplate(String metricsPath) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        String template = String.format("# 达梦数据库指标配置文件\n" +
                "# 生成时间: %s\n" +
                "# 使用说明:\n" +
                "# 1. 支持达梦数据库DM8版本\n" +
                "# 2. 需要相应的系统视图查询权限\n" +
                "# 3. 部分SQL可能需要根据实际版本调整\n" +
                "\n" +
                "# 会话和连接指标\n" +
                "- type: SINGLE\n" +
                "  name: session_count\n" +
                "  description: 会话总数\n" +
                "  sql: SELECT COUNT(*) AS sess_count FROM V$SESSIONS\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n" +
                "  threshold:\n" +
                "    level: high\n" +
                "    operator: '>'\n" +
                "    value: 50\n" +
                "\n" +
                "- type: SINGLE\n" +
                "  name: active_sessions\n" +
                "  description: 活跃会话数\n" +
                "  sql: SELECT COUNT(*) AS active_sess FROM V$SESSIONS WHERE STATUS = 'ACTIVE'\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n" +
                "  threshold:\n" +
                "    level: medium\n" +
                "    operator: '>'\n" +
                "    value: 20\n" +
                "\n" +
                "# 表空间使用情况\n" +
                "- type: MULTI\n" +
                "  name: tablespace_usage\n" +
                "  description: 表空间使用情况\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      tablespace_name,\n" +
                "      ROUND(total_space/1024/1024, 2) AS total_mb,\n" +
                "      ROUND(used_space/1024/1024, 2) AS used_mb,\n" +
                "      ROUND((used_space/total_space)*100, 2) AS usage_percent\n" +
                "    FROM DBA_TABLESPACE_USAGE_METRICS\n" +
                "    ORDER BY usage_percent DESC\n" +
                "  columns: [\"tablespace_name\", \"total_mb\", \"used_mb\", \"usage_percent\"]\n" +
                "  threshold:\n" +
                "    level: high\n" +
                "    operator: '>'\n" +
                "    usage_percent: 80\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 3\n" +
                "      backoff_ms: 1000\n" +
                "      delay_ms: 1000\n" +
                "\n" +
                "# 数据文件信息\n" +
                "- type: MULTI\n" +
                "  name: datafile_info\n" +
                "  description: 数据文件信息\n" +
                "  sql: |\n" +
                "    SELECT \n" +
                "      file_name,\n" +
                "      tablespace_name,\n" +
                "      ROUND(bytes/1024/1024, 2) AS size_mb,\n" +
                "      status\n" +
                "    FROM DBA_DATA_FILES\n" +
                "    ORDER BY bytes DESC\n" +
                "  columns: [\"file_name\", \"tablespace_name\", \"size_mb\", \"status\"]\n" +
                "  execution_strategy:\n" +
                "    mode: first\n" +
                "    retry_policy:\n" +
                "      enabled: true\n" +
                "      max_attempts: 2\n" +
                "      backoff_ms: 500\n" +
                "      delay_ms: 500\n", timestamp);
        
        Files.write(Paths.get(metricsPath, "dm-metrics.template.yml"), template.getBytes("UTF-8"));
        logger.info("生成达梦指标模板: {}/dm-metrics.template.yml", metricsPath);
    }

    /**
     * 生成README模板
     */
    public void generateReadmeTemplate(String outputPath) throws IOException {
        String readmeContent = "# 数据库配置说明\n\n" +
                "本目录包含数据库连接配置文件。\n\n" +
                "## 配置文件格式\n\n" +
                "- Oracle: oracle-config.template.yml\n" +
                "- MySQL: mysql-config.template.yml\n" +
                "- PostgreSQL: pg-config.template.yml\n" +
                "- 达梦: dm-config.template.yml\n\n" +
                "## 注意事项\n\n" +
                "1. 请根据实际环境修改配置参数\n" +
                "2. 敏感信息建议使用加密功能\n" +
                "3. 配置文件格式必须为有效的YAML格式\n";

        FileUtil.writeToFile(outputPath + "/README.md", readmeContent);
        logger.info("README模板生成完成: {}/README.md", outputPath);
    }

    /**
     * 生成指标README模板
     */
    public void generateMetricsReadmeTemplate(String outputPath) throws IOException {
        String readmeContent = "# 数据库指标配置说明\n\n" +
                "本目录包含数据库指标查询配置文件。\n\n" +
                "## 指标文件格式\n\n" +
                "- Oracle: oracle-metrics.template.yml\n" +
                "- MySQL: mysql-metrics.template.yml\n" +
                "- PostgreSQL: pg-metrics.template.yml\n" +
                "- 达梦: dm-metrics.template.yml\n\n" +
                "## 指标类型\n\n" +
                "- SINGLE: 单值指标\n" +
                "- MULTI: 多值指标\n\n" +
                "## 注意事项\n\n" +
                "1. SQL语句必须符合对应数据库语法\n" +
                "2. 指标名称不能重复\n" +
                "3. 建议添加详细的指标描述\n";

        FileUtil.writeToFile(outputPath + "/README.md", readmeContent);
        logger.info("指标README模板生成完成: {}/README.md", outputPath);
    }

    /**
     * 生成交互式模板
     */
    public void generateInteractiveTemplates(String configPath, String metricsPath) throws IOException {
        logger.info("开始生成交互式模板...");
        
        // 生成配置文件模板
        generateTemplates(configPath, metricsPath);
        
        logger.info("交互式模板生成完成");
    }
}
