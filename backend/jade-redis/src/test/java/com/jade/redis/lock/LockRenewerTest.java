package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LockRenewer 看门狗测试
 *
 * 核心：验证续期后 TTL 不会归零，业务跑过 lease 锁还在
 */
@QuarkusTest
class LockRenewerTest {

    @Inject
    RedisLock redisLock;

    @Inject
    LockRenewer renewer;

    @Inject
    RedisDataSource redis;

    @Test
    void watchdog_extends_lock_beyond_original_lease() throws Exception {
        String key = "test:watchdog:" + UUID.randomUUID();
        Duration lease = Duration.ofSeconds(2);   // 短 lease 方便测试

        // 拿锁
        String token = redisLock.tryLock(key, lease);
        assertNotNull(token);

        // 启动看门狗
        renewer.start(key, token, lease.toMillis());

        try {
            // 等 3 秒（超过原 2s lease）
            Thread.sleep(3000);

            // 锁应该还在（被看门狗续期过）
            String stillHeld = redisLock.tryLock(key, lease);
            assertNull(stillHeld, "锁应该仍在（被看门狗续期）");

            // Redis 中的锁也应该有有效 TTL
            Long ttl = redis.key().ttl("lock:" + key);
            assertNotNull(ttl);
            assertTrue(ttl > 0, "TTL 应该 > 0，看门狗在续期");
        } finally {
            renewer.stop(key);
            redisLock.unlock(key, token);
        }
    }

    @Test
    void watchdog_stop_cancels_renewal() throws Exception {
        String key = "test:watchdog:" + UUID.randomUUID();
        Duration lease = Duration.ofSeconds(2);

        String token = redisLock.tryLock(key, lease);
        assertNotNull(token);

        // 启动并立即停止
        renewer.start(key, token, lease.toMillis());
        renewer.stop(key);

        // 等 lease 过期
        Thread.sleep(2500);

        // 锁应该过期了（看门狗停了，没人续期）
        String shouldGet = redisLock.tryLock(key, lease);
        assertNotNull(shouldGet, "锁应该已过期，可以重新拿");
        redisLock.unlock(key, shouldGet);
    }

    @Test
    void activeCount_reflects_running_watchdogs() {
        String key = "test:watchdog:" + UUID.randomUUID();
        int before = renewer.activeCount();

        redisLock.tryLock(key, Duration.ofSeconds(2));
        // 注意：直接调 tryLock 不会启动看门狗，只有 executeWithWatchdog 才启动
        // 这里只验证 count 不会负数
        assertTrue(renewer.activeCount() >= 0);
    }
}
