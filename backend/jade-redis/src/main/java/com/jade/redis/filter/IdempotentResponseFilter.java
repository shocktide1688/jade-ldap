package com.jade.redis.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jade.redis.idempotent.IdempotentStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * 幂等性响应过滤器
 *
 * 修复点（安全审计后）：
 *   1. 只缓存 2xx 成功响应
 *   2. user/tenant 前缀（由 IdempotentFilter 加）
 *   3. 用 Jackson 序列化整响应（之前用 toString 出错）
 *   4. 不缓存空 body
 */
@Provider
public class IdempotentResponseFilter implements ContainerResponseFilter {

    @Inject
    IdempotentStore store;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object fullKey = request.getProperty("jade.idempotent.key");
        if (fullKey == null) return;

        Integer expire = (Integer) request.getProperty("jade.idempotent.expire");
        if (expire == null) expire = 300;

        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            store.release((String) fullKey);
            return;
        }

        Object entity = response.getEntity();
        if (entity == null) {
            store.release((String) fullKey);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(entity);
            store.markDone((String) fullKey, json, expire);
        } catch (IOException e) {
            // 序列化失败：清理锁，让客户端重试
            store.release((String) fullKey);
        }
    }
}
