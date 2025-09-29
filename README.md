# dbcli - 多数据库指标收集与报告工具

## 📋 项目概述

**dbcli** 是一个用于多数据库指标收集与报告生成的命令行工具，旨在统一管理 Oracle、MySQL、PostgreSQL 和达梦等主流数据库的性能指标采集任务。

### ✨ 核心特性

- 🎯 **多数据库支持**：Oracle、MySQL、PostgreSQL、达梦
- ⚡ **并发执行**：SQL 指标收集，按数据库类型分组，支持多线程并行处理
- 📊 **多格式报告**：自动生成 Excel/HTML 报告，支持数据可视化
- 🔐 **安全加密**：配置文件 SM4 加密（ENC(...) 包裹），敏感信息保护
- 🎭 **数据脱敏**：输出报告默认脱敏，保障信息安全
- 🔍 **连接测试**：快速预检查/测试，支持黑名单管理
- 📝 **细粒度日志**：主/错误/执行/连接/性能日志分流，便于审计与定位
- 🌐 **Web管理界面**：内置Web管理功能，支持在线配置和监控
- 🔧 **模板生成**：规范的配置/指标格式，快速上手

### 🏗️ 技术架构

- **核心入口**：`com.dbcli.DbCliApplication`
- **Java要求**：17+
- **当前版本**：1.0.0
- **构建工具**：Maven 3.6+
- **运行环境**：支持Windows、Linux、macOS及Docker容器化部署

## 📁 目录结构

```
dbcli/
├── src/main/java/com/dbcli/     # 核心代码
│   ├── cli/                     # 命令行参数处理
│   ├── config/                  # 配置模型与加载器
│   ├── core/                    # 核心执行流程
│   ├── database/                # 数据库连接工厂、驱动加载
│   ├── executor/                # 并发执行器、重试策略
│   ├── model/                   # 数据模型
│   ├── service/                 # 业务服务（加密、报告生成等）
│   ├── util/                    # 工具类（加密、日志、脱敏等）
│   └── web/                     # Web管理界面
├── configs/                     # 数据库连接配置（*.yml）
├── metrics/                     # 指标定义文件（*.yml）
├── reports/                     # 报告输出目录（自动创建）
├── logs/                        # 日志目录
│   ├── archive/                 # 历史日志归档
│   ├── error/                   # 错误日志
│   ├── execution/               # 执行日志
│   ├── connection/              # 连接日志
│   └── performance/             # 性能日志
├── lib/                         # 第三方数据库驱动
├── docs/                        # 文档目录
├── target/                      # Maven构建输出
├── dbcli.sh                     # Linux/macOS启动脚本
├── dbcli.bat                    # Windows启动脚本
└── pom.xml                      # Maven项目配置
```

## 🚀 快速开始

### 1️⃣ 环境准备

**基础要求：**
- Java 17+ （推荐使用 OpenJDK 17）
- Maven 3.6+

**数据库驱动：**
- MySQL、PostgreSQL：自动包含
- Oracle、达梦：需手动放入 `lib/` 目录
  - Oracle：`ojdbc8.jar`
  - 达梦：`DmJdbcDriver18.jar`

### 2️⃣ 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd dbcli

# 编译打包
mvn clean package -DskipTests

# 生成文件：target/dbcli-1.0.0.jar
```

### 3️⃣ 快速启动

**使用启动脚本（推荐）：**

```bash
# Linux/macOS
./dbcli.sh --help
./dbcli.sh --template    # 生成配置模板
./dbcli.sh --test        # 测试数据库连接
./dbcli.sh               # 执行指标收集

# Windows
dbcli.bat --help
dbcli.bat --template
dbcli.bat --test
dbcli.bat
```

**直接使用Java：**

```bash
# 生成配置模板
java -jar target/dbcli-1.0.0.jar --template

# 测试数据库连接
java -jar target/dbcli-1.0.0.jar --test

# 执行指标收集
java -jar target/dbcli-1.0.0.jar -c configs -m metrics -o reports -f both
```

### 4️⃣ Web管理界面

```bash
# 启动Web管理界面
java -jar target/dbcli-1.0.0.jar --web-management

