package com.jade.web.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

/**
 * CORS 过滤器（请求 + 响应）
 *
 * 解决 Quarkus 3.15 的 quarkus.http.cors 配置不生效问题
 * 直接在响应里加 CORS 头，并拦截 OPTIONS 预检请求
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerResponseFilter, jakarta.ws.rs.container.ContainerRequestFilter {

    @ConfigProperty(name = "quarkus.http.cors.origins", defaultValue = "http://localhost:5173")
    String allowedOrigins;

    @ConfigProperty(name = "quarkus.http.cors.methods", defaultValue = "GET,POST,PUT,DELETE,OPTIONS,PATCH")
    String allowedMethods;

    @ConfigProperty(name = "quarkus.http.cors.headers", defaultValue = "accept,authorization,content-type,x-requested-with,x-trace-id,x-idempotency-key")
    String allowedHeaders;

    @ConfigProperty(name = "quarkus.http.cors.exposed-headers", defaultValue = "x-trace-id")
    String exposedHeaders;

    @ConfigProperty(name = "jade.cors.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        if (!enabled) return;

        // 处理 OPTIONS 预检：直接返回 200 + CORS 头
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            String origin = request.getHeaderString("Origin");
            if (origin != null && isAllowed(origin)) {
                Response.ResponseBuilder rb = Response.ok();
                rb.header("Access-Control-Allow-Origin", origin);
                rb.header("Access-Control-Allow-Methods", allowedMethods);
                rb.header("Access-Control-Allow-Headers", allowedHeaders);
                rb.header("Access-Control-Expose-Headers", exposedHeaders);
                rb.header("Access-Control-Allow-Credentials", "true");
                rb.header("Access-Control-Max-Age", "3600");
                request.abortWith(rb.build());
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        if (!enabled) return;

        String origin = request.getHeaderString("Origin");
        if (origin == null) return;
        if (!isAllowed(origin)) return;

        response.getHeaders().putSingle("Access-Control-Allow-Origin", origin);
        response.getHeaders().putSingle("Access-Control-Allow-Methods", allowedMethods);
        response.getHeaders().putSingle("Access-Control-Allow-Headers", allowedHeaders);
        response.getHeaders().putSingle("Access-Control-Expose-Headers", exposedHeaders);
        response.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
    }

    private boolean isAllowed(String origin) {
        if ("*".equals(allowedOrigins)) return true;
        for (String allowed : allowedOrigins.split(",")) {
            if (allowed.trim().equals(origin)) return true;
        }
        return false;
    }
}
