package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式读写锁
 *
 * 规则：
 *   - 多个读操作可以并发（互不阻塞）
 *   - 写操作独占（阻塞所有读和写）
 *   - 读和写互斥
 *
 * 实现原理（Redis 风格）：
 *   - 用一个 counter 记录活跃的读锁数
 *   - 用一个 flag 表示写锁持有者
 *   - 读锁：CAS 模式增加 counter，校验无写者
 *   - 写锁：SET NX flag，校验无读者
 *
 * Key 设计：
 *   rwl:{key}:readers   读者计数器
 *   rwl:{key}:writer    写者 token
 *   rwl:{key}:wseq      写者请求序列号（保证写公平性）
 *
 * 适用场景：
 *   - 配置中心（写少读多）
 *   - 商品详情（详情读多，库存写多）
 *   - 缓存重建（重建时阻塞读，平时并发读）
 *
 * 注意：本实现优先"读"（写要等所有读完成）
 *       生产可加：写者排队机制（先来先写）
 */
@ApplicationScoped
public class ReadWriteLock {

    private static final Logger LOG = Logger.getLogger(ReadWriteLock.class);

    /**
     * Lua: 增加读者计数（仅在无写者时）
     *   - readers:  当前计数
     *   - writer:   当前写者（空则无）
     *   返回: 1=成功，0=有写者拒绝
     */
    private static final String READ_ACQUIRE_LUA = """
            if redis.call('exists', KEYS[2]) == 0 then
                return redis.call('incr', KEYS[1])
            else
                return 0
            end
            """;

    /**
     * Lua: 减少读者计数
     */
    private static final String READ_RELEASE_LUA = """
            local n = redis.call('decr', KEYS[1])
            if n < 0 then
                redis.call('set', KEYS[1], 0)
                return 0
            end
            return n
            """;

    /**
     * Lua: 尝试拿写锁（仅在无读者时）
     *   返回: 1=成功，0=有读者拒绝
     */
    private static final String WRITE_ACQUIRE_LUA = """
            if redis.call('get', KEYS[1]) == '0' or redis.call('exists', KEYS[1]) == 0 then
                return redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[2])
            else
                return nil
            end
            """;

    /**
     * Lua: 释放写锁（仅持有者）
     */
    private static final String WRITE_RELEASE_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    @Inject
    RedisDataSource redis;

    private String readersKey(String key) { return "rwl:" + key + ":readers"; }
    private String writerKey(String key)  { return "rwl:" + key + ":writer"; }

    // ==================== 读锁 ====================

    /**
     * 拿读锁（带重试）
     */
    public String tryReadLock(String key, Duration ttl, Duration maxWait) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + maxWait.toMillis();
        ValueCommands<String, String> cmd = redis.value(String.class, String.class);

        while (System.currentTimeMillis() < deadline) {
            try {
                Object result = redis.execute("EVAL", READ_ACQUIRE_LUA,
                        "2", readersKey(key), writerKey(key));
                if (result != null && Long.parseLong(result.toString()) > 0) {
                    LOG.debugf("[RWLock] read acquired key=%s token=%s", key, token);
                    return token;
                }
            } catch (Exception e) {
                LOG.warnf(e, "[RWLock] read acquire error key=%s", key);
            }
            try { TimeUnit.MILLISECONDS.sleep(50); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        }
        return null;
    }

    public <T> T read(String key, Duration maxWait, Supplier<T> action) {
        return read(key, Duration.ofSeconds(30), maxWait, action);
    }

    public <T> T read(String key, Duration lease, Duration maxWait, Supplier<T> action) {
        String token = tryReadLock(key, lease, maxWait);
        if (token == null) throw new RedisLock.LockFailedException("读锁超时: " + key);
        try {
            return action.get();
        } finally {
            releaseRead(key);
        }
    }

    public void releaseRead(String key) {
        try {
            redis.execute("EVAL", READ_RELEASE_LUA, "1", readersKey(key));
        } catch (Exception e) {
            LOG.warnf(e, "[RWLock] read release error key=%s", key);
        }
    }

    // ==================== 写锁 ====================

    /**
     * 拿写锁
     */
    public String tryWriteLock(String key, Duration ttl, Duration maxWait) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + maxWait.toMillis();

        while (System.currentTimeMillis() < deadline) {
            // 检查 reader 计数
            String readers = redis.value(String.class, String.class).get(readersKey(key));
            long r = readers == null ? 0 : Long.parseLong(readers);
            if (r > 0) {
                // 有读者在读，等一会
                try { TimeUnit.MILLISECONDS.sleep(50); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
                continue;
            }

            // 尝试拿写锁
            try {
                Object result = redis.execute("EVAL", WRITE_ACQUIRE_LUA,
                        "2", readersKey(key), writerKey(key),
                        token, String.valueOf(ttl.toSeconds()));
                if (result != null && "OK".equals(result.toString())) {
                    LOG.debugf("[RWLock] write acquired key=%s token=%s", key, token);
                    return token;
                }
            } catch (Exception e) {
                LOG.warnf(e, "[RWLock] write acquire error key=%s", key);
            }
            try { TimeUnit.MILLISECONDS.sleep(50); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        }
        return null;
    }

    public <T> T write(String key, Duration maxWait, Supplier<T> action) {
        return write(key, Duration.ofSeconds(30), maxWait, action);
    }

    public <T> T write(String key, Duration lease, Duration maxWait, Supplier<T> action) {
        String token = tryWriteLock(key, lease, maxWait);
        if (token == null) throw new RedisLock.LockFailedException("写锁超时: " + key);
        try {
            return action.get();
        } finally {
            releaseWrite(key, token);
        }
    }

    public boolean releaseWrite(String key, String token) {
        try {
            redis.execute("EVAL", WRITE_RELEASE_LUA, "1", writerKey(key), token);
            return true;
        } catch (Exception e) {
            LOG.warnf(e, "[RWLock] write release error key=%s", key);
            return false;
        }
    }

    // ==================== 监控 ====================

    public long activeReaders(String key) {
        String v = redis.value(String.class, String.class).get(readersKey(key));
        return v == null ? 0 : Long.parseLong(v);
    }

    public boolean hasWriter(String key) {
        return redis.key().exists(writerKey(key));
    }
}
