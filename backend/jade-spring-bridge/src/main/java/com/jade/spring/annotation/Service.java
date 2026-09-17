package com.jade.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring @Service 的 Jade 等价注解
 *
 * 仅作"代码语义标记"，**不会**让 Quarkus 识别为 Bean。
 * 实际生效请同时加 @ApplicationScoped（CDI）：
 *
 *   @Service              ← 习惯性标记
 *   @ApplicationScoped    ← 真正生效
 *   public class UserService { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
    String value() default "";
}
