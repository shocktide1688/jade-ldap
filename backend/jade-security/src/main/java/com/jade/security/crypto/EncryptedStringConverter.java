package com.jade.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密 String 字段的 JPA AttributeConverter
 *
 * 用法（与 @Encrypted 配合）：
 *   @Encrypted
 *   @Convert(converter = EncryptedStringConverter.class)
 *   private String idCard;
 *
 * 实现说明：JPA AttributeConverter 不是 CDI Bean，@Inject 不生效。
 *           这里直接用 ConfigProvider 读 master key，自包含实现，
 *           不依赖任何外部 bean（保证 converter 在 Hibernate 启动时就能用）。
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger LOG = Logger.getLogger(EncryptedStringConverter.class);
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ALG = "AES/GCM/NoPadding";

    private static SecretKey key() {
        String masterKey = ConfigProvider.getConfig()
                .getOptionalValue("jade.crypto.master-key", String.class)
                .orElseThrow(() -> new IllegalStateException("jade.crypto.master-key not configured"));
        byte[] raw = masterKey.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException("jade.crypto.master-key must be at least 32 bytes");
        }
        byte[] key32 = new byte[32];
        System.arraycopy(raw, 0, key32, 0, 32);
        return new SecretKeySpec(key32, "AES");
    }

    private static String encrypt(String plain) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    private static String decrypt(String cipherText) {
        try {
            byte[] data = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
            byte[] ciphertext = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) return null;
        try {
            return encrypt(plain);
        } catch (Exception e) {
            LOG.errorf(e, "[Encrypted] encrypt failed");
            throw e;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        try {
            return decrypt(dbValue);
        } catch (Exception e) {
            LOG.warnf("[Encrypted] decrypt failed: %s", e.getMessage());
            return dbValue;
        }
    }
}
