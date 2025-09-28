package com.dbcli.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * EncryptionUtil测试类
 */
public class EncryptionUtilTest {

    @Test
    public void testEncrypt() {
        String plainText = "test123";
        String encrypted = EncryptionUtil.encrypt(plainText);

        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
        assertTrue(encrypted.startsWith("ENC("));
        assertTrue(encrypted.endsWith(")"));
    }

    @Test
    public void testDecrypt() {
        String plainText = "test123";
        String encrypted = EncryptionUtil.encrypt(plainText);
        String decrypted = EncryptionUtil.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }

    @Test
    public void testEncryptDecryptConsistency() {
        String originalText = "testPassword123";

        String encrypted1 = EncryptionUtil.encrypt(originalText);
        String encrypted2 = EncryptionUtil.encrypt(originalText);

        // 由于使用随机IV，每次加密结果应该不同
        assertNotEquals(encrypted1, encrypted2, "相同文本多次加密结果应不同（因为使用随机IV）");

        // 但解密后应该得到相同的原文
        String decrypted1 = EncryptionUtil.decrypt(encrypted1);
        String decrypted2 = EncryptionUtil.decrypt(encrypted2);

        assertEquals(originalText, decrypted1, "解密后应得到原文");
        assertEquals(originalText, decrypted2, "解密后应得到原文");
    }

    @Test
    public void testEncryptEmptyString() {
        String encrypted = EncryptionUtil.encrypt("");
        String decrypted = EncryptionUtil.decrypt(encrypted);
        assertEquals("", decrypted, "空字符串加密解密后应保持为空");
    }

    @Test
    public void testIsEncrypted() {
        String plainText = "test123";
        String encrypted = EncryptionUtil.encrypt(plainText);

        assertFalse(EncryptionUtil.isEncrypted(plainText), "普通文本不应被识别为已加密");
        assertTrue(EncryptionUtil.isEncrypted(encrypted), "加密文本应被识别为已加密");
        assertFalse(EncryptionUtil.isEncrypted(null), "null不应被识别为已加密");
        assertFalse(EncryptionUtil.isEncrypted(""), "空字符串不应被识别为已加密");
    }

    @Test
    public void testDecryptNonEncryptedText() {
        String plainText = "test123";
        String result = EncryptionUtil.decrypt(plainText);

        // 对于非加密文本，应该返回原文
        assertEquals(plainText, result, "非加密文本解密应返回原文");
    }
}