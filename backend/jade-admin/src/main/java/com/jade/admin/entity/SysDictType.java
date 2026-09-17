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
 * 字典类型
 */
@Data
@Entity
@Table(name = "sys_dict_type")
@EqualsAndHashCode(callSuper = false)
public class SysDictType extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "dict_name", nullable = false, length = 100)
    public String dictName;

    @Column(name = "dict_type", nullable = false, length = 100)
    public String dictType;

    @Column
    public Short status = 1;

    @Column(length = 500)
    public String remark;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column
    public Boolean deleted = false;
}
