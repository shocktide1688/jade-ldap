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
 * 定时任务
 *
 * invokeTarget: beanName.methodName
 */
@Data
@Entity
@Table(name = "sys_task")
@EqualsAndHashCode(callSuper = false)
public class SysTask extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(name = "task_name", nullable = false, length = 100)
    public String taskName;

    @Column(name = "task_group", length = 50)
    public String taskGroup = "DEFAULT";

    @Column(name = "invoke_target", nullable = false, length = 500)
    public String invokeTarget;

    @Column(name = "cron_expression", length = 100)
    public String cronExpression;

    @Column(name = "misfire_policy", length = 20)
    public String misfirePolicy = "3";

    @Column
    public Short concurrent = 0;

    @Column
    public Short status = 1;  // 0=暂停 1=运行

    @Column(length = 500)
    public String remark;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column
    public Boolean deleted = false;
}
