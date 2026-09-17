package com.jade.redis.lock;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DistributedSemaphore 测试
 */
@QuarkusTest
class DistributedSemaphoreTest {

    @Inject
    DistributedSemaphore semaphore;

    private String testName() {
        return "test:sem:" + UUID.randomUUID();
    }

    @Test
    void create_initializes_max() {
        String name = testName();
        semaphore.create(name, 5);

        assertEquals(5, semaphore.available(name));
    }

    @Test
    void tryAcquire_decrements_available() {
        String name = testName();
        semaphore.create(name, 3);

        assertTrue(semaphore.tryAcquire(name, 1, Duration.ofSeconds(1)));
        assertEquals(2, semaphore.available(name));

        assertTrue(semaphore.tryAcquire(name, 1, Duration.ofSeconds(1)));
        assertEquals(1, semaphore.available(name));
    }

    @Test
    void tryAcquire_blocks_when_exhausted() throws Exception {
        String name = testName();
        semaphore.create(name, 1);

        // 占满
        assertTrue(semaphore.tryAcquire(name, 1, Duration.ofSeconds(1)));
        assertEquals(0, semaphore.available(name));

        // 第二个拿不到（最多等 200ms）
        assertFalse(semaphore.tryAcquire(name, 1, Duration.ofMillis(200)));
    }

    @Test
    void release_increments_available() {
        String name = testName();
        semaphore.create(name, 2);

        semaphore.tryAcquire(name, 1, Duration.ofSeconds(1));
        assertEquals(1, semaphore.available(name));

        semaphore.release(name, 1);
        assertEquals(2, semaphore.available(name));
    }

    @Test
    void release_doesnt_exceed_max() {
        String name = testName();
        semaphore.create(name, 2);

        // 多次 release 不应超过 max
        semaphore.release(name, 1);
        semaphore.release(name, 1);
        semaphore.release(name, 1);
        semaphore.release(name, 1);
        assertEquals(2, semaphore.available(name));
    }

    @Test
    void tryAcquire_multiple_permits_at_once() {
        String name = testName();
        semaphore.create(name, 10);

        assertTrue(semaphore.tryAcquire(name, 3, Duration.ofSeconds(1)));
        assertEquals(7, semaphore.available(name));
    }

    @Test
    void execute_waits_for_permit() throws Exception {
        String name = testName();
        semaphore.create(name, 1);

        // 第一个拿许可
        AtomicInteger ran = new AtomicInteger(0);
        CompletableFuture<Long> first = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            Long took = semaphore.execute(name, 1, Duration.ofSeconds(1), () -> {
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                ran.incrementAndGet();
                return System.currentTimeMillis() - start;
            });
            return took == null ? 0L : took;
        });

        // 第一个跑完后，第二个才能跑
        Thread.sleep(100);
        CompletableFuture<Long> second = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            Long took = semaphore.execute(name, 1, Duration.ofSeconds(2), () -> {
                ran.incrementAndGet();
                return System.currentTimeMillis() - start;
            });
            return took == null ? 0L : took;
        });

        Long firstTime = first.get(3, java.util.concurrent.TimeUnit.SECONDS);
        Long secondTime = second.get(3, java.util.concurrent.TimeUnit.SECONDS);

        // 第二个应该等第一个完成
        assertTrue(secondTime >= 400, "第二个应该等至少 400ms");
        assertEquals(2, ran.get());
    }
}