# 访问地址：http://localhost:8080
# 功能：配置管理、连接测试、报告生成、实时监控
```

### 5️⃣ 简化执行流程

为了简化日常使用，项目提供了简化执行脚本，可以一键完成编译、打包和运行：

**Windows环境：**
```cmd
# 编译并运行命令行模式
run.bat

# 启动Web管理界面
run.bat -w

# 显示帮助信息
run.bat --help
```

**Linux/macOS环境：**
```bash
# 编译并运行命令行模式
./run.sh

# 启动Web管理界面
./run.sh -w

# 显示帮助信息
./run.sh --help
```

这些简化脚本会自动执行以下操作：
1. 使用Maven编译项目：`mvn -q -DskipTests clean compile`
2. 打包项目：`mvn package -DskipTests -q`
3. 运行dbcli应用

### 6️⃣ 指标数据持久化存储

从1.5版本开始，dbcli支持将收集到的指标数据持久化存储到PostgreSQL数据库中，以便进行历史数据分析。

**功能特性：**
- 自动将指标数据存储到PostgreSQL数据库
- 支持批量写入以提高性能
- 自动创建表结构和索引
- 可配置的存储参数

**使用方法：**

1. 准备PostgreSQL数据库：
```sql
-- 创建数据库
CREATE DATABASE dbcli_metrics;

-- 创建用户
CREATE USER dbcli_user WITH PASSWORD 'dbcli_password';

-- 授权
GRANT ALL PRIVILEGES ON DATABASE dbcli_metrics TO dbcli_user;
```

2. 运行dbcli，指标数据将自动存储到数据库中：
```bash
./dbcli.sh -c configs -m metrics -o reports
```

3. 查询历史数据：
```sql
SELECT * FROM metric_results 
WHERE system_name = 'your_system_name' 
  AND collect_time >= '2025-01-01'
ORDER BY collect_time DESC;
```

更多详细信息请查看 [指标存储功能文档](docs/MetricsStorage.md)。

## 💻 启动脚本

项目提供了统一的启动脚本，支持跨平台使用：

### Windows 脚本（`dbcli.bat`）

- 自动检测Java环境
- 支持自动编译（如未编译）
- 优先使用打包后的JAR文件
- 支持类路径模式（开发阶段）

```cmd
REM 显示帮助
dbcli.bat --help

REM 生成配置模板
dbcli.bat --template

REM 测试数据库连接
dbcli.bat --test

REM 执行指标收集
dbcli.bat -c configs -m metrics -o reports -f excel -p 10

REM 启动Web管理界面
dbcli.bat --web-management
```

### Linux/macOS 脚本（`dbcli.sh`）

- 彩色输出和进度提示
- 智能Java环境检测
- 详细的错误诊断信息
- 性能优化的JVM参数

```bash
# 显示帮助
./dbcli.sh --help

# 生成配置模板
./dbcli.sh --template

# 测试数据库连接
./dbcli.sh --test

# 执行指标收集
./dbcli.sh -c configs -m metrics -o reports -f html -p 10

