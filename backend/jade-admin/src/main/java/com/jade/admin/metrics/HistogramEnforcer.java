package com.jade.admin.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.quarkus.micrometer.runtime.MeterRegistryCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * 强制把所有 Timer 转 histogram
 *
 * 用 @Produces MeterRegistryCustomizer，让它在 Quarkus 自带 binder 注册 meter 之前生效
 * 这样 http_server_requests_seconds 也会输出成 histogram
 */
@ApplicationScoped
public class HistogramEnforcer {

    @Produces
    @Singleton
    public MeterRegistryCustomizer histogramCustomizer() {
        return new MeterRegistryCustomizer() {
            @Override
            public void customize(MeterRegistry registry) {
                registry.config().meterFilter(new MeterFilter() {
                    @Override
                    public DistributionStatisticConfig configure(
                            Meter.Id id,
                            DistributionStatisticConfig config) {
                        if (id.getType() == Meter.Type.TIMER
                                || id.getType() == Meter.Type.DISTRIBUTION_SUMMARY) {
                            return DistributionStatisticConfig.builder()
                                    .percentilesHistogram(true)
                                    .build()
                                    .merge(config);
                        }
                        return config;
                    }
                });
            }
        };
    }
}
