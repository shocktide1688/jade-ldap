package com.jade.demo.controller;

import com.jade.common.api.R;
import com.jade.redis.lock.*;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式锁演示（4 种高级锁）
 *
 *   - 公平锁
 *   - 读写锁
 *   - 联锁
 *   - 信号量
 *   - 闭锁
 */
@Path("/api/v1/lock-demo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "分布式锁演示")
@PermitAll
public class LockDemoController {

    @Inject
    FairLock fairLock;

    @Inject
    ReadWriteLock rwLock;

    @Inject
    MultiLock multiLock;

    @Inject
    DistributedSemaphore semaphore;

    @Inject
    DistributedCountDownLatch latch;

    // ==================== 1. 公平锁 ====================

    @POST
    @Path("/fair/{key}")
    @Operation(summary = "公平锁（FIFO）— 演示：模拟排队买票")
    public R<String> fairLock(@PathParam("key") String key,
                               @QueryParam("holdMs") @DefaultValue("500") int holdMs) {
        long start = System.currentTimeMillis();
        return fairLock.execute(key, Duration.ofSeconds(5), Duration.ofSeconds(10), () -> {
            long waited = System.currentTimeMillis() - start;
            try { Thread.sleep(holdMs); } catch (InterruptedException ignored) {}
            return R.ok(String.format("key=%s 排队等位 %dms，持锁 %dms，队尾：%d",
                    key, waited, holdMs, fairLock.queueSize(key)));
        });
    }

    // ==================== 2. 读写锁 ====================

    @POST
    @Path("/rw/read/{key}")
    @Operation(summary = "读锁 — 多个读者可并发")
    public R<String> readLock(@PathParam("key") String key,
                               @QueryParam("holdMs") @DefaultValue("300") int holdMs) {
        long start = System.currentTimeMillis();
        return rwLock.read(key, Duration.ofSeconds(5), () -> {
            try { Thread.sleep(holdMs); } catch (InterruptedException ignored) {}
            return R.ok(String.format("READ key=%s (activeReaders=%d, hasWriter=%s, took %dms)",
                    key, rwLock.activeReaders(key), rwLock.hasWriter(key),
                    System.currentTimeMillis() - start));
        });
    }

    @POST
    @Path("/rw/write/{key}")
    @Operation(summary = "写锁 — 独占，阻塞所有读和写")
    public R<String> writeLock(@PathParam("key") String key,
                                @QueryParam("holdMs") @DefaultValue("500") int holdMs) {
        long start = System.currentTimeMillis();
        return rwLock.write(key, Duration.ofSeconds(5), () -> {
            try { Thread.sleep(holdMs); } catch (InterruptedException ignored) {}
            return R.ok(String.format("WRITE key=%s (activeReaders=%d, took %dms)",
                    key, rwLock.activeReaders(key), System.currentTimeMillis() - start));
        });
    }

    // ==================== 3. 联锁 ====================

    @POST
    @Path("/multi")
    @Operation(summary = "联锁 — 同时锁多个资源（模拟转账：A 账户 + B 账户）")
    public R<String> multiLock() {
        List<String> accounts = Arrays.asList("account:A", "account:B");
        return multiLock.execute(accounts, Duration.ofSeconds(5), () -> {
            // 模拟 A 扣 100，B 加 100
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            return R.ok("联锁成功：A 账户 + B 账户原子锁定");
        });
    }

    // ==================== 4. 信号量 ====================

    private final AtomicInteger apiCallCount = new AtomicInteger(0);

    @POST
    @Path("/sem/init")
    @Operation(summary = "初始化信号量：最多 3 个并发")
    public R<String> semInit() {
        semaphore.create("api:limited", 3);
        apiCallCount.set(0);
        return R.ok("信号量 'api:limited' 创建，最大 3 并发");
    }

    @GET
    @Path("/sem/available")
    @Operation(summary = "查询当前可用许可数")
    public R<Integer> semAvailable() {
        return R.ok(semaphore.available("api:limited"));
    }

    @POST
    @Path("/sem/call")
    @Operation(summary = "调用受限 API（最多 3 个并发）")
    public R<String> semCall(@QueryParam("holdMs") @DefaultValue("1000") int holdMs) {
        return semaphore.execute("api:limited", 1, Duration.ofSeconds(3), () -> {
            int n = apiCallCount.incrementAndGet();
            try { Thread.sleep(holdMs); } catch (InterruptedException ignored) {}
            return R.ok(String.format("call#%d 拿到许可，可用=%d", n, semaphore.available("api:limited")));
        });
    }

    // ==================== 5. 闭锁 ====================

    @POST
    @Path("/latch/init")
    @Operation(summary = "初始化闭锁：等 3 个 worker 就绪")
    public R<String> latchInit() {
        latch.create("batch:start", 3);
        return R.ok("闭锁 'batch:start' 创建，count=3");
    }

    @POST
    @Path("/latch/ready")
    @Operation(summary = "Worker 报到（countDown）")
    public R<Long> latchReady() {
        return R.ok(latch.countDown("batch:start"));
    }

    @GET
    @Path("/latch/await")
    @Operation(summary = "主任务等齐（await，最多等 5 秒）")
    public R<String> latchAwait() {
        boolean ok = latch.await("batch:start", Duration.ofSeconds(5));
        return R.ok(ok ? "✅ 全部就绪，开始执行" : "❌ 超时，仍有 worker 未到（count=" + latch.getCount("batch:start") + "）");
    }
}
