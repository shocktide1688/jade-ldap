package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 公平锁（FIFO 队列）
 *
 * 解决了什么：
 *   - 普通分布式锁是"非公平"的——后到的请求可能插队拿到锁
 *   - 公平锁保证按请求顺序排队，先到先得
 *
 * 实现原理（Redisson 风格）：
 *   - 用 ZSET 维护等待队列
 *   - 每个请求拿一个 sequence number 入队
 *   - 不断检查自己是不是队首，是的话才尝试拿锁
 *   - 拿锁失败继续轮询（不丢人）
 *
 * Key 设计：
 *   lock:{key}             当前持有者 token
 *   lock:{key}:sequence    全局递增序列号
 *   lock:{key}:waiters     ZSET：score=sequence, member=token
 *
 * 适用场景：
 *   - 抢购活动（公平性很重要，避免用户投诉"我先点的怎么没抢到"）
 *   - 排队叫号系统
 *   - 任务调度（保证按提交顺序执行）
 */
@ApplicationScoped
public class FairLock {

    private static final Logger LOG = Logger.getLogger(FairLock.class);

    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    @Inject
    RedisDataSource redis;

    private String lockKey(String key)   { return "lock:" + key; }
    private String seqKey(String key)    { return "lock:" + key + ":sequence"; }
    private String waitKey(String key)   { return "lock:" + key + ":waiters"; }

    /**
     * 公平拿锁
     *
     * @param key      业务 key
     * @param ttl      锁 lease
     * @param maxWait  最多等多久（拿不到就返回 null）
     * @return token 或 null（超时）
     */
    public String tryLock(String key, Duration ttl, Duration maxWait) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + maxWait.toMillis();

        // 1) 入队（拿 sequence）
        ValueCommands<String, String> valCmd = redis.value(String.class, String.class);
        long sequence = valCmd.incr(seqKey(key));
        SortedSetCommands<String, String> zsetCmd = redis.sortedSet(String.class, String.class);
        zsetCmd.zadd(waitKey(key), sequence, token);
        LOG.debugf("[FairLock] enqueue key=%s token=%s seq=%d", key, token, sequence);

        try {
            while (System.currentTimeMillis() < deadline) {
                // 2) 检查自己是不是队首
                var firstSet = zsetCmd.zrange(waitKey(key), 0, 0);
                String first = (firstSet == null || firstSet.isEmpty()) ? null
                        : firstSet.iterator().next();
                boolean isHead = token.equals(first);

                if (isHead) {
                    // 3) 队首才能尝试拿锁
                    String prev = valCmd.setGet(lockKey(key), token,
                            new SetArgs().nx().ex(ttl.toSeconds()));
                    if (prev == null) {
                        LOG.debugf("[FairLock] acquired key=%s token=%s", key, token);
                        return token;
                    }
                }

                // 4) 轮询间隔
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            LOG.debugf("[FairLock] timeout key=%s token=%s", key, token);
            return null;
        } finally {
            // 5) 无论结果，从队列移除
            zsetCmd.zrem(waitKey(key), token);
        }
    }

    /**
     * 公平拿锁 + 自动执行 + 自动释放
     */
    public <T> T execute(String key, Duration ttl, Duration maxWait,
                         java.util.function.Supplier<T> action) {
        String token = tryLock(key, ttl, maxWait);
        if (token == null) {
            throw new RedisLock.LockFailedException("公平锁超时: " + key);
        }
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }

    /**
     * 释放公平锁
     */
    public boolean unlock(String key, String token) {
        try {
            redis.execute("EVAL", UNLOCK_LUA, "1", lockKey(key), token);
            return true;
        } catch (Exception e) {
            LOG.warnf(e, "[FairLock] unlock error key=%s", key);
            return false;
        }
    }

    /**
     * 当前等待队列长度
     */
    public long queueSize(String key) {
        try {
            return redis.sortedSet(String.class, String.class).zcard(waitKey(key));
        } catch (Exception e) {
            return 0;
        }
    }
}
