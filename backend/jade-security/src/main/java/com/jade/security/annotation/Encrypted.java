package com.jade.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段加密注解（标在 Entity 字段上）
 *
 * 用法：
 *   @Encrypted
 *   private String idCard;
 *
 *   @Encrypted(prefix = "ENC:")
 *   private String phone;
 *
 * 行为：自动用 AES-256-GCM 加解密，DB 里存的是密文
 * 密钥从配置 jade.crypto.master-key 读取
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Encrypted {
    /** 密文前缀（方便识别"已加密"字段） */
    String prefix() default "ENC:";
}
