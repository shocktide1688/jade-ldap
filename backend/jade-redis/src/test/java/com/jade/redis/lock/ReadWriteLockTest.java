package com.jade.redis.lock;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReadWriteLock 读写锁测试
 *
 * 核心：
 *   - 多个读者可并发
 *   - 写者独占
 *   - 读 + 写互斥
 */
@QuarkusTest
class ReadWriteLockTest {

    @Inject
    ReadWriteLock rwLock;

    @Test
    void read_allows_multiple_readers() {
        String key = "test:rw:" + UUID.randomUUID();

        rwLock.read(key, Duration.ofSeconds(5), () -> {
            assertEquals(1, rwLock.activeReaders(key));
            return null;
        });

        rwLock.read(key, Duration.ofSeconds(5), () -> {
            assertEquals(1, rwLock.activeReaders(key));  // 前一个已释放
            return null;
        });
    }

    @Test
    void write_blocks_other_writers() throws Exception {
        String key = "test:rw:" + UUID.randomUUID();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // 线程 A 占写锁
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> {
            rwLock.write(key, Duration.ofSeconds(5), Duration.ofSeconds(5), () -> {
                holding.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return null;
            });
        });

        // 等 A 拿到锁
        assertTrue(holding.await(2, TimeUnit.SECONDS));
        assertTrue(rwLock.hasWriter(key));
        assertEquals(0, rwLock.activeReaders(key));

        // 释放 A
        release.countDown();
        a.get(3, TimeUnit.SECONDS);
    }

    @Test
    void read_blocks_while_writer_holds() throws Exception {
        String key = "test:rw:" + UUID.randomUUID();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // 线程 A 拿写锁
        CompletableFuture<Long> a = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            Long took = rwLock.write(key, Duration.ofSeconds(5), Duration.ofSeconds(5), () -> {
                holding.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return System.currentTimeMillis() - start;
            });
            return took == null ? 0L : took;
        });

        assertTrue(holding.await(2, TimeUnit.SECONDS));

        // 线程 B 尝试读（应该等）
        CompletableFuture<Long> b = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            Long took = rwLock.read(key, Duration.ofSeconds(5), Duration.ofSeconds(3), () -> null);
            return took == null ? (System.currentTimeMillis() - start) : took;
        });

        // 等 200ms（B 应该在等）
        Thread.sleep(200);
        assertFalse(b.isDone(), "B 应该在等 A 释放");

        // 释放 A
        release.countDown();
        a.get(3, TimeUnit.SECONDS);

        // B 应该完成
        Long bTime = b.get(3, TimeUnit.SECONDS);
        assertTrue(bTime >= 200, "B 应该等了至少 200ms");
    }

    @Test
    void write_exclusive_activeReaders_zero() {
        String key = "test:rw:" + UUID.randomUUID();

        rwLock.write(key, Duration.ofSeconds(5), Duration.ofSeconds(2), () -> {
            assertTrue(rwLock.hasWriter(key));
            assertEquals(0, rwLock.activeReaders(key));
            return null;
        });
    }

    @Test
    void releaseRead_decrements_counter() {
        String key = "test:rw:" + UUID.randomUUID();
        String token = rwLock.tryReadLock(key, Duration.ofSeconds(5), Duration.ofSeconds(2));
        assertNotNull(token);
        assertEquals(1, rwLock.activeReaders(key));
        rwLock.releaseRead(key);
        assertEquals(0, rwLock.activeReaders(key));
    }
}
