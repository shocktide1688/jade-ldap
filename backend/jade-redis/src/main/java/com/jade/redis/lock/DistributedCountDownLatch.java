package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 分布式闭锁（CountDownLatch）
 *
 * 用途：协调多个节点/线程"等齐了再开干"
 *
 * vs Semaphore:
 *   - Semaphore: N 个许可，可以 acquire/release 多次
 *   - CountDownLatch: 一次性，count 到 0 后永久放行
 *
 * 实现：Redis Pub/Sub + 计数器
 *
 * Key 设计：
 *   cdl:{name}      计数器
 *   cdl:{name}:ch   pub/sub 频道（count=0 时发消息）
 *
 * 适用场景：
 *   - 分布式任务"全部 worker 准备好再开始"
 *   - 微服务启动时"等所有依赖服务 ready 再接流量"
 *   - 批量任务"等所有分片完成再汇总"
 */
@ApplicationScoped
public class DistributedCountDownLatch {

    private static final Logger LOG = Logger.getLogger(DistributedCountDownLatch.class);

    @Inject
    RedisDataSource redis;

    private String countKey(String name)  { return "cdl:" + name; }
    private String channelName(String n)  { return "cdl:" + n + ":ch"; }

    /**
     * 创建闭锁（设定初始计数）
     */
    public void create(String name, int count) {
        redis.value(String.class, String.class).set(countKey(name), String.valueOf(count));
    }

    /**
     * 计数减 1，到 0 时通过 pub/sub 通知等待者
     */
    public long countDown(String name) {
        String v = redis.value(String.class, String.class).get(countKey(name));
        if (v == null) return -1;
        long newVal = redis.value(String.class, String.class).decr(countKey(name));
        LOG.debugf("[CountDown] %s count=%d", name, newVal);
        if (newVal <= 0) {
            // 发消息唤醒等待者（用原始命令）
            try {
                redis.execute("PUBLISH", channelName(name), "GO");
            } catch (Exception e) {
                LOG.warnf(e, "[CountDown] publish error name=%s", name);
            }
        }
        return newVal;
    }

    /**
     * 等待计数到 0
     *
     * @return true=等到；false=超时
     */
    public boolean await(String name, Duration timeout) {
        // 先快速检查一次
        String v = redis.value(String.class, String.class).get(countKey(name));
        if (v != null && Long.parseLong(v) <= 0) {
            return true;
        }

        // 用轮询等待（简化实现，Redisson 用 pub/sub）
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            v = redis.value(String.class, String.class).get(countKey(name));
            if (v != null && Long.parseLong(v) <= 0) {
                return true;
            }
            try { TimeUnit.MILLISECONDS.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    /**
     * 当前计数值
     */
    public long getCount(String name) {
        String v = redis.value(String.class, String.class).get(countKey(name));
        return v == null ? -1 : Long.parseLong(v);
    }
}
