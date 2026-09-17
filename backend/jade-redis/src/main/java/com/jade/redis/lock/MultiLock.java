package com.jade.redis.lock;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 联锁（MultiLock）
 *
 * 一次锁多个资源，要么全拿到，要么全释放。
 *
 * 关键防死锁策略：
 *   - 锁 keys 排序后逐个拿（避免循环依赖）
 *   - 任一失败立刻释放已拿到的
 *
 * 适用场景：
 *   - 转账：扣 A 账户 + 加 B 账户
 *   - 库存 + 优惠券：两个资源要同时锁
 *   - 订单 + 支付：必须同时操作
 *   - 分布式事务的"准备阶段"
 */
@ApplicationScoped
public class MultiLock {

    private static final Logger LOG = Logger.getLogger(MultiLock.class);

    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    @Inject
    RedisDataSource redis;

    private String lockKey(String key) { return "lock:" + key; }

    /**
     * 拿多把锁（全部成功才返回）
     *
     * @return token 或 null（任一失败）
     */
    public String tryLock(List<String> keys, Duration ttl) {
        if (keys == null || keys.isEmpty()) return null;

        // 关键：排序后拿锁（防死锁）
        List<String> sortedKeys = new ArrayList<>(keys);
        sortedKeys.sort(Comparator.naturalOrder());

        String token = UUID.randomUUID().toString();
        ValueCommands<String, String> cmd = redis.value(String.class, String.class);
        SetArgs args = new SetArgs().nx().ex(ttl.toSeconds());

        List<String> acquired = new ArrayList<>();
        try {
            for (String key : sortedKeys) {
                String prev = cmd.setGet(lockKey(key), token, args);
                if (prev != null) {
                    LOG.debugf("[MultiLock] failed at key=%s, releasing %d acquired",
                            key, acquired.size());
                    releaseAcquired(acquired, token);
                    return null;
                }
                acquired.add(key);
            }
            LOG.debugf("[MultiLock] acquired all %d keys, token=%s", acquired.size(), token);
            return token;
        } catch (Exception e) {
            LOG.warnf(e, "[MultiLock] error during acquire");
            releaseAcquired(acquired, token);
            return null;
        }
    }

    /**
     * 释放已拿到的锁（失败时回滚用）
     */
    private void releaseAcquired(List<String> keys, String token) {
        for (String key : keys) {
            try {
                redis.execute("EVAL", UNLOCK_LUA, "1", lockKey(key), token);
            } catch (Exception e) {
                LOG.warnf(e, "[MultiLock] release error key=%s", key);
            }
        }
    }

    /**
     * 释放联锁
     */
    public boolean unlock(List<String> keys, String token) {
        if (token == null) return false;
        // 注意：按拿锁的逆序释放（习惯）
        List<String> sortedKeys = new ArrayList<>(keys);
        sortedKeys.sort(Comparator.reverseOrder());

        boolean allOk = true;
        for (String key : sortedKeys) {
            try {
                redis.execute("EVAL", UNLOCK_LUA, "1", lockKey(key), token);
            } catch (Exception e) {
                LOG.warnf(e, "[MultiLock] unlock error key=%s", key);
                allOk = false;
            }
        }
        return allOk;
    }

    /**
     * 联锁 + 自动执行
     */
    public <T> T execute(List<String> keys, Duration ttl, Supplier<T> action) {
        String token = tryLock(keys, ttl);
        if (token == null) {
            throw new RedisLock.LockFailedException("联锁失败: " + keys);
        }
        try {
            return action.get();
        } finally {
            unlock(keys, token);
        }
    }
}
