package com.dbcli.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL脚本加载器测试类
 */
public class SQLScriptLoaderTest {

    @Test
    public void testLoadSQLScript() {
        // 测试加载表创建SQL脚本
        String tableSQL = SQLScriptLoader.loadSQLScript("sql/create-metric-table.sql");
        assertNotNull(tableSQL);
        assertTrue(tableSQL.contains("CREATE TABLE"));
        assertTrue(tableSQL.contains("metric_results"));
        
        // 测试加载索引创建SQL脚本
        String indexSQL = SQLScriptLoader.loadSQLScript("sql/create-metric-index.sql");
        assertNotNull(indexSQL);
        assertTrue(indexSQL.contains("CREATE INDEX"));
        assertTrue(indexSQL.contains("idx_metric_results_system_db"));
    }
    
    @Test
    public void testLoadNonExistentSQLScript() {
        // 测试加载不存在的SQL脚本
        String sql = SQLScriptLoader.loadSQLScript("sql/non-existent.sql");
        assertNull(sql);
    }
}