package com.jade.security.annotation;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色鉴权注解（标在 Resource 方法上）
 *
 * 用法：@RequiresRoles({"admin", "manager"})
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresRoles {
    String[] value();
}
