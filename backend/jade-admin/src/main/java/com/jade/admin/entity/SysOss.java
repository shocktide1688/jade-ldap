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
 * 文件存储
 */
@Data
@Entity
@Table(name = "sys_oss")
@EqualsAndHashCode(callSuper = false)
public class SysOss extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(name = "file_name", length = 200)
    public String fileName;

    @Column(name = "original_name", length = 200)
    public String originalName;

    @Column(name = "file_suffix", length = 20)
    public String fileSuffix;

    @Column(name = "file_size")
    public Long fileSize;

    @Column(length = 500)
    public String url;

    @Column(name = "storage_type", length = 20)
    public String storageType = "LOCAL";  // LOCAL/MINIO/ALIYUN/S3

    @Column(name = "storage_path", length = 500)
    public String storagePath;

    @Column(length = 100)
    public String bucket;

    @Column(name = "content_type", length = 100)
    public String contentType;

    @Column(name = "upload_by", length = 50)
    public String uploadBy;

    @Column(name = "upload_time")
    public LocalDateTime uploadTime;

    @Column
    public Boolean deleted = false;
}
