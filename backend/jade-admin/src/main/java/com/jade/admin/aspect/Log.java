package com.jade.admin.aspect;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * 标注在 controller 方法上，自动写入 sys_oper_log
 *
 * 用法:
 *   @Log(title = "用户管理", businessType = 1)  // 1=新增 2=修改 3=删除 4=查询 5=导出
 *   public R<User> create(...) { ... }
 *
 * businessType:
 *   0 = 其它
 *   1 = 新增
 *   2 = 修改
 *   3 = 删除
 *   4 = 查询
 *   5 = 导出
 *   6 = 导入
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Log {

    /** 模块标题（菜单名） */
    @Nonbinding
    String title() default "";

    /** 业务操作类型 */
    @Nonbinding
    int businessType() default 0;

    /** 是否保存请求参数 */
    @Nonbinding
    boolean saveParam() default true;

    /** 是否保存响应结果 */
    @Nonbinding
    boolean saveResult() default false;
}
