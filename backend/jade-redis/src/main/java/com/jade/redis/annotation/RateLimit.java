package com.jade.redis.annotation;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流拦截器绑定
 *
 * 用法：@RateLimit(key = "login", limit = 5, window = 60)
 *       每个 key 在 window 秒内最多 limit 次
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RateLimit {
    /** 限流维度（key 前缀，建议按业务命名：login / sms / order） */
    String key();

    /** 时间窗口（秒） */
    int window() default 60;

    /** 窗口内允许的最大次数 */
    int limit() default 10;

    /** 限流后提示 */
    String message() default "操作过于频繁，请稍后再试";
}
