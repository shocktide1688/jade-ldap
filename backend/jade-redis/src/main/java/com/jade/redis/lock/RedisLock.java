package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁（基于 Redis SET NX EX + Lua 释放 + Redisson 风格看门狗）
 *
 * 四种用法（按场景选）：
 *
 *   1. 简单执行（业务 < 30s，锁有明确时长）
 *      redisLock.execute("order:pay:" + id, Duration.ofSeconds(10), () -> pay(id));
 *
 *   2. 手动控制
 *      String token = redisLock.tryLock(key, ttl);
 *      if (token != null) try { ... } finally { redisLock.unlock(key, token); }
 *
 *   3. 带重试（等待锁释放）
 *      redisLock.tryLockWithRetry(key, ttl, 10, 200);
 *
 *   4. 带看门狗（业务时长不确定，自动续期）
 *      redisLock.executeWithWatchdog("dataExport:20261111", () -> doExport());
 *
 * 实现细节：
 *   - SET key token NX EX ttl
 *   - 释放用 Lua：只有 token 匹配才 DEL
 *   - 看门狗每 lease/3 续期（Lua 保证只续自己的锁）
 */
@ApplicationScoped
public class RedisLock {

    private static final Logger LOG = Logger.getLogger(RedisLock.class);

    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    @Inject
    RedisDataSource redis;

    @Inject
    LockRenewer renewer;

    // 高级锁通过组合暴露
    @Inject
    FairLock fairLock;

    @Inject
    ReadWriteLock readWriteLock;

    @Inject
    MultiLock multiLock;

    @Inject
    DistributedSemaphore semaphore;

    @Inject
    DistributedCountDownLatch countDownLatch;

    public FairLock fairLock()           { return fairLock; }
    public ReadWriteLock readWriteLock() { return readWriteLock; }
    public MultiLock multiLock()         { return multiLock; }
    public DistributedSemaphore semaphore() { return semaphore; }
    public DistributedCountDownLatch countDownLatch() { return countDownLatch; }

    // ==================== 基础：execute / tryLock ====================

    /**
     * 简单执行（拿不到锁抛异常）
     */
    public <T> T execute(String key, Duration ttl, Supplier<T> action) {
        String token = tryLock(key, ttl);
        if (token == null) {
            throw new LockFailedException("锁被占用: " + key);
        }
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }

    /**
     * 尝试拿锁（拿不到返回 null）
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        ValueCommands<String, String> cmd = redis.value(String.class, String.class);
        SetArgs args = new SetArgs().nx().ex(ttl.toSeconds());

        try {
            // 关键：加 "lock:" 前缀，与 unlock 保持一致
            String prev = cmd.setGet("lock:" + key, token, args);
            if (prev == null) {
                LOG.debugf("[Lock] acquired key=%s token=%s ttl=%ds", key, token, ttl.toSeconds());
                return token;
            }
            LOG.debugf("[Lock] failed key=%s held by other", key);
            return null;
        } catch (Exception e) {
            LOG.warnf(e, "[Lock] tryLock error key=%s", key);
            return null;
        }
    }

    /**
     * 释放锁（Lua 保证原子性 + token 匹配）
     *
     * @return true=成功释放（token 匹配）；false=未释放（token 不匹配或异常）
     */
    public boolean unlock(String key, String token) {
        if (token == null) return false;
        try {
            Object result = redis.execute("EVAL", UNLOCK_LUA, "1", "lock:" + key, token);
            // Lua 返回 0（不是持有者）或 1（删除成功）
            // Vert.x Response 包装：转字符串判断（"0" 视为失败，其他视为成功）
            if (result == null) return false;
            String s = result.toString().trim();
            boolean released = !s.equals("0") && !s.equals("null") && !s.isEmpty();
            LOG.debugf("[Lock] unlock key=%s result=[%s] released=%s", key, s, released);
            return released;
        } catch (Exception e) {
            LOG.warnf(e, "[Lock] unlock error key=%s", key);
            return false;
        }
    }

    // ==================== 重试：tryLockWithRetry ====================

    /**
     * 带重试的拿锁
     */
    public String tryLockWithRetry(String key, Duration ttl, int maxRetries, long sleepMs) {
        for (int i = 0; i < maxRetries; i++) {
            String token = tryLock(key, ttl);
            if (token != null) return token;
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    // ==================== Redisson 风格：看门狗 ====================

    /**
     * 默认 lease 30s（Redisson 默认）
     */
    public static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);

    /**
     * 带看门狗的执行（业务时长不确定也能安全用）
     *
     * 行为：
     *   - 拿锁后启动看门狗，每 10s 自动续期到 30s
     *   - 业务执行完（无论成功失败）停止看门狗 + 释放锁
     *   - 业务执行中即使超过 30s 锁也不会过期
     *
     * 用法：
     *   redisLock.executeWithWatchdog("dataExport:20261111", () -> {
     *       // 业务逻辑（可能 1 分钟也可能 10 分钟）
     *       doExport();
     *   });
     */
    public <T> T executeWithWatchdog(String key, Supplier<T> action) {
        return executeWithWatchdog(key, DEFAULT_LEASE, action);
    }

    /**
     * 带看门狗 + 自定义 lease
     *
     * @param lease 看门狗的 lease 时间（每 lease/3 自动续期）
     */
    public <T> T executeWithWatchdog(String key, Duration lease, Supplier<T> action) {
        String token = tryLock(key, lease);
        if (token == null) {
            throw new LockFailedException("锁被占用: " + key);
        }

        // 启动看门狗
        renewer.start(key, token, lease.toMillis());

        try {
            return action.get();
        } finally {
            // 顺序：先停看门狗，再释放锁（避免最后一刻看门狗又续期）
            renewer.stop(key);
            unlock(key, token);
        }
    }

    /**
     * 带看门狗 + 重试
     *
     * 用法：秒杀场景，等锁释放 + 拿到后自动续期
     *   redisLock.tryLockWithWatchdogAndRetry("flash:start:20261111", lease, 60, 500);
     */
    public String tryLockWithWatchdogAndRetry(String key, Duration lease, int maxRetries, long sleepMs) {
        for (int i = 0; i < maxRetries; i++) {
            String token = tryLock(key, lease);
            if (token != null) {
                renewer.start(key, token, lease.toMillis());
                return token;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * 解锁带看门狗的锁（停看门狗 + 释放）
     */
    public boolean unlockWithWatchdog(String key, String token) {
        renewer.stop(key);
        return unlock(key, token);
    }

    /**
     * 当前活跃的看门狗数量（监控/测试用）
     */
    public int activeWatchdogs() {
        return renewer.activeCount();
    }

    // ==================== 异常 ====================

    public static class LockFailedException extends RuntimeException {
        public LockFailedException(String message) {
            super(message);
        }
    }
}
