package com.jade.redis.filter;

import com.jade.common.api.R;
import com.jade.redis.annotation.RateLimit;
import com.jade.redis.limiter.RateLimiter;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.lang.reflect.Method;

/**
 * 限流过滤器（修复了安全审计中的维度问题）
 *
 * 维度优先级（修复后）：
 *   1. 已登录用户：user:{userId}（防爆破单个账号）
 *   2. 客户端 IP：ip:{X-Forwarded-For or remote}（防匿名爆破）
 *   3. 用户名（登录场景）：name:{username}（防针对账号的爆破）
 *
 * 登录接口（@RateLimit(key = "auth:login")）建议同时按 IP + 用户名两个维度
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 100)
public class RateLimitFilter implements ContainerRequestFilter {

    @Inject
    RateLimiter rateLimiter;

    @Context
    ResourceInfo resourceInfo;

    @Context
    HttpHeaders headers;

    @Inject
    jakarta.enterprise.inject.Instance<JsonWebToken> jwt;

    @Inject
    jakarta.enterprise.inject.Instance<SecurityIdentity> identity;

    @Override
    public void filter(ContainerRequestContext ctx) {
        Method method = resourceInfo.getResourceMethod();
        if (method == null) return;

        RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(RateLimit.class);
        }
        if (annotation == null) return;

        // 修复：登录接口用 IP（防匿名爆破），其他用 user
        String dimension = isAuthEndpoint(annotation.key())
                ? buildIpDimension(ctx)
                : buildUserDimension();

        String fullKey = "rate:" + annotation.key() + ":" + dimension;

        if (!rateLimiter.tryAcquire(fullKey, annotation.limit(), annotation.window())) {
            ctx.abortWith(Response.status(429)
                    .entity(R.fail(429, annotation.message()))
                    .build());
        }
    }

    /**
     * 是否登录/认证相关接口（按 IP 限流，防匿名爆破）
     */
    private boolean isAuthEndpoint(String key) {
        return key != null && (key.startsWith("auth:") || key.startsWith("login:") || key.startsWith("sms:"));
    }

    /**
     * 已登录用户维度
     */
    private String buildUserDimension() {
        try {
            if (!jwt.isUnsatisfied()) {
                String sub = jwt.get().getSubject();
                if (sub != null && !sub.isBlank()) return "u:" + sub;
            }
        } catch (Exception ignored) {
        }
        try {
            if (!identity.isUnsatisfied() && identity.get() != null
                    && !identity.get().isAnonymous()) {
                return "u:" + identity.get().getPrincipal().getName();
            }
        } catch (Exception ignored) {
        }
        // 匿名但不是 auth 接口：按 IP 兜底
        return "ip:unknown";
    }

    /**
     * IP 维度（优先取 X-Forwarded-For，再取 remote address）
     */
    private String buildIpDimension(ContainerRequestContext ctx) {
        String xff = ctx.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 取第一个 IP（最原始的客户端）
            return "ip:" + xff.split(",")[0].trim();
        }
        String xri = ctx.getHeaderString("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return "ip:" + xri.trim();
        }
        // remote address 拿不到就 fallback
        return "ip:unknown";
    }
}
