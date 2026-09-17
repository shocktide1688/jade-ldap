package com.jade.redis.idempotent;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * 幂等性存储（Redis SETNX + EX）
 *
 * 状态机：
 *   - PENDING: 请求已接收，未完成
 *   - DONE:    请求已完成，结果已缓存
 */
@ApplicationScoped
public class IdempotentStore {

    private static final Logger LOG = Logger.getLogger(IdempotentStore.class);
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";

    @Inject
    RedisDataSource redis;

    private ValueCommands<String, String> cmd() {
        return redis.value(String.class, String.class);
    }

    /**
     * 尝试锁定 key（SETNX）
     *
     * @return true=首次请求（可执行）；false=重复请求（拒绝或返回缓存）
     */
    public boolean tryLock(String fullKey, int expireSeconds) {
        SetArgs args = new SetArgs().nx().ex(expireSeconds);
        cmd().set(fullKey, STATUS_PENDING, args);
        // SETNX 返回 OK 表示抢锁成功
        // 但 quarkus client 的 set() 没返回值，我们用 GET 反查
        String v = cmd().get(fullKey);
        return v != null && v.equals(STATUS_PENDING);
    }

    /** 标记完成，并缓存结果 */
    public void markDone(String fullKey, String resultJson, int expireSeconds) {
        cmd().setex(fullKey, expireSeconds, "DONE:" + (resultJson == null ? "" : resultJson));
    }

    /** 读取已缓存结果（"DONE:xxx"），未完成则返回 null */
    public String getCachedResult(String fullKey) {
        String v = cmd().get(fullKey);
        if (v == null) return null;
        if (v.startsWith("DONE:")) return v.substring(5);
        return null;
    }

    /** 释放 key（业务失败时调用） */
    public void release(String fullKey) {
        try {
            redis.key().del(fullKey);
        } catch (Exception e) {
            LOG.warnf(e, "[Idempotent] release failed key=%s", fullKey);
        }
    }
}
