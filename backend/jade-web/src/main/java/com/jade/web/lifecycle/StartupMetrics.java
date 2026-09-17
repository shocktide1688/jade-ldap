package com.jade.web.lifecycle;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 启动时间指标（基础版）
 *
 * 暴露 3 个关键时间点：
 *   - JVM 启动时间（推算）
 *   - Quarkus 应用 ready 时间
 *   - 总启动耗时（JVM boot → Quarkus ready）
 *
 * 端点：/q/admin/startup（见 StartupInfoResource）
 * Prometheus 指标：在 jade-demo 模块通过 quarkus-micrometer-registry-prometheus 自动暴露
 */
@ApplicationScoped
public class StartupMetrics {

    private static final Logger LOG = Logger.getLogger(StartupMetrics.class);

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "jade")
    String appName;

    private final AtomicReference<Instant> jvmStartTime = new AtomicReference<>();
    private final AtomicReference<Instant> quarkusReadyTime = new AtomicReference<>();

    /**
     * 在应用 ready 时记录
     */
    void onStart(@Observes StartupEvent ev) {
        Instant now = Instant.now();

        // JVM 启动时间（从 uptime 反推）
        long jvmStartMs = ManagementFactory.getRuntimeMXBean().getUptime();
        jvmStartTime.set(now.minusMillis(jvmStartMs));

        // Quarkus ready 时间
        quarkusReadyTime.set(now);

        Duration total = Duration.between(jvmStartTime.get(), now);
        LOG.infof("⏱️  [STARTUP] %s 启动完成: %dms (JVM boot → Quarkus ready)", appName, total.toMillis());
    }

    public Instant getJvmStartTime() { return jvmStartTime.get(); }
    public Instant getQuarkusReadyTime() { return quarkusReadyTime.get(); }

    public long getTotalStartupMs() {
        if (jvmStartTime.get() == null || quarkusReadyTime.get() == null) return -1;
        return Duration.between(jvmStartTime.get(), quarkusReadyTime.get()).toMillis();
    }
}
