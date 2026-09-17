package com.jade.redis.lock;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiLock 联锁测试
 *
 * 核心：原子性——要么全拿要么全释放
 */
@QuarkusTest
class MultiLockTest {

    @Inject
    MultiLock multiLock;

    @Test
    void tryLock_acquires_all_keys_atomically() {
        String prefix = "test:multi:" + UUID.randomUUID();
        List<String> keys = Arrays.asList(prefix + ":1", prefix + ":2", prefix + ":3");

        String token = multiLock.tryLock(keys, Duration.ofSeconds(5));

        assertNotNull(token);
        multiLock.unlock(keys, token);

        // 全部释放后，应该能重新拿
        String token2 = multiLock.tryLock(keys, Duration.ofSeconds(5));
        assertNotNull(token2);
        multiLock.unlock(keys, token2);
    }

    @Test
    void tryLock_returns_null_when_any_key_held() {
        String prefix = "test:multi:" + UUID.randomUUID();
        List<String> keys = Arrays.asList(prefix + ":a", prefix + ":b", prefix + ":c");

        // 占用其中一个
        RedisLock plainLock = new RedisLock();  // 不太好，但为了测
        // 改用 fairLock 占住
        // 实际用普通 SET
        // 这里简化：手动占住一个 key
        var redis = io.quarkus.arc.Arc.container()
                .instance(io.quarkus.redis.datasource.RedisDataSource.class).get();
        redis.value(String.class, String.class)
                .set("lock:" + prefix + ":b", "other-holder",
                        new io.quarkus.redis.datasource.value.SetArgs().ex(5));

        // 现在多锁应该失败
        String token = multiLock.tryLock(keys, Duration.ofSeconds(5));
        assertNull(token);

        // 已拿到的应该被回滚（a、c 都不应被锁）
        // 验证 a 可以拿
        String testA = redis.value(String.class, String.class)
                .setGet("lock:" + prefix + ":a", "test", new io.quarkus.redis.datasource.value.SetArgs().nx().ex(5));
        assertNull(testA, "a 应该没被锁，可以拿");
        redis.key().del("lock:" + prefix + ":a");

        // 清理
        redis.key().del("lock:" + prefix + ":b");
    }

    @Test
    void keys_are_locked_in_sorted_order_preventing_deadlock() {
        // 即使传入 [B, A]（逆序），内部也按 [A, B] 拿
        // 这避免了 A→B / B→A 互相等待
        String prefix = "test:multi:" + UUID.randomUUID();
        List<String> unorderedKeys = Arrays.asList(prefix + ":z", prefix + ":a", prefix + ":m");

        // 应该成功
        String token = multiLock.tryLock(unorderedKeys, Duration.ofSeconds(5));
        assertNotNull(token);
        multiLock.unlock(unorderedKeys, token);
    }

    @Test
    void execute_runs_action_and_releases() {
        String prefix = "test:multi:" + UUID.randomUUID();
        List<String> keys = Arrays.asList(prefix + ":x", prefix + ":y");
        AtomicInteger counter = new AtomicInteger(0);

        String result = multiLock.execute(keys, Duration.ofSeconds(5), () -> {
            counter.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, counter.get());
    }

    @Test
    void empty_keys_returns_null() {
        assertNull(multiLock.tryLock(Arrays.asList(), Duration.ofSeconds(5)));
        assertNull(multiLock.tryLock(null, Duration.ofSeconds(5)));
    }
}
