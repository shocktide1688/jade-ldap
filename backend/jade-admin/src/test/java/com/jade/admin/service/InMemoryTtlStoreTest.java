package com.jade.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Redis 降级存储行为（进程内 TTL KV）
 */
class InMemoryTtlStoreTest {

    @Test
    void setex_get_del() {
        InMemoryTtlStore store = new InMemoryTtlStore();
        assertNull(store.get("k"));
        store.setex("k", 60, "v1");
        assertEquals("v1", store.get("k"));
        store.del("k");
        assertNull(store.get("k"));
    }

    @Test
    void get_expired_returns_null() throws InterruptedException {
        InMemoryTtlStore store = new InMemoryTtlStore();
        store.setex("k", 1, "v");
        assertEquals("v", store.get("k"));
        Thread.sleep(1100);
        assertNull(store.get("k"));
    }

    @Test
    void increment_counts_then_resets_after_expire() throws InterruptedException {
        InMemoryTtlStore store = new InMemoryTtlStore();
        assertEquals(1, store.increment("c", 1));
        assertEquals(2, store.increment("c", 1));
        assertEquals(3, store.increment("c", 1));
        // TTL 刷新语义同 Redis（此处不刷新）：过期后从 1 重新计数
        Thread.sleep(1100);
        assertEquals(1, store.increment("c", 1));
    }
}
