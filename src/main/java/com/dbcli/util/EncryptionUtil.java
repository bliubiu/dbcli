package com.dbcli.util;

import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;

/**
 * 国密SM4加密工具类
 * - 支持从环境变量 DBCLI_SM4_KEY 读取32位hex密钥；非法或缺省时回退默认密钥
 * - encrypt/decrypt 采用 CBC + PKCS7Padding（带IV）
 * - encryptDeterministic 采用ECB（无IV，用于一致性比对）
 */
public class EncryptionUtil {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionUtil.class);

    private static final int IV_SIZE = 16;

    // 默认密钥（生产中请通过环境变量覆盖）
    private static final String DEFAULT_KEY = "60f560e7c72a35c2d1969f83b78aafb8";

    // 缓存解析后的hex密钥字符串
    private static volatile String CACHED_KEY_HEX = null;

    /**
     * 解析密钥（优先环境变量 DBCLI_SM4_KEY，长度32的hex）
     */
    private static String resolveKeyHex() {
        if (CACHED_KEY_HEX != null) return CACHED_KEY_HEX;
        String envKey = System.getenv("DBCLI_SM4_KEY");
        String keyHex = DEFAULT_KEY;

        if (envKey != null) {
            String k = envKey.trim().toLowerCase();
            if (k.matches("^[0-9a-f]{32}$")) {
                keyHex = k;
                logger.info("SM4密钥已从环境变量 DBCLI_SM4_KEY 加载");
            } else {
                logger.warn("环境变量 DBCLI_SM4_KEY 非法（需32位hex），使用默认密钥");
            }
        } else {
            logger.debug("未设置环境变量 DBCLI_SM4_KEY，使用默认密钥");
        }
        CACHED_KEY_HEX = keyHex;
        return CACHED_KEY_HEX;
    }

    private static byte[] keyBytes() {
        return hexStringToByteArray(resolveKeyHex());
    }

    /**
     * 加密字符串（CBC）
     */
    public static String encrypt(String plainText) {
        try {
            byte[] key = keyBytes();
            byte[] iv = generateIV();

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new SM4Engine()));
            KeyParameter keyParam = new KeyParameter(key);
            ParametersWithIV params = new ParametersWithIV(keyParam, iv);
            cipher.init(true, params);

            byte[] input = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] output = new byte[cipher.getOutputSize(input.length)];

            int len = cipher.processBytes(input, 0, input.length, output, 0);
            len += cipher.doFinal(output, len);

            // 将IV和加密数据合并
            byte[] result = new byte[IV_SIZE + len];
            System.arraycopy(iv, 0, result, 0, IV_SIZE);
            System.arraycopy(output, 0, result, IV_SIZE, len);

            return "ENC(" + Base64.getEncoder().encodeToString(result) + ")";

        } catch (Exception e) {
            logger.error("加密失败", e);
            return plainText;
        }
    }

    /**
     * 解密字符串（CBC）
     */
    public static String decrypt(String encryptedText) {
        if (!isEncrypted(encryptedText)) {
            return encryptedText;
        }

        try {
            // 移除ENC()包装
            String base64Data = encryptedText.substring(4, encryptedText.length() - 1);
            byte[] data = Base64.getDecoder().decode(base64Data);

            // 提取IV和加密数据
            byte[] iv = new byte[IV_SIZE];
            byte[] encrypted = new byte[data.length - IV_SIZE];
            System.arraycopy(data, 0, iv, 0, IV_SIZE);
            System.arraycopy(data, IV_SIZE, encrypted, 0, encrypted.length);

            byte[] key = keyBytes();

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new SM4Engine()));
            KeyParameter keyParam = new KeyParameter(key);
            ParametersWithIV params = new ParametersWithIV(keyParam, iv);
            cipher.init(false, params);

            byte[] output = new byte[cipher.getOutputSize(encrypted.length)];
            int len = cipher.processBytes(encrypted, 0, encrypted.length, output, 0);
            len += cipher.doFinal(output, len);

            return new String(output, 0, len, StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("解密失败", e);
            return encryptedText;
        }
    }

    /**
     * 判断字符串是否已加密
     */
    public static boolean isEncrypted(String text) {
        return text != null && text.startsWith("ENC(") && text.endsWith(")");
    }

    /**
     * 随机IV
     */
    private static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * hex转byte[]
     */
    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("非法hex长度");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 确定性SM4加密（ECB，无IV），用于一致性比对
     * 返回格式：ENC_D(Base64)
     */
    public static String encryptDeterministic(String plainText) {
        try {
            byte[] key = keyBytes();

            // ECB: 不使用IV，确保相同明文得到相同密文（用于比对）
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new SM4Engine());
            KeyParameter keyParam = new KeyParameter(key);
            cipher.init(true, keyParam);

            byte[] input = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] outBuf = new byte[cipher.getOutputSize(input.length)];
            int len = cipher.processBytes(input, 0, input.length, outBuf, 0);
            len += cipher.doFinal(outBuf, len);

            byte[] result = Arrays.copyOf(outBuf, len);
            return "ENC_D(" + Base64.getEncoder().encodeToString(result) + ")";
        } catch (Exception e) {
            logger.warn("确定性加密失败: {}", e.getMessage());
            return null; // 避免返回明文
        }
    }
}