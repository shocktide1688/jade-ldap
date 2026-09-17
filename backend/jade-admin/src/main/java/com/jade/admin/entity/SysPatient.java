package com.jade.admin.entity;

import com.jade.security.annotation.Encrypted;
import com.jade.security.crypto.EncryptedStringConverter;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.persistence.Convert;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 病人信息（演示字段加密）
 *
 * 身份证、手机号字段在 DB 里是密文，API 返回明文
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_patient")
public class SysPatient extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false, length = 64)
    public String name;

    @Encrypted
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "id_card", length = 512)
    public String idCard;

    @Encrypted
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone", length = 512)
    public String phone;

    @Column(name = "diagnosis", length = 256)
    public String diagnosis;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;
}
