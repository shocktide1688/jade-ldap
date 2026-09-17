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
 * 参数配置（全局 KV）
 */
@Data
@Entity
@Table(name = "sys_config")
@EqualsAndHashCode(callSuper = false)
public class SysConfig extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "config_name", nullable = false, length = 100)
    public String configName;

    @Column(name = "config_key", nullable = false, length = 100)
    public String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    public String configValue;

    @Column(name = "config_type", length = 20)
    public String configType = "system";

    @Column(name = "is_builtin")
    public Short isBuiltin = 0;

    @Column(length = 500)
    public String remark;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column
    public Boolean deleted = false;
}
