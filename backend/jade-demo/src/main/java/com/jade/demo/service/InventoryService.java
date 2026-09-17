package com.jade.demo.service;

import com.jade.common.api.R;
import com.jade.redis.lock.RedisLock;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * 库存服务（演示分布式锁的正确用法）
 *
 * 场景：秒杀 100 件商品，1000 个并发请求同时扣库存
 *       不能超卖（卖超）、不能漏卖（实际有但显示无）
 *
 * 关键点：分布式锁用于"扣减"这个临界区，不是给整个秒杀流程加锁
 */
@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);

    @Inject
    RedisDataSource redis;

    @Inject
    RedisLock redisLock;

    private ValueCommands<String, String> cmd() {
        return redis.value(String.class, String.class);
    }

    /**
     * 扣减库存（核心：用锁保护"读-改-写"原子性）
     *
     * 锁粒度：每个 SKU 一把锁（不是整个库存一个锁）
     *        这样不同 SKU 的请求可以并发
     */
    public R<String> deduct(String sku, int quantity) {
        String stockKey = "stock:" + sku;
        String lockKey = "lock:" + sku;

        // 1) 拿锁（最多等 1 秒）
        String token = redisLock.tryLockWithRetry(lockKey, Duration.ofSeconds(3), 10, 100);
        if (token == null) {
            return R.fail(409, "系统繁忙，请重试");
        }

        try {
            // 2) 临界区开始：读 → 校验 → 写
            String stockStr = cmd().get(stockKey);
            int current = stockStr == null ? 0 : Integer.parseInt(stockStr);

            if (current < quantity) {
                return R.fail(4001, "库存不足");
            }

            // 模拟业务耗时（不模拟的话锁太快释放，看不出锁的作用）
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}

            int newStock = current - quantity;
            cmd().set(stockKey, String.valueOf(newStock));

            LOG.infof("[Inventory] sku=%s deducted=%d remaining=%d", sku, quantity, newStock);
            return R.ok("扣减成功，剩余 " + newStock);
            // 3) 临界区结束
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    /**
     * 初始化库存（管理用）
     */
    public R<String> init(String sku, int initial) {
        cmd().set("stock:" + sku, String.valueOf(initial));
        return R.ok("初始化 " + sku + " = " + initial);
    }

    /**
     * 查询库存
     */
    public R<Integer> get(String sku) {
        String v = cmd().get("stock:" + sku);
        return R.ok(v == null ? 0 : Integer.parseInt(v));
    }
}
