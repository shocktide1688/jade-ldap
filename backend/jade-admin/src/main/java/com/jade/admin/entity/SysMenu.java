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
 * 菜单（目录 / 菜单 / 按钮 三合一）
 *
 * menuType: M=目录 C=菜单 F=按钮
 * perms: 权限标识 system:user:list
 */
@Data
@Entity
@Table(name = "sys_menu")
@EqualsAndHashCode(callSuper = false)
public class SysMenu extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "parent_id")
    public Long parentId = 0L;

    @Column(name = "menu_name", nullable = false, length = 50)
    public String menuName;

    @Column(name = "menu_type", nullable = false, length = 1)
    public String menuType;  // M / C / F

    @Column(length = 200)
    public String path;

    @Column(length = 200)
    public String component;

    @Column(length = 50)
    public String icon;

    @Column(length = 100)
    public String perms;

    @Column(name = "sort_order")
    public Integer sortOrder = 0;

    @Column
    public Short visible = 1;

    @Column
    public Short status = 1;

    @Column(name = "is_cache")
    public Short isCache = 0;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column
    public Boolean deleted = false;
}
