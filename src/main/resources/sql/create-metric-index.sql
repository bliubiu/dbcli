-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_metric_results_system_db 
ON metric_results(system_name, database_name, collect_time);