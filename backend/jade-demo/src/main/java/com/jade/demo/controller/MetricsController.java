package com.jade.demo.controller;

import com.jade.common.api.R;
import com.jade.admin.repository.SysPatientRepository;
import com.jade.admin.repository.SysProjectRepository;
import com.jade.web.repository.SysTenantRepository;
import com.jade.security.repository.SysUserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务指标摘要端点（Grafana 之外的快速查看方式）
 *
 * GET /api/v1/metrics/summary
 * 返回：
 *   - 实体总数（用户/租户/病人/项目）
 *   - 业务事件计数（登录成功/失败、订单、患者、租户）
 *   - 平均处理耗时
 *
 * Grafana 从 Prometheus 拉更细的时序数据，这里给 REST 用。
 */
@Path("/api/v1/metrics")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "业务指标")
@PermitAll
public class MetricsController {

    @Inject MeterRegistry registry;

    @Inject SysUserRepository userRepo;
    @Inject SysTenantRepository tenantRepo;
    @Inject SysPatientRepository patientRepo;
    @Inject SysProjectRepository projectRepo;

    @GET
    @Path("/summary")
    @Operation(summary = "业务指标摘要（实体数 + 业务事件计数）")
    public R<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();

        // ---- 实体总数 ----
        Map<String, Long> entities = new LinkedHashMap<>();
        entities.put("user",    userRepo.count());
        entities.put("tenant",  tenantRepo.count());
        entities.put("patient", patientRepo.count());
        entities.put("project", projectRepo.count());
        data.put("entities", entities);

        // ---- 业务事件计数 ----
        Map<String, Object> events = new LinkedHashMap<>();
        Search s = Search.in(registry).name("jade.login.total");
        s.counters().forEach(c -> {
            String key = "login_" + c.getId().getTag("result");
            events.put(key, (long) c.count());
        });
        Search s2 = Search.in(registry).name("jade.order.total");
        s2.counters().forEach(c -> {
            String key = "order_" + c.getId().getTag("result");
            events.put(key, (long) c.count());
        });
        Search s3 = Search.in(registry).name("jade.patient.total");
        s3.counters().forEach(c -> events.put("patient_created", (long) c.count()));
        Search s4 = Search.in(registry).name("jade.tenant.total");
        s4.counters().forEach(c -> events.put("tenant_created", (long) c.count()));
        data.put("events", events);

        // ---- 业务操作平均耗时 ----
        Map<String, Object> timings = new LinkedHashMap<>();
        Search.in(registry).name("jade.login.duration").timers().forEach(t -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("count", t.count());
            info.put("avg_ms", t.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
            info.put("max_ms", t.max(java.util.concurrent.TimeUnit.MILLISECONDS));
            timings.put("login", info);
        });
        Search.in(registry).name("jade.order.duration").timers().forEach(t -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("count", t.count());
            info.put("avg_ms", t.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
            info.put("max_ms", t.max(java.util.concurrent.TimeUnit.MILLISECONDS));
            timings.put("order", info);
        });
        data.put("timings_ms", timings);

        // ---- HTTP 请求统计（从 Quarkus 自动埋点）----
        Map<String, Object> http = new LinkedHashMap<>();
        Search.in(registry).name("http_server_requests_seconds_count").timers().forEach(t -> {
            String uri = t.getId().getTag("uri");
            String method = t.getId().getTag("method");
            int status = Integer.parseInt(t.getId().getTag("status") == null ? "0" : t.getId().getTag("status"));
            double p95 = t.percentile(0.95, java.util.concurrent.TimeUnit.MILLISECONDS);
            String key = method + " " + uri + " [" + status + "]";
            http.put(key, Map.of(
                    "count", t.count(),
                    "p95_ms", Math.round(p95 * 100) / 100.0
            ));
        });
        data.put("http_top", http);

        return R.ok(data);
    }
}
