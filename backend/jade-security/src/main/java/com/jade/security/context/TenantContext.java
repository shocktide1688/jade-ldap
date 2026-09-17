package com.jade.security.context;

/**
 * 当前租户上下文（ThreadLocal）
 *
 * 用法：
 *   TenantContext.set(1L);  // 在登录后或拦截器里设置
 *   Long tenantId = TenantContext.get();
 *   TenantContext.clear();
 */
public class TenantContext {

    private static final ThreadLocal<Long> CTX = new ThreadLocal<>();

    public static void set(Long tenantId) {
        CTX.set(tenantId);
    }

    public static Long get() {
        return CTX.get();
    }

    public static Long require() {
        Long id = CTX.get();
        if (id == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return id;
    }

    public static void clear() {
        CTX.remove();
    }
}
