package com.jade.redis.id;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** 41 位时间戳 + 5 位 workerId + 12 位毫秒序列。 */
@ApplicationScoped
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1704067200000L;
    private static final long SEQUENCE_MASK = 4095L;
    private static final long WORKER_SHIFT = 12L;
    private static final long TIMESTAMP_SHIFT = 17L;

    @Inject WorkerIdAllocator workers;
    private long lastTimestamp = -1L;
    private long sequence;

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTimestamp) throw new IllegalStateException("系统时钟回拨，拒绝生成 ID");
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                do { now = System.currentTimeMillis(); } while (now <= lastTimestamp);
            }
        } else sequence = 0;
        lastTimestamp = now;
        return ((now - EPOCH) << TIMESTAMP_SHIFT) | ((long) workers.workerId() << WORKER_SHIFT) | sequence;
    }
}
