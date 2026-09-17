package com.jade.log.aspect;

import com.jade.log.annotation.OperateLog;
import com.jade.log.annotation.OperateLogBinding;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 操作日志切面（CDI Interceptor）
 *
 * 用法：在方法上 @OperateLog(module = "...", action = "...")
 */
@Slf4j
@OperateLogBinding
@Interceptor
public class OperateLogAspect {

    @AroundInvoke
    public Object log(InvocationContext ctx) throws Exception {
        Method method = ctx.getMethod();
        OperateLog annotation = method.getAnnotation(OperateLog.class);

        long start = System.currentTimeMillis();
        String module = annotation != null ? annotation.module() : method.getDeclaringClass().getSimpleName();
        String action = annotation != null ? annotation.action() : method.getName();

        try {
            Object result = ctx.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("[OperateLog] module={} action={} cost={}ms status=ok", module, action, cost);
            // TODO: 持久化到 DB / 发到 Kafka
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[OperateLog] module={} action={} cost={}ms status=fail msg={}",
                    module, action, cost, e.getMessage());
            throw e;
        }
    }
}