# 启动Web管理界面
./dbcli.sh --web-management
```

### 脚本特性

- ✅ **统一接口**：两个脚本支持相同的命令行参数
- ✅ **智能检测**：自动检测JAR文件、Java环境、依赖库
- ✅ **错误处理**：详细的错误信息和故障排除建议
- ✅ **性能优化**：预设JVM参数和资源配置

## 📋 命令行选项

### 基础命令

| 选项 | 说明 | 示例 |
|------|------|------|
| `-h, --help` | 显示帮助信息 | `dbcli.sh --help` |
| `--version` | 显示版本信息 | `dbcli.sh --version` |
| `--template` | 生成配置模板文件 | `dbcli.sh --template` |
| `--web-management` | 启动Web管理界面 | `dbcli.sh --web-management` |

### 配置与路径

| 选项 | 说明 | 默认值 | 示例 |
|------|------|--------|------|
| `-c, --config <路径>` | 数据库配置目录 | `configs/` | `-c myconfigs` |
| `-m, --metrics <路径>` | 指标配置目录 | `metrics/` | `-m mymetrics` |
| `-o, --output <路径>` | 报告输出目录 | `reports/` | `-o /tmp/reports` |

### 执行控制

| 选项 | 说明 | 默认值 | 示例 |
|------|------|--------|------|
| `-f, --format <格式>` | 报告格式 | `excel` | `-f both` |
| `-p, --threads <数量>` | 并发线程数 | `7` | `-p 10` |
| `--dry-run` | 仅验证配置，不执行 | - | `--dry-run` |

### 安全与测试

| 选项 | 说明 | 示例 |
|------|------|------|
| `-e, --encrypt` | 加密配置文件敏感信息 | `dbcli.sh --encrypt` |
| `-t, --test` | 测试数据库连接 | `dbcli.sh --test` |
| `--clean` | 清理失败清单后测试 | `dbcli.sh --test --clean` |

### 报告格式说明

- `excel`：生成Excel格式报告（.xlsx）
- `html`：生成HTML格式报告（.html）
- `both`：同时生成Excel和HTML报告

## 📝 配置文件规范

### 数据库配置（`configs/`）

**文件命名规则：**

必须以 `-config.yml` 或 `-config.yaml` 结尾，文件名前缀用于识别数据库类型：

- `oracle-*.yml` → Oracle数据库
- `mysql-*.yml` → MySQL数据库  
- `postgresql-*.yml` 或 `pg-*.yml` → PostgreSQL数据库
- `dm-*.yml` → 达梦数据库

**配置格式示例：**

```yaml
# mysql-config.yml
mysql测试库A:
  enable: true                    # 是否启用该系统配置
  port: 3306                      # 默认端口
  username: ENC(...)              # 支持加密存储
  password: ENC(...)              # 支持加密存储
  nodes:
    - host: ENC(...)            # 支持加密的主机地址
      svc_name: mysql           # 服务名
      role: master              # 角色：master/standby
      port: 3306                # 节点端口（可选）

# oracle-config.yml  
ora测试库:
  enable: true
  port: 1521
  username: ENC(...)
  password: ENC(...)
  nodes:
    - host: ENC(...)
      svc_name: ORCL            # Oracle服务名
      role: master
```

**字段说明：**

- `enable`：是否启用该系统配置
- `port`：默认端口（节点未显式配置端口时使用）
- `username/password`：支持 `ENC(...)` 加密存储
- `nodes`：节点列表
  - `host`：支持 `ENC(...)` 加密的主机地址
  - `svc_name/sid_name`：服务名或SID（不同数据库类型有差异）
  - `role`：`master/standby`（内部标准化）
  - `port`：节点级端口（可选，覆盖顶层port）

### 指标配置（`metrics/`）

支持三种YAML组织方式：

1. **顶层数组格式**：
```yaml
- type: mysql
  name: 数据库大小
  description: 查询数据库占用空间
  sql: "SELECT table_schema, SUM(data_length) as size FROM information_schema.tables GROUP BY table_schema"
  
- type: oracle
  name: 表空间监控
  sql: "SELECT tablespace_name, bytes FROM dba_data_files"
```

2. **Map对象格式**：
```yaml
metrics:
  - type: postgresql
    name: 连接数统计
    sql: "SELECT state, count(*) FROM pg_stat_activity GROUP BY state"
    execution_strategy:
      mode: master
      retry_policy:
        enabled: true
        max_attempts: 3
        backoff_ms: 1000
