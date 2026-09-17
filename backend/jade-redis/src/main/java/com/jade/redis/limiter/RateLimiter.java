package com.jade.redis.limiter;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;

/**
 * 限流器（基于 Redis 固定窗口计数）
 *
 * 策略：每个 key 维护一个递增计数器，TTL = window
 *      超限则返回 false
 *
 * 高并发场景建议用 Lua 脚本保证原子性（生产可换）
 */
@ApplicationScoped
public class RateLimiter {

    private static final Logger LOG = Logger.getLogger(RateLimiter.class);

    @Inject
    RedisDataSource redis;

    /**
     * 检查是否允许通过
     *
     * @param key    完整 key（含业务前缀 + 维度，如 "rate:login:192.168.1.1"）
     * @param limit  窗口内最大次数
     * @param window 窗口秒数
     * @return true=放行；false=超限
     */
    public boolean tryAcquire(String key, int limit, int window) {
        ValueCommands<String, String> cmd = redis.value(String.class, String.class);
        String fullKey = "rate:" + key;

        try {
            String countStr = cmd.get(fullKey);
            long count = countStr == null ? 0 : Long.parseLong(countStr);

            if (count >= limit) {
                LOG.debugf("[RateLimit] blocked key=%s count=%d limit=%d", fullKey, count, limit);
                return false;
            }

            if (count == 0) {
                cmd.setex(fullKey, window, "1");
            } else {
                cmd.incr(fullKey);
            }
            return true;
        } catch (Exception e) {
            // Redis 故障：fail-open 放行，避免影响业务
            LOG.warnf(e, "[RateLimit] Redis error, fail-open key=%s", fullKey);
            return true;
        }
    }
}
