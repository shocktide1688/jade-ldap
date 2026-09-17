package com.jade.web.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * OpenAPI 基础信息（可被 application.yml 覆盖）
 */
@ApplicationScoped
public class JadeOpenApiConfig {

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "Jade API")
    String appName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;

    public String getTitle() {
        return appName;
    }

    public Map<String, Object> getInfo() {
        return Map.of(
                "title", appName,
                "version", appVersion,
                "description", "Built with Jade Platform"
        );
    }
}
