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
 * 通知公告
 */
@Data
@Entity
@Table(name = "sys_notice")
@EqualsAndHashCode(callSuper = false)
public class SysNotice extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(name = "notice_title", nullable = false, length = 200)
    public String noticeTitle;

    @Column(name = "notice_type")
    public Short noticeType = 1;  // 1=通知 2=公告

    @Column(name = "notice_content", columnDefinition = "TEXT")
    public String noticeContent;

    @Column
    public Short status = 1;

    @Column(name = "created_by", length = 50)
    public String createdBy;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column
    public Boolean deleted = false;
}
