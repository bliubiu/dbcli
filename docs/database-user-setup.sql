-- ============================================================================
-- 数据库监控账号创建脚本
-- 用于dbcli工具连接数据库进行指标收集
-- ============================================================================

-- ============================================================================
-- MySQL 监控账号创建
-- ============================================================================
-- 创建监控用户
CREATE USER 'dbmonitor'@'%' IDENTIFIED BY 'Db#1359Mon';

-- 授予必要权限（生产环境建议使用最小权限原则）
-- 完整权限（适用于测试环境）
GRANT ALL PRIVILEGES ON *.* TO 'dbmonitor'@'%';

-- 最小权限（推荐用于生产环境）
-- GRANT SELECT ON *.* TO 'dbmonitor'@'%';
-- GRANT SELECT ON information_schema.* TO 'dbmonitor'@'%';
-- GRANT SELECT ON performance_schema.* TO 'dbmonitor'@'%';
-- GRANT SELECT ON mysql.* TO 'dbmonitor'@'%';
-- GRANT PROCESS ON *.* TO 'dbmonitor'@'%';
-- GRANT REPLICATION CLIENT ON *.* TO 'dbmonitor'@'%';

FLUSH PRIVILEGES;

-- 验证用户创建
SELECT User, Host FROM mysql.user WHERE User = 'dbmonitor';

-- ============================================================================
-- Oracle 监控账号创建
-- ============================================================================
-- 创建监控用户
CREATE USER dbmonitor IDENTIFIED BY "Db#1359Mon";

-- 授予基本连接权限
GRANT CONNECT, RESOURCE TO dbmonitor;

-- 授予DBA权限（适用于测试环境）
GRANT DBA TO dbmonitor;

-- 最小权限（推荐用于生产环境）
-- GRANT CREATE SESSION TO dbmonitor;
-- GRANT SELECT_CATALOG_ROLE TO dbmonitor;
-- GRANT SELECT ANY DICTIONARY TO dbmonitor;
-- GRANT SELECT ON V_$SESSION TO dbmonitor;
-- GRANT SELECT ON V_$SESSTAT TO dbmonitor;
-- GRANT SELECT ON V_$STATNAME TO dbmonitor;
-- GRANT SELECT ON V_$INSTANCE TO dbmonitor;
-- GRANT SELECT ON V_$DATABASE TO dbmonitor;
-- GRANT SELECT ON V_$DATAFILE TO dbmonitor;
-- GRANT SELECT ON V_$TABLESPACE TO dbmonitor;
-- GRANT SELECT ON DBA_TABLESPACES TO dbmonitor;
-- GRANT SELECT ON DBA_DATA_FILES TO dbmonitor;
-- GRANT SELECT ON DBA_FREE_SPACE TO dbmonitor;
-- GRANT SELECT ON DBA_USERS TO dbmonitor;
-- GRANT SELECT ON DBA_OBJECTS TO dbmonitor;
-- GRANT SELECT ON DBA_SEGMENTS TO dbmonitor;
-- GRANT SELECT ON V_$SYSSTAT TO dbmonitor;
-- GRANT SELECT ON V_$SYSTEM_EVENT TO dbmonitor;
-- GRANT SELECT ON V_$LOCK TO dbmonitor;
-- GRANT SELECT ON V_$LOCKED_OBJECT TO dbmonitor;
-- GRANT SELECT ON V_$SQL TO dbmonitor;
-- GRANT SELECT ON V_$SQLAREA TO dbmonitor;
-- GRANT SELECT ON V_$PROCESS TO dbmonitor;
-- GRANT SELECT ON V_$PARAMETER TO dbmonitor;

-- 验证用户创建
SELECT USERNAME, ACCOUNT_STATUS FROM DBA_USERS WHERE USERNAME = 'DBMONITOR';

-- ============================================================================
-- PostgreSQL 监控账号创建
-- ============================================================================
-- 创建监控用户
CREATE USER dbmonitor WITH LOGIN PASSWORD 'Db#1359Mon';

-- 授予监控角色权限
GRANT pg_monitor TO dbmonitor;

-- 授予读取权限
GRANT SELECT ON ALL TABLES IN SCHEMA information_schema TO dbmonitor;
GRANT SELECT ON ALL TABLES IN SCHEMA pg_catalog TO dbmonitor;

