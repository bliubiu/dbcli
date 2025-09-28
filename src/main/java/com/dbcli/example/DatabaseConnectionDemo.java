package com.dbcli.example;

import com.dbcli.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据库连接演示和加密测试
 */
public class DatabaseConnectionDemo {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionDemo.class);
    
    public static void main(String[] args) {
        System.out.println("=== 数据库连接演示和加密测试 ===");
        
        // 测试当前配置文件中的加密值
        String[] testValues = {
            "ENC(wsA7HXDKzTSuZ3kG6f6eIjLhfJeGKOhF4YJT8vRJu9U=)", // username
            "ENC(Lb6jBl8uNlD7h7Y0DWzxxc8syMph8XWVoaadcxnc0v4=)", // password  
            "ENC(zdOPy9lLWVrR3PCSrwbwB0bV5QPDDan9NPJjW7j22z4=)"  // host
        };
        
        String[] labels = {"用户名", "密码", "主机地址"};
        
        System.out.println("\n1. 测试配置文件中的加密值解密:");
        for (int i = 0; i < testValues.length; i++) {
            String encrypted = testValues[i];
            System.out.println(labels[i] + " (加密): " + encrypted);
            
            try {
                String decrypted = EncryptionUtil.decrypt(encrypted);
                System.out.println(labels[i] + " (解密): " + decrypted);
                
                // 测试重新加密是否一致
                String reencrypted = EncryptionUtil.encrypt(decrypted);
                System.out.println(labels[i] + " (重新加密): " + reencrypted);
                
                // 测试重新解密
                String redecrypted = EncryptionUtil.decrypt(reencrypted);
                System.out.println(labels[i] + " (重新解密): " + redecrypted);
                System.out.println("解密一致性: " + decrypted.equals(redecrypted));
                
            } catch (Exception e) {
                System.out.println(labels[i] + " 解密失败: " + e.getMessage());
            }
            System.out.println();
        }
        
        System.out.println("\n2. 测试新的加密解密:");
        String[] plainTexts = {"testuser", "testpass", "192.168.1.100"};
        
        for (int i = 0; i < plainTexts.length; i++) {
            String plain = plainTexts[i];
            System.out.println(labels[i] + " (明文): " + plain);
            
            try {
                String encrypted = EncryptionUtil.encrypt(plain);
                System.out.println(labels[i] + " (新加密): " + encrypted);
                
                String decrypted = EncryptionUtil.decrypt(encrypted);
                System.out.println(labels[i] + " (新解密): " + decrypted);
                System.out.println("新加密一致性: " + plain.equals(decrypted));
                
            } catch (Exception e) {
                System.out.println(labels[i] + " 新加密失败: " + e.getMessage());
            }
            System.out.println();
        }
        
        System.out.println("=== 测试完成 ===");
    }
}