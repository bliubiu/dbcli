package com.dbcli.model;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MetricResultTest {

    @Test
    public void testConstructorAndGetters() {
        MetricResult result = new MetricResult("sys1", "db1", "127.0.0.1", "Uptime", "DB Uptime", "SINGLE", "mysql");
        assertEquals("sys1", result.getSystemName(), "System name should match");
        assertEquals("db1", result.getDatabaseName(), "Database name should match");
        assertEquals("127.0.0.1", result.getNodeIp(), "Node IP should match");
        assertEquals("Uptime", result.getMetricName(), "Metric name should match");
        assertEquals("DB Uptime", result.getDescription(), "Description should match");
        assertEquals("SINGLE", result.getMetricType(), "Metric type should match");
        assertEquals("mysql", result.getDbType(), "DB type should match");
        assertNull(result.getValue(), "Initial value should be null");
        assertNull(result.getMultiValues(), "Initial multi-values should be null");
        assertFalse(result.isSuccess(), "Initial success state should be false");
        assertNull(result.getErrorMessage(), "Initial error message should be null");
    }

    @Test
    public void testSetters() {
        MetricResult result = new MetricResult("sys1", "db1", "127.0.0.1", "Uptime", "DB Uptime", "SINGLE", "mysql");
        result.setValue(12345);
        result.setSuccess(true);
        result.setErrorMessage("No error");

        assertEquals(12345, result.getValue(), "Value should be updated");
        assertTrue(result.isSuccess(), "Success state should be updated");
        assertEquals("No error", result.getErrorMessage(), "Error message should be updated");

        Map<String, Object> row = new HashMap<>();
        row.put("col1", "val1");
        List<Map<String, Object>> multiValues = Arrays.asList(row);
        result.setMultiValues(multiValues);

        assertEquals(multiValues, result.getMultiValues(), "Multi-values should be updated");
    }
}