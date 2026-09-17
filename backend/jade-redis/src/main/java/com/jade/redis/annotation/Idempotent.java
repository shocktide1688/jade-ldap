package com.jade.redis.annotation;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性拦截器绑定
 *
 * 用法：@Idempotent(key = "order:create", expire = 300)
 *       同一 key 在 expire 秒内只执行一次
 *
 * 工作原理：
 *   1. 客户端请求时带 header: Idempotency-Key: <uuid>
 *   2. 拦截器拿 key 去 Redis 设 SETNX
 *   3. SETNX 成功 → 执行并缓存结果
 *   4. SETNX 失败 → 返回缓存的"上次结果"（保护业务不被重放）
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Idempotent {
    /** 业务标识（key 前缀） */
    String key();

    /** 防重时间窗口（秒） */
    int expire() default 300;

    /** Idempotency-Key 请求头名（默认 X-Idempotency-Key） */
    String header() default "X-Idempotency-Key";
}
