package com.dbcli.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DataMaskUtil测试类
 */
public class DataMaskUtilTest {
    
    @Test
    public void testMaskSensitiveData() {
        // 测试密码脱敏
        assertEquals("t*****d", DataMaskUtil.maskSensitiveData("password", "testpwd"));
        assertEquals("ENC(encrypted)", DataMaskUtil.maskSensitiveData("password", "ENC(encrypted)"));
        
        // 测试IP地址脱敏
        assertEquals("***.***.1.100", DataMaskUtil.maskSensitiveData("host", "192.168.1.100"));
        assertEquals("***.***.0.1", DataMaskUtil.maskSensitiveData("ip", "127.0.0.1"));
        
        // 测试用户名脱敏
        assertEquals("a***n", DataMaskUtil.maskSensitiveData("username", "admin"));
        assertEquals("t******r", DataMaskUtil.maskSensitiveData("user", "testuser"));
        
        // 测试数据库名脱敏
        assertEquals("tes***", DataMaskUtil.maskSensitiveData("database", "testdb"));
        assertEquals("ora***", DataMaskUtil.maskSensitiveData("db", "oracle"));
        
        // 测试非敏感字段不脱敏
        assertEquals("normalfield", DataMaskUtil.maskSensitiveData("description", "normalfield"));
    }
    
    @Test
    public void testMaskUsername() {
        // 测试用户名脱敏
        assertEquals("t**t", DataMaskUtil.maskUsername("test"));
        assertEquals("a***n", DataMaskUtil.maskUsername("admin"));
        assertEquals("u*****3", DataMaskUtil.maskUsername("user123"));
        assertEquals("", DataMaskUtil.maskUsername(""));
        assertNull(DataMaskUtil.maskUsername(null));
        
        // 测试单字符用户名
        assertEquals("*", DataMaskUtil.maskUsername("a"));
    }
    
    @Test
    public void testMaskPassword() {
        // 测试密码脱敏
        assertEquals("t*****d", DataMaskUtil.maskPassword("testpwd"));
        assertEquals("a***n", DataMaskUtil.maskPassword("admin"));
        assertEquals("**", DataMaskUtil.maskPassword("ab"));
        assertEquals("ENC(encrypted)", DataMaskUtil.maskPassword("ENC(encrypted)"));
        assertNull(DataMaskUtil.maskPassword(null));
        assertEquals("", DataMaskUtil.maskPassword(""));
    }
    
    @Test
    public void testMaskIpAddress() {
        // 测试IP地址脱敏
        assertEquals("***.***.1.100", DataMaskUtil.maskIpAddress("192.168.1.100"));
        assertEquals("***.***.0.1", DataMaskUtil.maskIpAddress("127.0.0.1"));
        assertEquals("invalid", DataMaskUtil.maskIpAddress("invalid"));
        assertNull(DataMaskUtil.maskIpAddress(null));
        assertEquals("", DataMaskUtil.maskIpAddress(""));
    }
    
    @Test
    public void testIsSensitiveField() {
        // 测试敏感字段识别
        assertTrue(DataMaskUtil.isSensitiveField("password"));
        assertTrue(DataMaskUtil.isSensitiveField("pwd"));
        assertTrue(DataMaskUtil.isSensitiveField("host"));
        assertTrue(DataMaskUtil.isSensitiveField("ip"));
        assertTrue(DataMaskUtil.isSensitiveField("username"));
        assertTrue(DataMaskUtil.isSensitiveField("user"));
        
        assertFalse(DataMaskUtil.isSensitiveField("description"));
        assertFalse(DataMaskUtil.isSensitiveField("name"));
        assertFalse(DataMaskUtil.isSensitiveField(null));
    }
}