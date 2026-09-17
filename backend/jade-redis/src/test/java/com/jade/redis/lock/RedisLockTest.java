package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisLock 集成测试（打真实 Redis）
 *
 * 注意：每个测试用 unique key 避免相互干扰
 */
@QuarkusTest
class RedisLockTest {

    @Inject
    RedisLock redisLock;

    @Inject
    RedisDataSource redis;

    private String testKey() {
        return "test:lock:" + UUID.randomUUID();
    }

    @AfterEach
    void cleanup() {
        // 清掉测试中可能遗留的 key
    }

    // ============ tryLock / unlock 基础 ============

    @Test
    void tryLock_acquires_lock_when_free() {
        String key = testKey();

        String token = redisLock.tryLock(key, Duration.ofSeconds(10));

        assertNotNull(token);
        assertTrue(redisLock.unlock(key, token));
    }

    @Test
    void tryLock_returns_null_when_already_held() {
        String key = testKey();

        String token1 = redisLock.tryLock(key, Duration.ofSeconds(10));
        String token2 = redisLock.tryLock(key, Duration.ofSeconds(10));

        assertNotNull(token1);
        assertNull(token2);

        redisLock.unlock(key, token1);
    }

    @Test
    void unlock_with_wrong_token_doesnt_release() {
        String key = testKey();

        String token = redisLock.tryLock(key, Duration.ofSeconds(10));
        assertNotNull(token);

        // 用错的 token 解锁
        boolean released = redisLock.unlock(key, "wrong-token");
        assertFalse(released, "wrong token should not release lock");

        // 锁应该还在，原 token 仍能解锁
        String secondTry = redisLock.tryLock(key, Duration.ofSeconds(10));
        assertNull(secondTry, "锁应该还在（没被 wrong token 释放）");
        redisLock.unlock(key, token);
    }

    // ============ execute ============

    @Test
    void execute_runs_action_when_lock_acquired() {
        String key = testKey();
        AtomicInteger counter = new AtomicInteger(0);

        String result = redisLock.execute(key, Duration.ofSeconds(5), () -> {
            counter.incrementAndGet();
            return "done";
        });

        assertEquals("done", result);
        assertEquals(1, counter.get());
    }

    @Test
    void execute_throws_when_lock_held() {
        String key = testKey();

        // 占住锁
        String holder = redisLock.tryLock(key, Duration.ofSeconds(5));
        assertNotNull(holder);

        // execute 应该抛 LockFailedException
        assertThrows(RedisLock.LockFailedException.class, () -> {
            redisLock.execute(key, Duration.ofSeconds(5), () -> "should not run");
        });

        redisLock.unlock(key, holder);
    }

    @Test
    void execute_releases_lock_even_on_exception() {
        String key = testKey();

        assertThrows(RuntimeException.class, () -> {
            redisLock.execute(key, Duration.ofSeconds(5), () -> {
                throw new RuntimeException("boom");
            });
        });

        // 锁应该被释放（finally 块）
        String token = redisLock.tryLock(key, Duration.ofSeconds(5));
        assertNotNull(token);
        redisLock.unlock(key, token);
    }

    // ============ tryLockWithRetry ============

    @Test
    void tryLockWithRetry_gets_lock_after_release() throws Exception {
        String key = testKey();

        // 占住锁，1 秒后释放
        String holder = redisLock.tryLock(key, Duration.ofSeconds(5));
        assertNotNull(holder);

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            redisLock.unlock(key, holder);
        });

        long start = System.currentTimeMillis();
        String token = redisLock.tryLockWithRetry(key, Duration.ofSeconds(5), 20, 100);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(token);
        assertTrue(elapsed >= 400, "Should have waited for lock release");

        redisLock.unlock(key, token);
    }

    @Test
    void tryLockWithRetry_returns_null_on_timeout() {
        String key = testKey();

        // 占住锁
        String holder = redisLock.tryLock(key, Duration.ofSeconds(5));
        assertNotNull(holder);

        // 最多重试 3 次
        String token = redisLock.tryLockWithRetry(key, Duration.ofSeconds(5), 3, 50);
        assertNull(token);

        redisLock.unlock(key, holder);
    }
}
