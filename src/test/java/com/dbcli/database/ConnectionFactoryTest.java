package com.dbcli.database;

import com.dbcli.model.DatabaseConfig;
import com.dbcli.model.DatabaseNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionFactoryTest {
    private ConnectionFactory connectionFactory;
    private DatabaseConfig config;
    private DatabaseNode node;

    @BeforeEach
    public void setUp() {
        connectionFactory = new ConnectionFactory();
        
        config = new DatabaseConfig();
        config.setType("oracle");
        config.setHost("localhost");
        config.setPort(1521);
        config.setUsername("user");
        config.setPassword("pass");
        config.setMaxPoolSize(5);
        config.setMinIdle(1);
        config.setConnectionTimeout(30000L);
        config.setIdleTimeout(600000L);
        config.setMaxLifetime(1800000L);
        
        node = new DatabaseNode();
        node.setHost("localhost");
        node.setPort(1521);
        node.setSvcName("ORCL");
        node.setRole("master");
    }

    @Test
    public void testBuildConnectionStringForOracle() {
        config.setType("oracle");
        node.setSvcName("ORCL");
        
        String connectionString = connectionFactory.buildConnectionString("oracle", config, node);
        assertNotNull(connectionString, "Connection string should not be null for Oracle");
        assertEquals("jdbc:oracle:thin:@//localhost:1521/ORCL", connectionString, "JDBC URL for Oracle is incorrect");
    }

    @Test
    public void testBuildConnectionStringForMySQL() {
        config.setType("mysql");
        config.setPort(3306);
        node.setPort(3306);
        node.setSvcName("testdb");

        String connectionString = connectionFactory.buildConnectionString("mysql", config, node);
        assertNotNull(connectionString, "Connection string should not be null for MySQL");
        assertTrue(connectionString.startsWith("jdbc:mysql://localhost:3306/testdb"), "JDBC URL for MySQL is incorrect");
    }

    @Test
    public void testBuildConnectionStringForPostgreSQL() {
        config.setType("postgresql");
        config.setPort(5432);
        node.setPort(5432);
        node.setSvcName("postgres");

        String connectionString = connectionFactory.buildConnectionString("postgresql", config, node);
        assertNotNull(connectionString, "Connection string should not be null for PostgreSQL");
        assertEquals("jdbc:postgresql://localhost:5432/postgres", connectionString, "JDBC URL for PostgreSQL is incorrect");
    }
    
    @Test
    public void testConnectionTest() {
        // 使用H2数据库进行测试
        config.setType("h2");
        node.setSvcName("testdb");
        
        boolean result = connectionFactory.testConnection("test_system", node, config, "h2");
        assertTrue(result, "Connection test should pass with H2 database");
    }
}
