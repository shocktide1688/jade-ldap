package com.jade.security.crypto;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（修复了密钥处理风险）
 *
 * 输出格式：Base64( IV(12 bytes) || ciphertext || authTag(16 bytes) )
 *
 * 修复点：
 *   - 主密钥必须是 32 字节（启动时校验，不够直接抛错，不再静默截断）
 *   - 推荐主密钥是 Base64 编码的 32 字节随机数
 *   - 支持 keyVersion（保留接口，先实现单版本）
 */
@ApplicationScoped
public class AesGcmCrypto {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final String ALG = "AES/GCM/NoPadding";

    @ConfigProperty(name = "jade.crypto.master-key")
    String masterKeyConfig;

    private SecretKey cachedKey;

    private SecretKey getKey() {
        if (cachedKey != null) return cachedKey;
        synchronized (this) {
            if (cachedKey != null) return cachedKey;
            cachedKey = parseKey(masterKeyConfig);
            return cachedKey;
        }
    }

    /**
     * 解析密钥（必须是 32 字节 = 256 bit）
     *
     * 支持两种格式：
     *   1. Base64 编码的 32 字节（推荐）
     *   2. 原始字符串（截取前 32 字节，但要求长度 >= 32）
     *
     * 修复：长度不够直接抛错，不再静默截断
     */
    private SecretKey parseKey(String config) {
        if (config == null || config.isBlank()) {
            throw new IllegalStateException("jade.crypto.master-key not configured");
        }

        byte[] raw;
        // 尝试 Base64 解码
        try {
            byte[] decoded = Base64.getDecoder().decode(config);
            if (decoded.length == KEY_LENGTH_BYTES) {
                raw = decoded;
            } else if (decoded.length > KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                    "jade.crypto.master-key Base64 decoded to " + decoded.length +
                    " bytes, expected exactly " + KEY_LENGTH_BYTES);
            } else {
                // 解码成功但太短：用 UTF-8 字节
                raw = config.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            // 不是 Base64，按 UTF-8 字节处理
            raw = config.getBytes(StandardCharsets.UTF_8);
        }

        if (raw.length < KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                "jade.crypto.master-key is " + raw.length + " bytes, need >= " + KEY_LENGTH_BYTES +
                " (推荐：32 字节随机 Base64)");
        }
        if (raw.length > KEY_LENGTH_BYTES) {
            // 修复：长度超过明确抛错，不再静默截断
            throw new IllegalStateException(
                "jade.crypto.master-key is " + raw.length + " bytes, must be exactly " + KEY_LENGTH_BYTES +
                " (use Base64 encoding of a 32-byte random key)");
        }
        return new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] data = Base64.getDecoder().decode(cipherText);
            if (data.length < GCM_IV_LENGTH + 16) {
                throw new IllegalArgumentException("Invalid ciphertext length");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
            byte[] ciphertext = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed", e);
        }
    }
}
