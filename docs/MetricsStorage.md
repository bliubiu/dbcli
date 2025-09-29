# 指标数据持久化存储功能说明

## 功能概述

从1.5版本开始，dbcli支持将收集到的指标数据持久化存储到PostgreSQL数据库中，以便进行历史数据分析。

## 功能特性

- 自动将指标数据存储到PostgreSQL数据库
- 支持批量写入以提高性能
- 支持配置文件指定数据库连接信息
- 支持SM4算法加密解密敏感配置项
- 外部化SQL脚本，不硬编码在代码中
- 自动创建表结构和索引

## 配置说明

### 存储配置文件

存储配置文件位于 `configs/storage-config.yaml`，格式如下：

```yaml
# 指标数据持久化存储配置文件
storage:
  # 是否启用存储功能
  enabled: true
  
  # 存储类型 (目前仅支持postgresql)
  type: postgresql
  
  # 批量存储配置
  batchMode: true
  batchSize: 100
  
  # PostgreSQL数据库连接信息
  postgresql:
    host: ENC(localhost)
    port: 5432
    database: ENC(dbcli_metrics)
    username: ENC(dbcli_user)
    password: ENC(dbcli_password)
```

### 配置项说明

- `enabled`: 是否启用存储功能，默认为false
- `type`: 存储类型，目前仅支持postgresql
- `batchMode`: 是否启用批量存储模式，默认为true
- `batchSize`: 批量存储大小，默认为100
- `postgresql.host`: PostgreSQL数据库主机地址，支持SM4加密
- `postgresql.port`: PostgreSQL数据库端口
- `postgresql.database`: PostgreSQL数据库名，支持SM4加密
- `postgresql.username`: PostgreSQL用户名，支持SM4加密
- `postgresql.password`: PostgreSQL密码，支持SM4加密

## 数据库准备

### 创建数据库和用户

```sql
-- 创建数据库
CREATE DATABASE dbcli_metrics;

-- 创建用户
CREATE USER dbcli_user WITH PASSWORD 'dbcli_password';

-- 授权
GRANT ALL PRIVILEGES ON DATABASE dbcli_metrics TO dbcli_user;
```

### 表结构

系统会自动创建以下表结构：

```sql
CREATE TABLE IF NOT EXISTS metric_results (
    id SERIAL PRIMARY KEY,
    system_name VARCHAR(255),
    database_name VARCHAR(255),
    node_ip VARCHAR(45),
    metric_name VARCHAR(255),
    metric_description TEXT,
    metric_type VARCHAR(50),
    value TEXT,
    multi_values JSONB,
    execute_time TIMESTAMP,
    collect_time TIMESTAMP,
    success BOOLEAN,
    error_message TEXT,
    db_type VARCHAR(50),
    threshold_level VARCHAR(20),
    unit VARCHAR(50),
    node_role VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 索引

系统会自动创建以下索引：

```sql
CREATE INDEX IF NOT EXISTS idx_metric_results_system_db 
ON metric_results(system_name, database_name, collect_time);
```

## 使用方法

### 启用存储功能

确保在 `configs/storage-config.yaml` 中将 `enabled` 设置为 `true`。

### 运行dbcli

运行dbcli时，指标数据将自动存储到数据库中：

```bash
./dbcli.sh -c configs -m metrics -o reports
```

### 查询历史数据

```sql
SELECT * FROM metric_results 
WHERE system_name = 'your_system_name' 
  AND collect_time >= '2025-01-01'
ORDER BY collect_time DESC;
```

## 安全性

### SM4加密

配置文件中的敏感信息（如主机地址、数据库名、用户名、密码）可以使用SM4算法进行加密。加密后的值需要使用 `ENC()` 包裹。

例如：
```yaml
postgresql:
  host: ENC(加密后的主机地址)
  database: ENC(加密后的数据库名)
  username: ENC(加密后的用户名)
  password: ENC(加密后的密码)
```

### 加密工具

可以使用dbcli自带的加密功能来加密配置文件中的敏感信息：

```bash
./dbcli.sh --encrypt
```

这将自动加密 `configs/` 目录下所有配置文件中的敏感信息。

## 性能优化

### 批量存储

默认情况下，系统使用批量存储模式，将多个指标结果一起写入数据库，以提高性能。可以通过修改 `batchSize` 配置项来调整批量大小。

### 连接池

系统使用HikariCP连接池来管理数据库连接，以提高连接效率和资源利用率。

## 故障排除

### 连接失败

如果数据库连接失败，请检查以下几点：

1. 数据库服务是否正常运行
2. 数据库地址、端口、用户名、密码是否正确
3. 防火墙是否允许连接
4. 用户是否有足够的权限

### 存储失败

如果指标数据存储失败，请检查以下几点：

1. 存储功能是否已启用
2. 数据库连接是否正常
3. 表结构是否正确
4. 是否有足够的磁盘空间

## 扩展性

### 支持更多数据库

目前仅支持PostgreSQL，但系统设计具有良好的扩展性，可以轻松添加对其他数据库的支持。

### 自定义表结构

如果需要自定义表结构，可以修改 `src/main/resources/sql/create-metric-table.sql` 文件。

### 自定义索引

如果需要自定义索引，可以修改 `src/main/resources/sql/create-metric-index.sql` 文件。