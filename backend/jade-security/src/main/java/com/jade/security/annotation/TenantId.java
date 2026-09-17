package com.jade.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多租户 ID 字段标记
 *
 * 用法：Entity 中标 @TenantId 的字段会被自动注入当前租户 ID，
 *      查询时会自动加 WHERE tenant_id = ?
 *
 * 配合 TenantContext 使用
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TenantId {
}
