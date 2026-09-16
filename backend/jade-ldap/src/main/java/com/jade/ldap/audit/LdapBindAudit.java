package com.jade.ldap.audit;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ldap_bind_audit")
public class LdapBindAudit extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "bind_dn", nullable = false, length = 512)
    public String bindDn;

    @Column(name = "client_address", length = 128)
    public String clientAddress;

    @Column(nullable = false)
    public boolean success;

    @Column(name = "result_code", nullable = false)
    public int resultCode;

    @Column(length = 256)
    public String detail;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
