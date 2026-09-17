package com.jade.web.lifecycle;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动信息端点
 *
 * 路径：/q/admin/startup
 *
 * 暴露：
 *   - JVM 启动时间
 *   - Quarkus ready 时间
 *   - 总启动耗时
 *   - JVM uptime
 *   - 当前时间
 */
@Path("/q/admin/startup")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class StartupInfoResource {

    @Inject
    StartupMetrics startupMetrics;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "jade")
    String appName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;

    @GET
    public Map<String, Object> get() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", appName);
        info.put("version", appVersion);

        long now = System.currentTimeMillis();
        Instant jvmStart = startupMetrics.getJvmStartTime();
        Instant quarkusReady = startupMetrics.getQuarkusReadyTime();
        long jvmUptime = ManagementFactory.getRuntimeMXBean().getUptime();

        info.put("now", Instant.ofEpochMilli(now).toString());
        info.put("jvm_start_time", jvmStart == null ? null : jvmStart.toString());
        info.put("quarkus_ready_time", quarkusReady == null ? null : quarkusReady.toString());
        info.put("total_startup_ms", startupMetrics.getTotalStartupMs());
        info.put("jvm_uptime_ms", jvmUptime);
        info.put("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        info.put("heap_used_mb",
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024 / 1024);
        info.put("heap_max_mb",
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / 1024 / 1024);

        return info;
    }
}
