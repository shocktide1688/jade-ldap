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
 * 部门（树形）
 */
@Data
@Entity
@Table(name = "sys_dept")
@EqualsAndHashCode(callSuper = false)
public class SysDept extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(name = "parent_id")
    public Long parentId = 0L;

    @Column(name = "dept_name", nullable = false, length = 50)
    public String deptName;

    @Column(name = "dept_code", length = 50)
    public String deptCode;

    @Column(name = "sort_order")
    public Integer sortOrder = 0;

    @Column(name = "leader_user_id")
    public Long leaderUserId;

    @Column(length = 20)
    public String phone;

    @Column(length = 100)
    public String email;

    @Column
    public Short status = 1;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column
    public Boolean deleted = false;
}
