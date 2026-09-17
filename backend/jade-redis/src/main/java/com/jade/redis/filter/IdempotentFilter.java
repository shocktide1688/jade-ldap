package com.jade.redis.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jade.common.api.R;
import com.jade.redis.annotation.Idempotent;
import com.jade.redis.idempotent.IdempotentStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 幂等性过滤器（JAX-RS）
 *
 * 用法：业务方法加 @Idempotent(key="order:create", expire=300)
 *      客户端带 header: X-Idempotency-Key: <uuid>
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 200)
public class IdempotentFilter implements ContainerRequestFilter {

    @Inject
    IdempotentStore store;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Instance<JsonWebToken> jwt;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        Method method = resourceInfo.getResourceMethod();
        if (method == null) return;

        Idempotent annotation = method.getAnnotation(Idempotent.class);
        if (annotation == null) return;

        String idemKey = ctx.getHeaderString(annotation.header());
        if (idemKey == null || idemKey.isBlank()) {
            return;  // 没带 key 放行
        }

        // 修复 3：key 必须包含 user/tenant 前缀，防止跨用户/跨租户串扰
        String userTenantPrefix = resolveUserTenantPrefix(ctx);
        String fullKey = "idem:" + annotation.key() + ":" + userTenantPrefix + ":" + idemKey;

        boolean firstTime = store.tryLock(fullKey, annotation.expire());

        if (!firstTime) {
            String cached = store.getCachedResult(fullKey);
            if (cached != null) {
                ctx.abortWith(Response.ok(cached)
                        .type("application/json")
                        .build());
            } else {
                ctx.abortWith(Response.status(409)
                        .entity(R.fail(409, "请求正在处理中，请勿重复提交"))
                        .build());
            }
        }
        // 首次：放行，资源方法执行后通过 ResponseFilter 缓存结果
        ctx.setProperty("jade.idempotent.key", fullKey);
        ctx.setProperty("jade.idempotent.expire", annotation.expire());
    }

    /**
     * 解析 user/tenant 前缀（未登录用 "anon"，用于登录接口的幂等）
     */
    private String resolveUserTenantPrefix(ContainerRequestContext ctx) {
        try {
            if (!jwt.isUnsatisfied()) {
                String sub = jwt.get().getSubject();
                if (sub != null) return "u:" + sub;
            }
        } catch (Exception ignored) {
        }
        return "anon";
    }

    private Object deserialize(String json, Type type) {
        try {
            if (type instanceof ParameterizedType pt) {
                return objectMapper.readValue(json,
                        objectMapper.getTypeFactory().constructType(pt));
            }
            Class<?> raw = type instanceof Class ? (Class<?>) type : Object.class;
            return objectMapper.readValue(json, raw);
        } catch (Exception e) {
            return json;
        }
    }
}
