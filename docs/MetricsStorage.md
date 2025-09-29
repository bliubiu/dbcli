# 指标数据持久化存储功能说明

## 功能概述

dbcli 1.5版本引入了指标数据持久化存储功能，可以将收集到的数据库指标数据存储到PostgreSQL数据库中，以便进行历史数据分析和趋势跟踪。

## 功能特性

1. **PostgreSQL存储支持**：将指标数据持久化存储到PostgreSQL数据库
2. **批量存储优化**：支持批量写入以提高存储性能
3. **自动表结构创建**：首次运行时自动创建必要的表结构和索引
4. **灵活配置**：支持通过配置文件自定义存储参数
5. **资源管理**：自动管理数据库连接和资源释放

## 存储结构

### 指标结果表 (metric_results)

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | SERIAL PRIMARY KEY | 主键 |
| system_name | VARCHAR(255) | 系统名称 |
| database_name | VARCHAR(255) | 数据库名称 |
| node_ip | VARCHAR(45) | 节点IP地址 |
| metric_name | VARCHAR(255) | 指标名称 |
| metric_description | TEXT | 指标描述 |
| metric_type | VARCHAR(50) | 指标类型 |
| value | TEXT | 指标值 |
| multi_values | JSONB | 多值指标数据 |
| execute_time | TIMESTAMP | 执行时间 |
| collect_time | TIMESTAMP | 收集时间 |
| success | BOOLEAN | 是否成功 |
| error_message | TEXT | 错误信息 |
| db_type | VARCHAR(50) | 数据库类型 |
| threshold_level | VARCHAR(20) | 阈值级别 |
| unit | VARCHAR(50) | 单位 |
| node_role | VARCHAR(20) | 节点角色 |
| created_at | TIMESTAMP | 创建时间 |

## 配置说明

### 存储配置参数

```yaml
storage:
  enabled: true          # 是否启用存储功能
  type: postgresql       # 存储类型
  host: localhost        # PostgreSQL主机地址
  port: 5432            # PostgreSQL端口
  database: dbcli_metrics # 数据库名称
  username: dbcli_user   # 用户名
  password: dbcli_password # 密码
  batchMode: true        # 是否启用批量模式
  batchSize: 100         # 批量大小
```

## 使用方法

### 1. 数据库准备

首先需要准备一个PostgreSQL数据库：

```sql
-- 创建数据库
CREATE DATABASE dbcli_metrics;

-- 创建用户
CREATE USER dbcli_user WITH PASSWORD 'dbcli_password';

-- 授权
GRANT ALL PRIVILEGES ON DATABASE dbcli_metrics TO dbcli_user;
```

### 2. 配置启用

在运行dbcli时，确保存储配置已正确设置。默认情况下，存储功能是启用的。

### 3. 运行指标收集

正常运行dbcli进行指标收集，收集到的数据将自动存储到配置的PostgreSQL数据库中：

```bash
# 使用启动脚本运行
./dbcli.sh -c configs -m metrics -o reports

# 或直接使用Java运行
java -jar target/dbcli-1.0.0.jar -c configs -m metrics -o reports
```

## 查询历史数据

可以使用标准SQL查询历史指标数据：

```sql
-- 查询特定系统的指标数据
SELECT * FROM metric_results 
WHERE system_name = 'your_system_name' 
  AND collect_time >= '2025-01-01' 
  AND collect_time <= '2025-12-31'
ORDER BY collect_time DESC;

-- 查询特定指标的趋势
SELECT metric_name, value, collect_time 
FROM metric_results 
WHERE metric_name = 'cpu_usage' 
  AND system_name = 'your_system_name'
ORDER BY collect_time;

-- 按时间段统计指标平均值
SELECT 
    metric_name,
    AVG(CAST(value AS NUMERIC)) as avg_value,
    MIN(collect_time) as period_start,
    MAX(collect_time) as period_end
FROM metric_results 
WHERE collect_time >= '2025-01-01' 
  AND collect_time <= '2025-01-31'
  AND value ~ '^[0-9]+\.?[0-9]*$'  -- 确保value是数字
GROUP BY metric_name;
```

## 性能优化

1. **批量写入**：默认启用批量模式，减少数据库I/O操作
2. **索引优化**：自动创建必要的索引以提高查询性能
3. **连接池管理**：合理管理数据库连接，避免连接泄漏

## 注意事项

1. 确保PostgreSQL数据库服务正常运行
2. 确保存储配置中的用户名和密码正确
3. 确保网络连接正常，能够访问PostgreSQL数据库
4. 定期清理历史数据以避免数据库过大
5. 监控数据库性能，必要时调整批量大小参数

## 故障排除

### 常见问题

1. **连接失败**：检查数据库地址、端口、用户名和密码
2. **权限不足**：确保用户具有创建表和写入数据的权限
3. **存储空间不足**：监控数据库存储空间，及时清理历史数据

### 日志查看

查看dbcli日志文件以获取详细的存储操作信息：
- `logs/dbcli_INFO.log` - 一般信息日志
- `logs/dbcli_ERROR.log` - 错误日志