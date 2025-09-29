-- 创建指标结果表
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