```

3. **单个指标对象**：
```yaml
type: dm
name: 用户表统计
description: 统计用户表数量
sql: "SELECT owner, count(*) as table_count FROM dba_tables GROUP BY owner"
```

执行流程（DbCliRunner）
- 目录创建 → 特殊命令（模板/加密/连接测试）→ 加载数据库配置 → 加载指标配置
- 可选连接预检查：未启用 --test 时仍进行隐式预检查并跳过失败目标
- 指标执行：按目录按类型成组并发执行（ConcurrentMetricsExecutor）
- 报告生成：根据 -f 选择 Excel/HTML 或 both
- 摘要输出：成功/失败计数、按类型聚合、报告路径提示
- 资源清理：关闭连接、线程池与服务

报告输出
- 输出目录：-o/--output（默认 reports/）
- 命名：db_metrics_report_yyyyMMdd.xlsx / .html
- 报告生成工厂：ReportGeneratorFactory（标准 Excel/HTML，扩展见 EnhancedReportGeneratorFactory）
- 高级：增强工厂包含流式 Excel 与分页 HTML 生成器，可应对大结果集

日志
- 配置：src/main/resources/logback.xml
- 控制台 + 文件，多路分发：
  - logs/dbcli.log（主日志，归档至 logs/archive）
  - logs/error/dbcli-error.log
  - logs/execution/execution.log
  - logs/connection/connection.log
  - logs/performance/performance.log
- MDC 维度：traceId/spanId/operation/dbType/systemName/metricName

数据库驱动
- Maven 依赖已包含：MySQL、PostgreSQL
- 需手动放入 lib/：
  - Oracle：ojdbc8.jar（参考 lib/ORACLE_DRIVER_README.md）
  - 达梦：DmJdbcDriver18.jar
- Windows 提供 download-mysql-driver.bat 脚本（如需）

测试
- 测试指南：docs/testing-guide.md
- 依赖：JUnit 5、Mockito（见 pom.xml）
- 运行：mvn test（或使用仓库中的测试脚本）

常见问题
- 找不到配置或指标：检查目录与文件名是否以 -config.yml 结尾、metrics 是否存在 YAML
- 加密/解密失败：确认 ENC(...) 格式与密钥一致，先用 --encrypt 统一加密
- 驱动缺失：确保 lib/ 放入 Oracle/达梦驱动；MySQL/PG 由 Maven 提供
- 报告为空：确认连接测试通过、指标 sql 与类型匹配、无目标被预检查跳过
- Java 版本：确保使用 17+（脚本提示可能较宽松，以 pom 配置为准）

## 🔧 高级功能

### Web管理界面

启动Web管理界面后，可通过浏览器访问 `http://localhost:8080` 进行：

- 📄 **配置文件管理**：在线编辑、保存数据库配置和指标配置
- 🔗 **连接测试**：实时测试数据库连接状态，查看详细测试结果
- 📊 **报告生成**：在线生成Excel/HTML报告，支持预览和下载
- 🔐 **配置加密**：在线加密配置文件中的敏感信息
- 📈 **实时监控**：查看系统运行状态和性能指标

### 黑名单管理

系统自动维护连接失败的黑名单机制：

- **错误日志**：`logs/db_conn_error.txt` - 记录失败连接详情
- **黑名单文件**：`logs/db_conn_blacklist.txt` - 存储加密的失败连接标识
- **自动跳过**：后续执行时自动跳过黑名单中的失败连接

### 增强执行器

提供基于重试、熔断、性能监控的增强执行路径：

- `EnhancedDbCliRunner`：增强版主执行器
- `EnhancedMetricsExecutor`：带重试策略的指标执行器
- 默认未启用，如需深度定制可参考 `core/executor` 包实现

## 🐳 Docker部署

```bash
# 构建镜像
docker build -t dbcli:1.0.0 .

# 运行容器
docker run -v ./configs:/app/configs \
           -v ./metrics:/app/metrics \
           -v ./reports:/app/reports \
           -v ./logs:/app/logs \
           dbcli:1.0.0 -c configs -m metrics -o reports -f both
```

## 📄 许可证

本项目为内部开发项目。如需开源或商业分发，请联系开发团队补充相应的许可证声明。

## 🤝 贡献指南

欢迎提交Issue和Pull Request来改进项目。请遵循以下指南：

1. Fork项目到个人仓库
2. 创建功能分支
3. 提交代码变更
4. 确保测试通过
5. 提交Pull Request

## 📞 技术支持

如遇到问题，请查看：

1. 📖 **文档**：`docs/` 目录下的详细文档
2. 📋 **日志**：`logs/` 目录下的详细日志
3. 🔧 **示例**：`configs/` 和 `metrics/` 目录下的配置示例
4. 🌐 **Web界面**：使用 `--web-management` 启动在线管理工具