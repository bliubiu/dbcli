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
        
        StorageConfig config = new StorageConfig();
        // 这里我们手动测试解密逻辑
        // 在实际的StorageConfigLoader中，decryptIfEncrypted方法是私有的
        // 我们可以通过其他方式验证EncryptionUtil的功能
        String decrypted = EncryptionUtil.decrypt(encrypted);
        assertEquals(original, decrypted);
    }
}