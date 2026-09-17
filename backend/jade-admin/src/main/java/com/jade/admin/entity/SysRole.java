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
 * 角色
 *
 * tenantId = null 表示系统级角色（跨租户）
 * dataScope: ALL/DEPT_AND_CHILD/DEPT/SELF
 */
@Data
@Entity
@Table(name = "sys_role")
@EqualsAndHashCode(callSuper = false)
public class SysRole extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(name = "role_name", nullable = false, length = 50)
    public String roleName;

    @Column(name = "role_code", nullable = false, length = 50)
    public String roleCode;

    @Column(name = "role_sort")
    public Integer roleSort = 0;

    @Column(name = "data_scope", length = 20)
    public String dataScope = "ALL";

    @Column(name = "status")
    public Short status = 1;

    @Column(length = 500)
    public String remark;

    @Column(name = "created_by", length = 50)
    public String createdBy;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_by", length = 50)
    public String updatedBy;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column(name = "deleted")
    public Boolean deleted = false;
}
