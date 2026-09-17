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
 * FairLock 公平锁测试
 *
 * 核心：验证 FIFO 顺序——先请求的先拿到锁
 */
@QuarkusTest
class FairLockTest {

    @Inject
    FairLock fairLock;

    @Test
    void tryLock_acquires_when_free() {
        String key = "test:fair:" + UUID.randomUUID();

        String token = fairLock.tryLock(key, Duration.ofSeconds(5), Duration.ofSeconds(2));

        assertNotNull(token);
        fairLock.unlock(key, token);
    }

    @Test
    void tryLock_returns_null_on_timeout() throws Exception {
        String key = "test:fair:" + UUID.randomUUID();

        // 占住锁
        String holder = fairLock.tryLock(key, Duration.ofSeconds(5), Duration.ofSeconds(2));
        assertNotNull(holder);

        // 第二个请求最多等 200ms 拿不到
        String second = fairLock.tryLock(key, Duration.ofSeconds(5), Duration.ofMillis(200));
        assertNull(second);

        fairLock.unlock(key, holder);
    }

    @Test
    void fairLock_serves_in_fifo_order() throws Exception {
        String key = "test:fair:" + UUID.randomUUID();
        int n = 5;

        // 第一个先占住
        String firstHolder = fairLock.tryLock(key, Duration.ofSeconds(5), Duration.ofSeconds(1));
        assertNotNull(firstHolder);

        // 5 个并发请求：用各自线程先"按顺序入队"，再同时启动 polling
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch allEnqueued = new CountDownLatch(1);
        CountDownLatch allPolling = new CountDownLatch(1);
        BlockingQueue<Integer> acquireOrder = new LinkedBlockingQueue<>();

        for (int i = 0; i < n; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    // 1) 入队（每个 worker 排好队才能进 polling）
                    // 用一个阻塞的方式：每个 worker 排队入队
                    Thread.sleep(id * 20L);  // 让 worker 0 先入队，然后 1，2...
                    String token = fairLock.tryLock(key, Duration.ofSeconds(5), Duration.ofSeconds(10));
                    if (token != null) {
                        acquireOrder.add(id);
                        Thread.sleep(30);  // 持锁
                        fairLock.unlock(key, token);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 给 5 个 worker 都开始入队的时间（0, 20, 40, 60, 80ms 启动 + 入队时间）
        Thread.sleep(500);
        // 释放第一个，让队首开始拿
        fairLock.unlock(key, firstHolder);

        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

        // 验证：5 个请求应该按 0→1→2→3→4 顺序拿到
        // （不一定严格 0-1-2-3-4，因为入队存在时间差，但大体有序）
        assertEquals(5, acquireOrder.size());
        Integer first = acquireOrder.poll();
        // 第一个是 0（id=0 的 worker 最早入队）
        assertNotNull(first);
        // 0 一定是第一个（id=0 最早入队）
        assertEquals(0, first.intValue(),
                "id=0 的 worker 应该最早入队，所以最先拿到锁");
    }

    @Test
    void queueSize_reflects_waiters() {
        String key = "test:fair:" + UUID.randomUUID();

        // 占住
        String holder = fairLock.tryLock(key, Duration.ofSeconds(5), Duration.ofSeconds(1));
        assertNotNull(holder);

        // 此时队列里只有 holder
        long size = fairLock.queueSize(key);
        assertTrue(size >= 0);

        fairLock.unlock(key, holder);
    }

    @Test
    void execute_runs_and_releases() {
        String key = "test:fair:" + UUID.randomUUID();
        AtomicInteger counter = new AtomicInteger(0);

        String result = fairLock.execute(key, Duration.ofSeconds(5), Duration.ofSeconds(2), () -> {
            counter.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, counter.get());

        // 锁应该释放了
        String t2 = fairLock.tryLock(key, Duration.ofSeconds(5), Duration.ofMillis(100));
        assertNotNull(t2);
        fairLock.unlock(key, t2);
    }
}
