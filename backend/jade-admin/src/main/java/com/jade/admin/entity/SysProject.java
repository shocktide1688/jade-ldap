package com.jade.admin.entity;

import com.jade.security.annotation.TenantId;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 项目实体（演示多租户隔离）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_project")
public class SysProject extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    public Long tenantId;

    @Column(name = "name", nullable = false, length = 128)
    public String name;

    @Column(name = "description", length = 512)
    public String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    @Column(nullable = false)
    public Boolean deleted = false;
}
