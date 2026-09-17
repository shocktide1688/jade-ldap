package com.jade.log.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（标在 Controller 方法上）
 *
 * 用法：@OperateLog(module = "用户管理", action = "创建用户")
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperateLog {
    String module() default "";
    String action() default "";
    boolean saveRequest() default true;
    boolean saveResponse() default false;
}
