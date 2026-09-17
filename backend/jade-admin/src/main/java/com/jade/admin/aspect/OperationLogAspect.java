package com.jade.admin.aspect;

import com.jade.admin.entity.SysOperLog;
import com.jade.admin.repository.SysOperLogRepository;
import com.jade.security.context.TenantContext;
import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import org.jboss.resteasy.reactive.server.core.CurrentRequestManager;

import java.time.OffsetDateTime;
import java.util.Arrays;

/**
 * 操作日志切面
 *
 * 拦截 @Log 注解的方法，方法返回后异步写入 sys_oper_log
 */
@Log
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
public class OperationLogAspect {

    @Inject
    SysOperLogRepository operLogRepository;

    @Inject
    SysUserRepository userRepository;

    @Inject
    SecurityIdentity identity;

    @Context
    ContainerRequestContext requestContext;

    @AroundInvoke
    public Object around(InvocationContext ctx) throws Exception {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = ctx.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                record(ctx, result, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 写日志失败不能影响业务
            }
        }
    }

    private void record(InvocationContext ctx, Object result, Throwable error, long durationMs) {
        Log annotation = ctx.getMethod().getAnnotation(Log.class);
        if (annotation == null) return;

        SysOperLog log = new SysOperLog();
        log.title = annotation.title();
        log.businessType = (short) annotation.businessType();
        log.method = ctx.getMethod().getDeclaringClass().getSimpleName() + "." + ctx.getMethod().getName();
        log.durationMs = durationMs;
        log.operTime = java.time.LocalDateTime.now();

        // 用户
        try {
            if (identity != null && !identity.isAnonymous() && identity.getPrincipal() != null) {
                String username = identity.getPrincipal().getName();
                SysUser user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    log.userId = user.id;
                    log.username = user.username;
                }
                if (user != null && user.tenantId != null) {
                    log.tenantId = user.tenantId;
                }
            }
        } catch (Exception ignored) {}

        // HTTP 信息
        try {
            if (requestContext != null) {
                log.requestMethod = requestContext.getMethod();
                log.requestUrl = requestContext.getUriInfo().getPath();
                log.ip = requestContext.getHeaderString("X-Forwarded-For");
                if (log.ip == null) log.ip = "127.0.0.1";
                log.userAgent = requestContext.getHeaderString("User-Agent");
                if (annotation.saveParam()) {
                    // 只记录前 500 字符，避免太大
                    String param = requestContext.getUriInfo().getQueryParameters().toString();
                    log.requestParam = param.length() > 500 ? param.substring(0, 500) + "..." : param;
                }
            }
        } catch (Exception ignored) {}

        // 响应 / 异常
        if (error != null) {
            log.errorMsg = error.getClass().getSimpleName() + ": " + error.getMessage();
        }
        if (annotation.saveResult() && result != null) {
            try {
                String s = result.toString();
                log.responseResult = s.length() > 1000 ? s.substring(0, 1000) + "..." : s;
            } catch (Exception ignored) {}
        }

        operLogRepository.persist(log);
    }
}
