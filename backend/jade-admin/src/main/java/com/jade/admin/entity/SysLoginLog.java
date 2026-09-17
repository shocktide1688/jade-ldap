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
 * 登录日志（由 AuthService 登录成功后写入，或登录失败兜底写入）
 */
@Data
@Entity
@Table(name = "sys_login_log")
@EqualsAndHashCode(callSuper = false)
public class SysLoginLog extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(length = 50)
    public String username;

    @Column(length = 50)
    public String ip;

    @Column(length = 200)
    public String location;

    @Column(length = 100)
    public String browser;

    @Column(length = 100)
    public String os;

    @Column
    public Short status = 1;  // 1=成功 0=失败

    @Column(length = 500)
    public String msg;

    @Column(name = "login_time")
    public LocalDateTime loginTime;
}