-- 最小权限（推荐用于生产环境）
-- GRANT CONNECT ON DATABASE postgres TO dbmonitor;
-- GRANT pg_read_all_stats TO dbmonitor;
-- GRANT pg_read_all_settings TO dbmonitor;
-- GRANT SELECT ON pg_stat_database TO dbmonitor;
-- GRANT SELECT ON pg_stat_user_tables TO dbmonitor;
-- GRANT SELECT ON pg_stat_activity TO dbmonitor;
-- GRANT SELECT ON pg_stat_replication TO dbmonitor;
-- GRANT SELECT ON pg_database TO dbmonitor;
-- GRANT SELECT ON pg_tablespace TO dbmonitor;
-- GRANT SELECT ON pg_class TO dbmonitor;
-- GRANT SELECT ON pg_namespace TO dbmonitor;
-- GRANT SELECT ON pg_locks TO dbmonitor;

-- 验证用户创建
SELECT usename, usesuper FROM pg_user WHERE usename = 'dbmonitor';

-- ============================================================================
-- 达梦数据库 监控账号创建
-- ============================================================================
-- 创建监控用户
CREATE USER dbmonitor IDENTIFIED BY "Db#1359Mon";

-- 授予基本权限
GRANT PUBLIC, RESOURCE, VTI, SOI, SVI TO dbmonitor;

-- 授予系统表查询权限
GRANT SELECT ON SYS.DBA_DATA_FILES TO dbmonitor;
GRANT SELECT ON SYS.DBA_USERS TO dbmonitor;
GRANT SELECT ON SYS.DBA_FREE_SPACE TO dbmonitor;
GRANT SELECT ON SYS.DBA_TABLESPACES TO dbmonitor;
GRANT SELECT ON SYS.DBA_OBJECTS TO dbmonitor;
GRANT SELECT ON SYS.DBA_SEGMENTS TO dbmonitor;

-- 授予动态性能视图权限
GRANT SELECT ON SYS.V$SESSION TO dbmonitor;
GRANT SELECT ON SYS.V$SESSIONS TO dbmonitor;
GRANT SELECT ON SYS.V$INSTANCE TO dbmonitor;
GRANT SELECT ON SYS.V$DATABASE TO dbmonitor;
GRANT SELECT ON SYS.V$DATAFILE TO dbmonitor;
GRANT SELECT ON SYS.V$TABLESPACE TO dbmonitor;
GRANT SELECT ON SYS.V$SYSSTAT TO dbmonitor;
GRANT SELECT ON SYS.V$SYSTEM_EVENT TO dbmonitor;
GRANT SELECT ON SYS.V$LOCK TO dbmonitor;
GRANT SELECT ON SYS.V$LOCKED_OBJECT TO dbmonitor;
GRANT SELECT ON SYS.V$SQL TO dbmonitor;
GRANT SELECT ON SYS.V$PROCESS TO dbmonitor;
GRANT SELECT ON SYS.V$PARAMETER TO dbmonitor;

-- 验证用户创建
SELECT USERNAME, ACCOUNT_STATUS FROM DBA_USERS WHERE USERNAME = 'DBMONITOR';

-- ============================================================================
-- 安全建议
-- ============================================================================
/*
1. 密码策略：
   - 使用复杂密码，包含大小写字母、数字和特殊字符
   - 定期更换密码
   - 避免使用默认密码

2. 权限控制：
   - 生产环境建议使用最小权限原则
   - 定期审查用户权限
   - 监控用户活动

3. 网络安全：
   - 限制连接来源IP
   - 使用SSL/TLS加密连接
   - 配置防火墙规则

4. 审计日志：
   - 启用数据库审计功能
   - 记录监控用户的操作
   - 定期检查审计日志

5. 账号管理：
   - 设置账号过期时间
   - 禁用不必要的账号
   - 使用专用监控账号，避免使用管理员账号
*/

-- ============================================================================
-- 权限验证查询
-- ============================================================================

-- MySQL权限验证
-- SHOW GRANTS FOR 'dbmonitor'@'%';

-- Oracle权限验证
-- SELECT * FROM DBA_ROLE_PRIVS WHERE GRANTEE = 'DBMONITOR';
-- SELECT * FROM DBA_SYS_PRIVS WHERE GRANTEE = 'DBMONITOR';
-- SELECT * FROM DBA_TAB_PRIVS WHERE GRANTEE = 'DBMONITOR';

-- PostgreSQL权限验证
-- SELECT * FROM information_schema.role_table_grants WHERE grantee = 'dbmonitor';
-- SELECT * FROM pg_roles WHERE rolname = 'dbmonitor';

-- 达梦权限验证
-- SELECT * FROM DBA_ROLE_PRIVS WHERE GRANTEE = 'DBMONITOR';
-- SELECT * FROM DBA_SYS_PRIVS WHERE GRANTEE = 'DBMONITOR';
-- SELECT * FROM DBA_TAB_PRIVS WHERE GRANTEE = 'DBMONITOR';