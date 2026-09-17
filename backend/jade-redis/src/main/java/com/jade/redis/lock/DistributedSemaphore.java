package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式信号量
 *
 * 用途：限制对某资源的并发访问数（不是锁，是"最多 N 个"）
 *
 * vs 锁：
 *   - 锁：1 个能进，其他全等
 *   - 信号量：N 个能进，超出的等
 *
 * 实现：Redis 计数器 + Lua 原子操作
 *
 * Key 设计：
 *   sem:{name}         当前可用许可数
 *   sem:{name}:max     最大许可数（创建时设定）
 *
 * 适用场景：
 *   - 接口限流（每秒最多 100 个请求）
 *   - 资源池（最多 10 个连接）
 *   - 流量削峰
 */
@ApplicationScoped
public class DistributedSemaphore {

    private static final Logger LOG = Logger.getLogger(DistributedSemaphore.class);

    /**
     * Lua: 原子获取 N 个许可
     *   如果当前 >= N 则减去 N 并返回 1，否则返回 0
     */
    private static final String ACQUIRE_LUA = """
            local current = tonumber(redis.call('get', KEYS[1]) or '0')
            local need = tonumber(ARGV[1])
            if current >= need then
                redis.call('decrby', KEYS[1], need)
                return 1
            else
                return 0
            end
            """;

    /**
     * Lua: 释放 N 个许可（不超过 max）
     */
    private static final String RELEASE_LUA = """
            local current = tonumber(redis.call('get', KEYS[1]) or '0')
            local max = tonumber(redis.call('get', KEYS[2]) or '0')
            local give = tonumber(ARGV[1])
            if current + give <= max then
                return redis.call('incrby', KEYS[1], give)
            else
                redis.call('set', KEYS[1], max)
                return max
            end
            """;

    @Inject
    RedisDataSource redis;

    private String semKey(String name)   { return "sem:" + name; }
    private String maxKey(String name)   { return "sem:" + name + ":max"; }

    /**
     * 创建信号量
     */
    public void create(String name, int maxPermits) {
        redis.value(String.class, String.class).set(semKey(name), String.valueOf(maxPermits));
        redis.value(String.class, String.class).set(maxKey(name), String.valueOf(maxPermits));
    }

    /**
     * 尝试获取许可（带重试）
     *
     * @return true=拿到；false=超时
     */
    public boolean tryAcquire(String name, int permits, Duration maxWait) {
        long deadline = System.currentTimeMillis() + maxWait.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                Object result = redis.execute("EVAL", ACQUIRE_LUA,
                        "1", semKey(name), String.valueOf(permits));
                if (result != null && "1".equals(result.toString())) {
                    return true;
                }
            } catch (Exception e) {
                LOG.warnf(e, "[Semaphore] acquire error name=%s", name);
            }
            try { TimeUnit.MILLISECONDS.sleep(50); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    /**
     * 释放许可
     */
    public void release(String name, int permits) {
        try {
            redis.execute("EVAL", RELEASE_LUA, "2", semKey(name), maxKey(name),
                    String.valueOf(permits));
        } catch (Exception e) {
            LOG.warnf(e, "[Semaphore] release error name=%s", name);
        }
    }

    /**
     * 用信号量保护一段代码
     */
    public <T> T execute(String name, int permits, Duration maxWait, Supplier<T> action) {
        if (!tryAcquire(name, permits, maxWait)) {
            throw new RedisLock.LockFailedException("信号量获取失败: " + name);
        }
        try {
            return action.get();
        } finally {
            release(name, permits);
        }
    }

    /**
     * 当前可用许可数
     */
    public int available(String name) {
        try {
            String v = redis.value(String.class, String.class).get(semKey(name));
            return v == null ? 0 : Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }
}
