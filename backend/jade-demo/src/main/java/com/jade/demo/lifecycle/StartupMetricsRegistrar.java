package com.jade.demo.lifecycle;

import com.jade.web.lifecycle.StartupMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * 把 jade-web 的 StartupMetrics 注册到 Micrometer
 *
 * 必须在 jade-demo（依赖 micrometer）里做这个桥接
 */
@ApplicationScoped
public class StartupMetricsRegistrar {

    @Inject
    StartupMetrics startupMetrics;

    @Inject
    MeterRegistry registry;

    void onStart(@Observes StartupEvent ev) {
        // 等 StartupMetrics 自己初始化完
        new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            long totalMs = startupMetrics.getTotalStartupMs();
            if (totalMs > 0) {
                Timer.builder("jade.application.startup.time")
                        .description("应用启动到 ready 的耗时（毫秒）")
                        .register(registry)
                        .record(java.time.Duration.ofMillis(totalMs));
                registry.gauge("jade.application.startup.timestamp",
                        startupMetrics.getQuarkusReadyTime().toEpochMilli());
            }
        }).start();
    }
}
