package com.jade.ldap.directory;

import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldif.LDIFReader;
import com.jade.ldap.server.LdapServerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class LdapDirectoryStore {

    public static final long WRITE_LOCK_ID = 0x4A4144454C444150L;

    @Inject
    EntityManager entityManager;

    @Inject
    LdapServerConfig config;

    @Transactional
    public long revision() {
        LdapDirectoryRevision row = LdapDirectoryRevision.findById((short) 1);
        return row == null ? 0 : row.revision;
    }

    @Transactional
    public List<Entry> loadEntries() {
        List<LdapDirectoryEntry> rows = LdapDirectoryEntry.listAll();
        List<Entry> entries = new ArrayList<>(rows.size());
        for (LdapDirectoryEntry row : rows) {
            try {
                if (!belongsToConfiguredDirectory(row.dn)) continue;
                try (LDIFReader reader = new LDIFReader(new ByteArrayInputStream(
                        row.entryLdif.getBytes(StandardCharsets.UTF_8)))) {
                    Entry entry = reader.readEntry();
                    if (entry == null) throw new IllegalStateException("Empty LDIF entry");
                    entries.add(entry);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Invalid LDAP entry persisted for " + row.dn, e);
            }
        }
        entries.sort(Comparator.comparingInt(entry -> dnDepth(entry.getDN())));
        return entries;
    }

    public void acquireWriteLock() {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
                .setParameter(1, WRITE_LOCK_ID)
                .getSingleResult();
    }

    public long replaceAll(List<Entry> entries) {
        for (LdapDirectoryEntry row : LdapDirectoryEntry.<LdapDirectoryEntry>listAll()) {
            if (belongsToConfiguredDirectory(row.dn)) entityManager.remove(row);
        }
        entityManager.flush();
        entityManager.clear();
        OffsetDateTime now = OffsetDateTime.now();
        for (Entry entry : entries) {
            LdapDirectoryEntry row = new LdapDirectoryEntry();
            try {
                row.normalizedDn = new DN(entry.getDN()).toNormalizedString();
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid LDAP DN " + entry.getDN(), e);
            }
            row.dn = entry.getDN();
            row.entryLdif = entry.toLDIFString();
            row.updatedAt = now;
            entityManager.persist(row);
        }
        LdapDirectoryRevision revision = LdapDirectoryRevision.findById((short) 1);
        if (revision == null) {
            revision = new LdapDirectoryRevision();
            revision.id = 1;
            revision.revision = 1;
            revision.updatedAt = now;
            entityManager.persist(revision);
        } else {
            revision.revision++;
            revision.updatedAt = now;
        }
        entityManager.flush();
        return revision.revision;
    }

    @Transactional
    public void initializeIfEmpty(List<Entry> entries) {
        acquireWriteLock();
        if (loadEntries().isEmpty()) {
            replaceAll(entries);
        }
    }

    private boolean belongsToConfiguredDirectory(String dn) {
        try {
            return new DN(dn).isDescendantOf(new DN(config.baseDn()), true);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid LDAP DN persisted for " + dn, e);
        }
    }

    private static int dnDepth(String value) {
        try {
            return new DN(value).getRDNs().length;
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
