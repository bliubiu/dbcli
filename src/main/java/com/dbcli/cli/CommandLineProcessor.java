package com.dbcli.cli;

import com.dbcli.config.AppConfig;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 命令行参数处理器
 */
public class CommandLineProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CommandLineProcessor.class);

    public AppConfig parseArgs(String[] args) {
        Options options = createOptions();
        
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            
            // 显示帮助信息
            if (cmd.hasOption("h") || cmd.hasOption("help")) {
                printHelp(options);
                return null;
            }
            
            // 显示版本信息
            if (cmd.hasOption("version")) {
                showVersion();
                return null;
            }
            
            AppConfig config = new AppConfig();
            
            // 解析各个参数
            if (cmd.hasOption("template")) {
                config.setTemplate(true);
            }
            
            if (cmd.hasOption("e") || cmd.hasOption("encrypt")) {
                config.setEncrypt(true);
            }
            
            if (cmd.hasOption("t") || cmd.hasOption("test")) {
                config.setTest(true);
                // 当使用test参数时，默认清理灰名单文件
                config.setClean(true);
            }
            
            if (cmd.hasOption("dry-run")) {
                config.setDryRun(true);
            }
            
            if (cmd.hasOption("web") || cmd.hasOption("web-management")) {
                config.setWebManagement(true);
            }
            
            if (cmd.hasOption("web-port")) {
                try {
                    int port = Integer.parseInt(cmd.getOptionValue("web-port", "8080"));
                    if (port <= 0 || port > 65535) {
                        System.err.println("错误: 端口号必须在1-65535之间");
                        return null;
                    }
                    config.setWebPort(port);
                } catch (NumberFormatException e) {
                    System.err.println("错误: 端口号必须是有效的数字");
                    return null;
                }
            }
            
            // 设置配置路径（如果没有指定参数，使用默认值）
            config.setConfigPath(cmd.getOptionValue("config", "configs/"));
            
            // 设置指标路径（如果没有指定参数，使用默认值）
            config.setMetricsPath(cmd.getOptionValue("metrics", "metrics/"));
            
            // 设置输出路径（如果没有指定参数，使用默认值）
            config.setOutputPath(cmd.getOptionValue("output", "reports/"));
            
            if (cmd.hasOption("f") || cmd.hasOption("format")) {
                String format = cmd.getOptionValue("format", "excel");
                String f = format != null ? format.toLowerCase() : "excel";
                if (!"excel".equals(f) && !"html".equals(f) && !"both".equals(f)) {
                    System.err.println("错误: 输出格式只支持 excel、html 或 both");
                    return null;
                }
                config.setFormat(f);
            }
            
            if (cmd.hasOption("p") || cmd.hasOption("threads")) {
                try {
                    int threads = Integer.parseInt(cmd.getOptionValue("threads", "7"));
                    if (threads <= 0) {
                        System.err.println("错误: 线程数必须大于0");
                        return null;
                    }
                    config.setThreads(threads);
                } catch (NumberFormatException e) {
                    System.err.println("错误: 线程数必须是有效的数字");
                    return null;
                }
            }
            
            // 验证参数组合的有效性
            if (config.isTemplate() && (config.isTest() || config.isEncrypt())) {
                System.err.println("错误: --template 不能与 --test 或 --encrypt 同时使用");
                return null;
            }
            
            return config;
            
        } catch (ParseException e) {
            System.err.println("参数解析错误: " + e.getMessage());
            System.err.println("使用 --help 或 -h 查看帮助信息");
            // 不打印完整帮助信息，避免在测试中输出过多内容
            return null;
        }
    }
    
    private Options createOptions() {
        Options options = new Options();
        
        options.addOption("h", "help", false, "显示帮助信息");
        options.addOption(null, "version", false, "显示版本信息");
        options.addOption(null, "template", false, "生成配置文件模板");
        options.addOption("e", "encrypt", false, "加密配置文件中的敏感信息");
        options.addOption("t", "test", false, "测试数据库连接（自动清理历史失败清单与黑名单）");
        options.addOption(null, "dry-run", false, "仅验证配置，不执行实际操作");
        options.addOption("w", "web", false, "启动Web管理界面");
        options.addOption(null, "web-management", false, "启动Web管理界面（完整参数名）");
        
        options.addOption(Option.builder()
                .longOpt("web-port")
                .hasArg()
                .argName("端口")
                .desc("指定Web管理界面端口（默认：8080）")
                .build());
        
        options.addOption(Option.builder("c")
                .longOpt("config")
                .hasArg()
                .argName("路径")
                .desc("指定配置文件路径（默认：configs/）")
                .build());
                
        options.addOption(Option.builder("m")
                .longOpt("metrics")
                .hasArg()
                .argName("路径")
                .desc("指定指标文件路径（默认：metrics/）")
                .build());
                
        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("路径")
                .desc("指定输出文件路径（默认：reports/）")
                .build());
                
        options.addOption(Option.builder("f")
                .longOpt("format")
                .hasArg()
                .argName("格式")
                .desc("输出格式：excel|html|both（默认：excel）")
                .build());
                
        options.addOption(Option.builder("p")
                .longOpt("threads")
                .hasArg()
                .argName("数量")
                .desc("并发线程数（默认：7）")
                .build());
        
        return options;
    }
    
    private void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("dbcli", "多数据库指标收集工具", options, 
            "\n使用方法:\n" +
            "  dbcli [选项] [参数]\n\n" +
            "示例:\n" +
            "  dbcli --template                    # 生成配置模板\n" +
            "  dbcli --encrypt                     # 加密配置文件\n" +
            "  dbcli --test                        # 测试数据库连接（自动清理灰名单）\n" +
            "  dbcli -f html -p 10                 # 生成HTML报告，使用10个线程\n" +
            "  dbcli -c /path/configs -o /path/out # 指定配置和输出路径\n");
    }
    
    private void showVersion() {
        logger.info("dbcli - 数据库指标收集工具");
        logger.info("版本: 1.0.0");
        logger.info("支持数据库: Oracle, MySQL, PostgreSQL, 达梦");
        logger.info("功能特性: 并行查询, Excel/HTML报告, SM4加密, 数据脱敏");
    }
}