package com.jade.ldap.directory;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ldap_directory_entry")
public class LdapDirectoryEntry extends PanacheEntityBase {
    @Id
    @Column(name = "normalized_dn", length = 1024)
    public String normalizedDn;

    @Column(nullable = false, length = 1024)
    public String dn;

    @Column(name = "entry_ldif", nullable = false, columnDefinition = "TEXT")
    public String entryLdif;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
