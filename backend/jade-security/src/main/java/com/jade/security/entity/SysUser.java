package com.jade.security.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * 系统用户（安全核心实体，被 jade-admin 和 jade-demo 共享）
 *
 * 字段约定：
 *   - tenantId = null → 平台超级管理员（跨租户）
 *   - tenantId = X    → 租户 X 的用户
 *   - status = 1      → 正常，0 → 禁用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_user")
public class SysUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String username;

    @Column(nullable = false, length = 128, updatable = false)
    public String password;

    @Column(length = 64)
    public String nickname;

    @Column(length = 128)
    public String email;

    @Column(length = 32)
    public String phone;

    /** 1=正常 0=禁用 */
    @Column(nullable = false)
    public Short status = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @Column(nullable = false)
    public Boolean deleted = false;

    /**
     * 所属租户（null = 平台超管，可管理所有租户）
     * 登录时写入 JWT，TenantFilter 据此设置 TenantContext
     */
    @Column(name = "tenant_id")
    public Long tenantId;
}
