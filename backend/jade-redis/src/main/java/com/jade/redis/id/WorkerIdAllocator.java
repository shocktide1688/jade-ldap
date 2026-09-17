package com.jade.redis.id;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Redis 租约式 workerId 分配器。租约失效后，实例必须停止生成 ID。 */
@ApplicationScoped
public class WorkerIdAllocator {
    private static final String KEY_PREFIX = "jade:id:worker:";
    private static final String RENEW_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end";

    @Inject RedisDataSource redis;
    @ConfigProperty(name = "jade.id.worker.max", defaultValue = "32") int maxWorkers;
    @ConfigProperty(name = "jade.id.worker.lease-seconds", defaultValue = "30") long leaseSeconds;

    private final String instanceId = UUID.randomUUID().toString();
    private ScheduledExecutorService renewer;
    private String key;
    private int workerId = -1;
    private volatile boolean leaseValid;

    @PostConstruct
    void start() {
        ValueCommands<String, String> values = redis.value(String.class, String.class);
        for (int candidate = 0; candidate < maxWorkers; candidate++) {
            String candidateKey = KEY_PREFIX + candidate;
            String previous = values.setGet(candidateKey, instanceId,
                    new SetArgs().nx().ex(leaseSeconds));
            if (previous == null) {
                workerId = candidate;
                key = candidateKey;
                leaseValid = true;
                renewer = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "jade-id-worker-renewer");
                    t.setDaemon(true);
                    return t;
                });
                long period = Math.max(1, leaseSeconds / 3);
                renewer.scheduleAtFixedRate(this::renew, period, period, TimeUnit.SECONDS);
                return;
            }
        }
        throw new IllegalStateException("没有可用的 ID workerId，请增加 jade.id.worker.max");
    }

    private void renew() {
        try {
            Object result = redis.execute("EVAL", RENEW_LUA, "1", key, instanceId,
                    String.valueOf(leaseSeconds));
            leaseValid = result != null && !"0".equals(result.toString());
        } catch (Exception e) {
            leaseValid = false;
        }
    }

    public int workerId() {
        if (!leaseValid) throw new IllegalStateException("ID workerId 租约已失效，拒绝继续生成 ID");
        return workerId;
    }

    @PreDestroy
    void stop() {
        if (renewer != null) renewer.shutdownNow();
        if (key != null) {
            try { redis.execute("EVAL", "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", "1", key, instanceId); }
            catch (Exception ignored) { }
        }
    }
}
