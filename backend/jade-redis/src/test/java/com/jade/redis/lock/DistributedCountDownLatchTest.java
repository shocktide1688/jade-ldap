package com.jade.redis.lock;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DistributedCountDownLatch 测试
 */
@QuarkusTest
class DistributedCountDownLatchTest {

    @Inject
    DistributedCountDownLatch latch;

    private String testName() {
        return "test:latch:" + UUID.randomUUID();
    }

    @Test
    void create_sets_initial_count() {
        String name = testName();
        latch.create(name, 5);
        assertEquals(5, latch.getCount(name));
    }

    @Test
    void countDown_decrements() {
        String name = testName();
        latch.create(name, 3);

        assertEquals(2, latch.countDown(name));
        assertEquals(1, latch.countDown(name));
        assertEquals(0, latch.countDown(name));
    }

    @Test
    void await_returns_immediately_when_count_already_zero() {
        String name = testName();
        latch.create(name, 0);

        long start = System.currentTimeMillis();
        boolean ok = latch.await(name, Duration.ofSeconds(5));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(ok);
        assertTrue(elapsed < 100, "应该立即返回");
    }

    @Test
    void await_blocks_until_count_zero() throws Exception {
        String name = testName();
        latch.create(name, 2);

        AtomicBoolean done = new AtomicBoolean(false);
        CompletableFuture<Boolean> waiter = CompletableFuture.supplyAsync(() -> {
            boolean ok = latch.await(name, Duration.ofSeconds(5));
            done.set(ok);
            return ok;
        });

        // 等 200ms，await 应该在等
        Thread.sleep(200);
        assertFalse(waiter.isDone());

        // countDown 一次，仍在等
        latch.countDown(name);
        Thread.sleep(100);
        assertFalse(waiter.isDone());

        // 再 countDown 一次，await 应该返回
        latch.countDown(name);
        Boolean result = waiter.get(2, TimeUnit.SECONDS);
        assertTrue(result);
        assertTrue(done.get());
    }

    @Test
    void await_returns_false_on_timeout() {
        String name = testName();
        latch.create(name, 5);

        long start = System.currentTimeMillis();
        boolean ok = latch.await(name, Duration.ofMillis(200));
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(ok);
        assertEquals(5, latch.getCount(name), "超时后 count 不变");
        assertTrue(elapsed >= 200);
    }

    @Test
    void countDown_below_zero_is_safe() {
        String name = testName();
        latch.create(name, 1);

        latch.countDown(name);  // → 0
        long result = latch.countDown(name);  // → -1（不应抛异常）

        assertTrue(result <= 0);
    }
}
