package com.dbcli.storage;

import com.dbcli.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 存储配置加载器测试类
 */
public class StorageConfigLoaderTest {

    @Test
    public void testLoadConfig() {
        // 测试加载配置文件
        StorageConfig config = StorageConfigLoader.loadConfig("configs/storage-config.yaml");
        assertNotNull(config);
        // 注意：根据当前配置文件，enabled应该是true
        assertTrue(config.isEnabled());
        assertEquals("postgresql", config.getType());
        assertTrue(config.isBatchMode());
        assertEquals(100, config.getBatchSize());
    }

    @Test
    public void testDecryptIfEncrypted() {
        // 测试解密功能
        String original = "test_value";
        String encrypted = EncryptionUtil.encrypt(original);
        
        // 验证EncryptionUtil正常工作
        String decrypted = EncryptionUtil.decrypt(encrypted);
        assertEquals(original, decrypted);
    }
    
    @Test
    public void testIsEncrypted() {
        // 测试isEncrypted方法
        String original = "test_value";
        String encrypted = EncryptionUtil.encrypt(original);
        
        assertTrue(EncryptionUtil.isEncrypted(encrypted));
        assertFalse(EncryptionUtil.isEncrypted(original));
    }
}