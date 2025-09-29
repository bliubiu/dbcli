package com.dbcli.util;

import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Arrays;

/**
 * SM4加密解密工具类
 * 用于加密和解密配置文件中的敏感信息
 * 使用ECB模式以确保相同明文得到相同密文，适用于配置文件加密场景
 */
public class SM4Util {
    private static final Logger logger = LoggerFactory.getLogger(SM4Util.class);
    
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
            
            // 使用ECB模式的SM4加密
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new SM4Engine());
            KeyParameter keyParam = new KeyParameter(keyBytes);
            cipher.init(true, keyParam);
            
            byte[] input = data.getBytes(StandardCharsets.UTF_8);
            byte[] output = new byte[cipher.getOutputSize(input.length)];
            
            int len = cipher.processBytes(input, 0, input.length, output, 0);
            len += cipher.doFinal(output, 0);
            
            // 只返回有效数据部分
            return Base64.getEncoder().encodeToString(Arrays.copyOf(output, len));
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
            
            // 使用ECB模式的SM4解密
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new SM4Engine());
            KeyParameter keyParam = new KeyParameter(keyBytes);
            cipher.init(false, keyParam);
            
            byte[] input = Base64.getDecoder().decode(encryptedData);
            byte[] output = new byte[cipher.getOutputSize(input.length)];
            
            int len = cipher.processBytes(input, 0, input.length, output, 0);
            len += cipher.doFinal(output, 0);
            
            // 只返回有效数据部分
            return new String(Arrays.copyOf(output, len), StandardCharsets.UTF_8);
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