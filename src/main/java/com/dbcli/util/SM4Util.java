package com.dbcli.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SM4加密解密工具类
 * 用于加密和解密配置文件中的敏感信息
 */
public class SM4Util {
    private static final Logger logger = LoggerFactory.getLogger(SM4Util.class);
    
    // SM4密钥长度为16字节（128位）
    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORMATION = "SM4/ECB/PKCS5Padding";
    
    // 默认密钥（实际应用中应该从安全的地方获取）
    private static final String DEFAULT_KEY = "dbcli_default_key_12345678";
    
    /**
     * 加密字符串
     * @param data 待加密的数据
     * @param key 密钥
     * @return 加密后的字符串（Base64编码）
     */
    public static String encrypt(String data, String key) {
        try {
            // 确保密钥长度为16字节
            byte[] keyBytes = getKeyBytes(key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            logger.error("SM4加密失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解密字符串
     * @param encryptedData 加密的数据（Base64编码）
     * @param key 密钥
     * @return 解密后的字符串
     */
    public static String decrypt(String encryptedData, String key) {
        try {
            // 确保密钥长度为16字节
            byte[] keyBytes = getKeyBytes(key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("SM4解密失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 使用默认密钥加密字符串
     * @param data 待加密的数据
     * @return 加密后的字符串（Base64编码）
     */
    public static String encrypt(String data) {
        return encrypt(data, DEFAULT_KEY);
    }
    
    /**
     * 使用默认密钥解密字符串
     * @param encryptedData 加密的数据（Base64编码）
     * @return 解密后的字符串
     */
    public static String decrypt(String encryptedData) {
        return decrypt(encryptedData, DEFAULT_KEY);
    }
    
    /**
     * 获取16字节的密钥
     * @param key 原始密钥
     * @return 16字节的密钥
     */
    private static byte[] getKeyBytes(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[16];
        
        // 如果密钥长度大于16字节，截取前16字节
        if (keyBytes.length >= 16) {
            System.arraycopy(keyBytes, 0, result, 0, 16);
        } else {
            // 如果密钥长度小于16字节，用0填充
            System.arraycopy(keyBytes, 0, result, 0, keyBytes.length);
            for (int i = keyBytes.length; i < 16; i++) {
                result[i] = 0;
            }
        }
        
        return result;
    }
}