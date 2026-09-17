package com.jade.web.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * TraceId 透传过滤器：自动生成或读取 traceId 写入 MDC / 响应头
 */
@Provider
@Priority(1000)
public class TraceIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String traceId = requestContext.getHeaderString(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        requestContext.setProperty(TRACE_ID_MDC_KEY, traceId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object traceId = request.getProperty(TRACE_ID_MDC_KEY);
        if (traceId != null) {
            response.getHeaders().putSingle(TRACE_ID_HEADER, traceId.toString());
        }
        MDC.remove(TRACE_ID_MDC_KEY);
    }
}
