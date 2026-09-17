package com.jade.spring.annotation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring @RequestMapping 的 Jade 等价注解（简化版）
 *
 * 注意：Quarkus 用 JAX-RS，更精细的注解是 @GET / @POST / @Path
 *       此注解主要为了让 Spring 代码能 import 通过（不会生效）
 *
 * 实际使用推荐：
 *   @GET @Path("/users")
 *   public List<User> list() { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequestMapping {
    String value() default "";
    RequestMethod[] method() default {};
}
