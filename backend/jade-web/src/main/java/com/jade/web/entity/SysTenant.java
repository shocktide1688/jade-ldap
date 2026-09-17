package com.jade.web.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 租户（多租户核心，被 jade-admin 和 jade-demo 共享）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_tenant")
public class SysTenant extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "code", nullable = false, length = 64)
    public String code;

    @Column(name = "name", nullable = false, length = 128)
    public String name;

    @Column(name = "status", nullable = false)
    public Short status = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;
}
