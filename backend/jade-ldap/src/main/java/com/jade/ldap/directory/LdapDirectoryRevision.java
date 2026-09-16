package com.jade.ldap.directory;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ldap_directory_revision")
public class LdapDirectoryRevision extends PanacheEntityBase {
    @Id
    public Short id;

    @Column(nullable = false)
    public long revision;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
