package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 锁看门狗（Redisson 风格）
 *
 * 行为：
 *   - 锁默认 lease = 30s
 *   - 每 10s（lease / 3）自动 PEXPIRE 续期一次
 *   - 续期前检查 token 是否还匹配（防止给别人的锁续期）
 *   - 主动 unlock 时停止续期
 *
 * 解决了什么：
 *   - 业务执行 > lease 时锁过期，被其他线程抢到 → 数据不一致
 *   - 业务需要预估最长执行时间 → 难
 *   - 业务 > lease 报 LockLeaseExpiredException
 *
 * 对比 Redisson：
 *   Redisson 看门狗也是 lease/3 续期，调度用 Netty HashedWheelTimer
 *   Jade 用 JDK ScheduledExecutorService（轻量，Quarkus 友好）
 */
@ApplicationScoped
public class LockRenewer {

    private static final Logger LOG = Logger.getLogger(LockRenewer.class);

    /**
     * Lua：只有持有者才能续期（防给别人的锁续期）
     */
    private static final String RENEW_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            else
                return 0
            end
            """;

    @Inject
    RedisDataSource redis;

    /** 活跃的续期任务 */
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    /** 调度线程池（守护线程，JVM 退出时自动结束） */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            2, r -> {
                Thread t = new Thread(r, "jade-lock-renewer");
                t.setDaemon(true);
                return t;
            });

    /**
     * 启动看门狗
     *
     * 注意：key 是用户传入的"业务 key"（不带 lock: 前缀）
     *      续期时必须用 RedisLock 同样的前缀
     *
     * @param key      业务 key
     * @param token    锁 token
     * @param leaseMs  锁 lease 毫秒（续期时长）
     */
    public void start(String key, String token, long leaseMs) {
        // 每 lease/3 续期一次（最少 1s）
        long renewInterval = Math.max(leaseMs / 3, 1000);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> renew(key, token, leaseMs),
                renewInterval, renewInterval, TimeUnit.MILLISECONDS);

        tasks.put(key, future);
        LOG.infof("[Watchdog] started key=%s lease=%dms renewEvery=%dms", key, leaseMs, renewInterval);
    }

    /**
     * 续期（执行一次）
     */
    private void renew(String key, String token, long leaseMs) {
        try {
            // 用 Lua：只有自己的锁才续期
            // 注意：RedisLock 用 "lock:{key}" 前缀，这里必须保持一致
            String fullKey = "lock:" + key;
            redis.execute("EVAL", RENEW_LUA, "1", fullKey, token, String.valueOf(leaseMs));
            LOG.debugf("[Watchdog] renewed key=%s", key);
        } catch (Exception e) {
            LOG.warnf(e, "[Watchdog] renew failed key=%s (lock may have expired)", key);
            // 续期失败：从调度中移除
            stop(key);
        }
    }

    /**
     * 停止看门狗
     */
    public void stop(String key) {
        ScheduledFuture<?> future = tasks.remove(key);
        if (future != null) {
            future.cancel(false);
            LOG.debugf("[Watchdog] stopped key=%s", key);
        }
    }

    @PreDestroy
    void shutdown() {
        LOG.infof("[Watchdog] shutting down, %d active tasks", tasks.size());
        tasks.values().forEach(f -> f.cancel(false));
        tasks.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 当前活跃锁数量（监控用）
     */
    public int activeCount() {
        return tasks.size();
    }
}
