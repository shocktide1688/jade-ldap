package com.jade.ldap.security;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ldap_account_lock")
public class LdapAccountLock extends PanacheEntityBase {
    @Id
    @Column(name = "normalized_dn", length = 1024)
    public String normalizedDn;

    @Column(name = "bind_dn", nullable = false, length = 1024)
    public String bindDn;

    @Column(name = "failed_attempts", nullable = false)
    public int failedAttempts;

    @Column(name = "last_failed_at", nullable = false)
    public OffsetDateTime lastFailedAt;

    @Column(name = "locked_until")
    public OffsetDateTime lockedUntil;
}
