package com.jade.admin.service;

import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 TTL KV 存储：Redis 不可用时的降级存储（单机语义：重启归零、多实例不共享）
 *
 * 仅用于认证辅助数据（登录失败计数/验证码答案），量小且有 TTL；
 * 超过容量上限时先清过期项，仍超限则整体清空（计数/验证码失效好过内存溢出）。
 */
class InMemoryTtlStore {

    private static final Logger LOG = Logger.getLogger(InMemoryTtlStore.class);
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(String value, long expireAtMillis) {
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public String get(String key) {
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }
        if (e.expireAtMillis() < System.currentTimeMillis()) {
            store.remove(key);
            return null;
        }
        return e.value();
    }

    public void setex(String key, long ttlSeconds, String value) {
        purgeIfNeeded();
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    /** 自增计数；key 不存在或已过期则从 1 开始并重置 TTL（语义同 Redis INCR + 首次 SETEX） */
    public long increment(String key, long ttlSeconds) {
        purgeIfNeeded();
        long[] result = new long[1];
        store.compute(key, (k, cur) -> {
            long now = System.currentTimeMillis();
            if (cur == null || cur.expireAtMillis() < now) {
                result[0] = 1;
                return new Entry("1", now + ttlSeconds * 1000);
            }
            result[0] = Long.parseLong(cur.value()) + 1;
            return new Entry(Long.toString(result[0]), cur.expireAtMillis());
        });
        return result[0];
    }

    public void del(String key) {
        store.remove(key);
    }

    /** 原子读取并删除（语义同 Redis GETDEL），保证一次性消费不被并发复用 */
    public synchronized String getdel(String key) {
        Entry e = store.remove(key);
        if (e == null) {
            return null;
        }
        if (e.expireAtMillis() < System.currentTimeMillis()) {
            return null;
        }
        return e.value();
    }

    private void purgeIfNeeded() {
        if (store.size() < MAX_ENTRIES) {
            return;
        }
        long now = System.currentTimeMillis();
        store.values().removeIf(e -> e.expireAtMillis() < now);
        if (store.size() >= MAX_ENTRIES) {
            LOG.warnf("[AuthMemoryStore] overflow (%d entries), clearing all", store.size());
            store.clear();
        }
    }
}
