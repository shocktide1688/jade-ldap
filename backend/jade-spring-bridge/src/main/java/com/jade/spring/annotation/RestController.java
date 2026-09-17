package com.jade.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring @RestController 的 Jade 等价注解
 *
 * 仅作"代码语义标记"用，**不会**让 Quarkus 自动识别为 Resource。
 * 实际生效请同时加 @Path（Quarkus JAX-RS）：
 *
 *   @RestController          ← 习惯性标记
 *   @Path("/api/v1/users")   ← 真正生效
 *   public class UserController { ... }
 *
 * 优点：让 Spring 老代码 import 能通过，不用批量改 import
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RestController {
    String value() default "";
}
