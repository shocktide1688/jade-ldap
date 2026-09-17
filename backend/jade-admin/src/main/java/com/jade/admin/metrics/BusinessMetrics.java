package com.jade.admin.metrics;

import com.jade.admin.repository.SysPatientRepository;
import com.jade.admin.repository.SysProjectRepository;
import com.jade.web.repository.SysTenantRepository;
import com.jade.security.repository.SysUserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Jade 业务指标中心
 *
 * 暴露 4 类指标到 Prometheus：
 *   1. 实体计数（gauge，懒查询 DB）: jade_entity_total{type="user|tenant|patient|project"}
 *   2. 业务事件（counter）: jade_login_total{result="success|fail"}, jade_order_total, jade_patient_total 等
 *   3. 业务操作耗时（timer）: jade_login_duration, jade_idempotent_check_duration
 *   4. 当前活跃（gauge）: jade_active_tenants
 *
 * 在 Grafana 里可以直接 panel 这些指标做实时数据统计
 */
@ApplicationScoped
public class BusinessMetrics {

    @Inject MeterRegistry registry;

    @Inject SysUserRepository userRepo;
    @Inject SysTenantRepository tenantRepo;
    @Inject SysPatientRepository patientRepo;
    @Inject SysProjectRepository projectRepo;

    // 业务事件 counters（懒初始化）
    private Counter loginSuccess;
    private Counter loginFailure;
    private Counter orderCreated;
    private Counter orderIdempotentHit;
    private Counter patientCreated;
    private Counter tenantCreated;
    private Counter userCreated;

    void onStart(@Observes StartupEvent ev) {
        // ---- 关键：用 MeterFilter 把所有 Timer 强制转 histogram ----
        // 这是程序化方式，绕开 Quarkus 配置文件不生效的 bug
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    DistributionStatisticConfig config) {
                // 对所有 Timer 启用 percentile histogram
                if (id.getType() == Meter.Type.TIMER) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .build()
                            .merge(config);
                }
                return config;
            }
        });

        // ---- 实体总数 gauge（查询 DB）----
        registry.gauge("jade.entity.total",
                io.micrometer.core.instrument.Tags.of("type", "user"),
                userRepo, r -> r.count());
        registry.gauge("jade.entity.total",
                io.micrometer.core.instrument.Tags.of("type", "tenant"),
                tenantRepo, r -> r.count());
        registry.gauge("jade.entity.total",
                io.micrometer.core.instrument.Tags.of("type", "patient"),
                patientRepo, r -> r.count());
        registry.gauge("jade.entity.total",
                io.micrometer.core.instrument.Tags.of("type", "project"),
                projectRepo, r -> r.count());

        // ---- 业务事件 counters（预创建）----
        loginSuccess   = Counter.builder("jade.login.total")
                .description("登录尝试总数")
                .tag("result", "success").register(registry);
        loginFailure   = Counter.builder("jade.login.total")
                .description("登录尝试总数")
                .tag("result", "failure").register(registry);
        orderCreated   = Counter.builder("jade.order.total")
                .description("订单创建总数")
                .tag("result", "created").register(registry);
        orderIdempotentHit = Counter.builder("jade.order.total")
                .description("订单创建总数")
                .tag("result", "idempotent_hit").register(registry);
        patientCreated = Counter.builder("jade.patient.total")
                .description("患者创建总数")
                .tag("result", "created").register(registry);
        tenantCreated  = Counter.builder("jade.tenant.total")
                .description("租户创建总数")
                .tag("result", "created").register(registry);
        userCreated    = Counter.builder("jade.user.total")
                .description("用户创建总数")
                .tag("result", "created").register(registry);
    }

    // ---- 业务事件 API（让 controller/service 调用）----

    public void recordLoginSuccess()    { if (loginSuccess != null) loginSuccess.increment(); }
    public void recordLoginFailure()    { if (loginFailure != null) loginFailure.increment(); }
    public void recordOrderCreated()    { if (orderCreated != null) orderCreated.increment(); }
    public void recordOrderIdempotent() { if (orderIdempotentHit != null) orderIdempotentHit.increment(); }
    public void recordPatientCreated()  { if (patientCreated != null) patientCreated.increment(); }
    public void recordTenantCreated()   { if (tenantCreated != null) tenantCreated.increment(); }
    public void recordUserCreated()     { if (userCreated != null) userCreated.increment(); }

    /**
     * 提供业务计时器（用于登录/订单等关键路径）
     *
     * 用 publishPercentileHistogram() 把 summary 转 histogram，Prometheus 端就有 bucket
     * 可以在 Grafana 用 histogram_quantile() 算 p50/p95/p99
     */
    public io.micrometer.core.instrument.Timer loginTimer() {
        return io.micrometer.core.instrument.Timer.builder("jade.login.duration")
                .description("登录处理耗时")
                .publishPercentileHistogram()
                .register(registry);
    }

    public io.micrometer.core.instrument.Timer orderTimer() {
        return io.micrometer.core.instrument.Timer.builder("jade.order.duration")
                .description("订单处理耗时")
                .publishPercentileHistogram()
                .register(registry);
    }
}
