package com.jade.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring @Autowired 的 Jade 等价注解
 *
 * 仅作"代码语义标记"，**不会**让 Quarkus 注入 bean。
 * 实际生效请同时加 @Inject（CDI）：
 *
 *   @Autowired        ← 习惯性标记
 *   @Inject           ← 真正生效
 *   private UserService userService;
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Autowired {
    boolean required() default true;
}
