package com.dbcli.service;

import com.dbcli.model.MetricConfig;
import com.dbcli.model.MetricResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 测试单值指标修复功能
 * 验证有columns定义和无columns定义的单值指标都能正确处理
 */
public class SingleValueMetricsFixTest {

    @Mock
    private Connection mockConnection;
    
    @Mock
    private PreparedStatement mockStatement;
    
    @Mock
    private ResultSet mockResultSet;
    
    @Mock
    private ResultSetMetaData mockMetaData;

    private MetricsCollectionService service;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        
        // 设置基本的mock行为
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
    }

    @Test
    void testSingleValueMetricWithColumns() throws SQLException {
        // 测试有columns定义的单值指标（如db_config）
        MetricConfig metric = new MetricConfig();
        metric.setType("SINGLE");
        metric.setName("db_config");
        metric.setDescription("数据库配置信息");
        metric.setSql("SELECT VERSION() AS version, @@DATADIR as datadir, @@port as port");
        metric.setColumns(Arrays.asList("数据库版本", "数据库目录", "端口号"));

        // Mock ResultSet行为
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockMetaData.getColumnCount()).thenReturn(3);
        when(mockResultSet.getObject(1)).thenReturn("8.4.5");
        when(mockResultSet.getObject(2)).thenReturn("/mysql/data/");
        when(mockResultSet.getObject(3)).thenReturn(3307);

        MetricResult result = new MetricResult("mysql测试库", "mysql", "***.***.10.186",
                "db_config", "数据库配置信息", "SINGLE", "mysql");

        // 调用私有方法进行测试（通过反射）
        try {
            java.lang.reflect.Method method = MetricsCollectionService.class.getDeclaredMethod(
                    "collectSingleValueMetric", Connection.class, MetricConfig.class, MetricResult.class);
            method.setAccessible(true);
            method.invoke(service, mockConnection, metric, result);

            // 验证结果
            assertNotNull(result.getColumns());
            assertEquals(3, result.getColumns().size());
            assertEquals("数据库版本", result.getColumns().get(0));
            assertEquals("数据库目录", result.getColumns().get(1));
            assertEquals("端口号", result.getColumns().get(2));

            assertNotNull(result.getMultiValues());
            assertEquals(1, result.getMultiValues().size());
            
            Map<String, Object> row = result.getMultiValues().get(0);
            assertEquals("8.4.5", row.get("数据库版本"));
            assertEquals("/mysql/data/", row.get("数据库目录"));
            assertEquals(3307, row.get("端口号"));

            assertEquals("8.4.5 | /mysql/data/ | 3307", result.getValue().toString());

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }

    @Test
    void testSingleValueMetricWithoutColumns() throws SQLException {
        // 测试无columns定义的单值指标（如engine）
        MetricConfig metric = new MetricConfig();
        metric.setType("SINGLE");
        metric.setName("engine");
        metric.setDescription("默认存储引擎");
        metric.setSql("show variables like 'default_storage_engine'");
        // 注意：没有设置columns

        // Mock ResultSet行为
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getObject(1)).thenReturn("InnoDB");

        MetricResult result = new MetricResult("mysql测试库", "mysql", "***.***.10.186",
                "engine", "默认存储引擎", "SINGLE", "mysql");

        // 调用私有方法进行测试（通过反射）
        try {
            java.lang.reflect.Method method = MetricsCollectionService.class.getDeclaredMethod(
                    "collectSingleValueMetric", Connection.class, MetricConfig.class, MetricResult.class);
            method.setAccessible(true);
            method.invoke(service, mockConnection, metric, result);

            // 验证结果
            assertNotNull(result.getColumns());
            assertEquals(1, result.getColumns().size());
            assertEquals("默认存储引擎", result.getColumns().get(0)); // 应该使用description作为列名

            assertNotNull(result.getMultiValues());
            assertEquals(1, result.getMultiValues().size());
            
            Map<String, Object> row = result.getMultiValues().get(0);
            assertEquals("InnoDB", row.get("默认存储引擎"));

            assertEquals("InnoDB", result.getValue().toString());

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }

    @Test
    void testSingleValueMetricWithoutColumnsAndDescription() throws SQLException {
        // 测试既无columns也无description的单值指标
        MetricConfig metric = new MetricConfig();
        metric.setType("SINGLE");
        metric.setName("test_metric");
        // 注意：没有设置description和columns
        metric.setSql("SELECT 'test_value'");

        // Mock ResultSet行为
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getObject(1)).thenReturn("test_value");

        MetricResult result = new MetricResult("test_system", "test_db", "127.0.0.1",
                "test_metric", null, "SINGLE", "mysql");

        // 调用私有方法进行测试（通过反射）
        try {
            java.lang.reflect.Method method = MetricsCollectionService.class.getDeclaredMethod(
                    "collectSingleValueMetric", Connection.class, MetricConfig.class, MetricResult.class);
            method.setAccessible(true);
            method.invoke(service, mockConnection, metric, result);

            // 验证结果
            assertNotNull(result.getColumns());
            assertEquals(1, result.getColumns().size());
            assertEquals("test_metric", result.getColumns().get(0)); // 应该使用name作为列名

            assertNotNull(result.getMultiValues());
            assertEquals(1, result.getMultiValues().size());
            
            Map<String, Object> row = result.getMultiValues().get(0);
            assertEquals("test_value", row.get("test_metric"));

            assertEquals("test_value", result.getValue().toString());

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }

    @Test
    void testExcelReportGeneratorHandlesAllSingleValueMetrics() {
        // 测试Excel报告生成器能处理所有类型的单值指标
        ExcelReportGenerator generator = new ExcelReportGenerator();

        // 创建测试数据：包含有columns和无columns的单值指标
        MetricResult resultWithColumns = new MetricResult("mysql测试库", "mysql", "***.***.10.186",
                "db_config", "数据库配置信息", "SINGLE", "mysql");
        resultWithColumns.setColumns(Arrays.asList("数据库版本", "数据库目录", "端口号"));
        resultWithColumns.setMultiValues(Arrays.asList(
                Map.of("数据库版本", "8.4.5", "数据库目录", "/mysql/data/", "端口号", 3307)
        ));
        resultWithColumns.setValue("8.4.5 | /mysql/data/ | 3307");
        resultWithColumns.setSuccess(true);
        resultWithColumns.setExecuteTime(LocalDateTime.now());

        MetricResult resultWithoutColumns = new MetricResult("mysql测试库", "mysql", "***.***.10.186",
                "engine", "默认存储引擎", "SINGLE", "mysql");
        resultWithoutColumns.setColumns(Arrays.asList("默认存储引擎"));
        resultWithoutColumns.setMultiValues(Arrays.asList(
                Map.of("默认存储引擎", "InnoDB")
        ));
        resultWithoutColumns.setValue("InnoDB");
        resultWithoutColumns.setSuccess(true);
        resultWithoutColumns.setExecuteTime(LocalDateTime.now());

        List<MetricResult> results = Arrays.asList(resultWithColumns, resultWithoutColumns);

        // 验证不会抛出异常
        assertDoesNotThrow(() -> {
            generator.generate(results, "target/test-reports", "metrics");
        });
    }
}