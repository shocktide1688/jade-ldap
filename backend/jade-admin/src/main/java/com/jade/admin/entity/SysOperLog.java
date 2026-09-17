package com.jade.admin.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志（由 @Log 注解 + 切面自动写入）
 */
@Data
@Entity
@Table(name = "sys_oper_log")
@EqualsAndHashCode(callSuper = false)
public class SysOperLog extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(length = 100)
    public String title;

    @Column(name = "business_type")
    public Short businessType = 0;  // 1=新增 2=修改 3=删除 4=查询 5=导出

    @Column(length = 200)
    public String method;  // 类.方法

    @Column(name = "request_method", length = 10)
    public String requestMethod;

    @Column(name = "request_url", length = 500)
    public String requestUrl;

    @Column(name = "request_param", columnDefinition = "TEXT")
    public String requestParam;

    @Column(name = "response_result", columnDefinition = "TEXT")
    public String responseResult;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    public String errorMsg;

    @Column(name = "user_id")
    public Long userId;

    @Column(length = 50)
    public String username;

    @Column(length = 50)
    public String ip;

    @Column(length = 200)
    public String location;

    @Column(name = "user_agent", length = 500)
    public String userAgent;

    @Column(name = "duration_ms")
    public Long durationMs;

    @Column(name = "oper_time")
    public LocalDateTime operTime;
}
