package com.dbcli.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SM4加密解密工具类测试
 */
public class SM4UtilTest {

    @Test
    public void testEncryptDecrypt() {
        String original = "test_data";
        String encrypted = SM4Util.encrypt(original);
        String decrypted = SM4Util.decrypt(encrypted);
        
        assertNotNull(encrypted);
        assertNotNull(decrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void testEncryptDecryptWithCustomKey() {
        String original = "test_data";
        String key = "custom_key_123456";
        String encrypted = SM4Util.encrypt(original, key);
        String decrypted = SM4Util.decrypt(encrypted, key);
        
        assertNotNull(encrypted);
        assertNotNull(decrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void testDecryptWithInvalidData() {
        String decrypted = SM4Util.decrypt("invalid_data");
        assertNull(decrypted);
    }
}