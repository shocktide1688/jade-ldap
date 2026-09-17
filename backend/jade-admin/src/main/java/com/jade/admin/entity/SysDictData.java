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
 * 字典数据
 */
@Data
@Entity
@Table(name = "sys_dict_data")
@EqualsAndHashCode(callSuper = false)
public class SysDictData extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "dict_type", nullable = false, length = 100)
    public String dictType;

    @Column(name = "dict_label", nullable = false, length = 100)
    public String dictLabel;

    @Column(name = "dict_value", nullable = false, length = 100)
    public String dictValue;

    @Column(name = "css_class", length = 50)
    public String cssClass;

    @Column(name = "sort_order")
    public Integer sortOrder = 0;

    @Column
    public Short status = 1;

    @Column(name = "is_default")
    public Short isDefault = 0;

    @Column(length = 500)
    public String remark;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column
    public Boolean deleted = false;
}
