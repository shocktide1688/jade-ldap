package com.jade.ldap.security;

import com.jade.ldap.server.LdapBindSecurityService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class LdapAccountLockStore {

    @Inject
    EntityManager entityManager;

    @Transactional
    public boolean isLocked(String normalizedDn) {
        LdapAccountLock row = LdapAccountLock.findById(normalizedDn);
        return row != null && row.lockedUntil != null && row.lockedUntil.isAfter(OffsetDateTime.now());
    }

    @Transactional
    public void recordFailure(String normalizedDn, String bindDn, int maximum, long lockSeconds,
                              long failureWindowSeconds) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, normalizedDn)
                .getSingleResult();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LdapAccountLock row = LdapAccountLock.findById(normalizedDn);
        if (row == null) {
            row = new LdapAccountLock();
            row.normalizedDn = normalizedDn;
            row.bindDn = bindDn;
            row.failedAttempts = 1;
            row.lastFailedAt = now;
            row.lockedUntil = maximum <= 1 ? now.plusSeconds(lockSeconds) : null;
            entityManager.persist(row);
            return;
        }
        if (row.lockedUntil != null && row.lockedUntil.isAfter(now)) return;
        if (row.lastFailedAt == null || row.lastFailedAt.isBefore(now.minusSeconds(failureWindowSeconds))) {
            row.failedAttempts = 0;
        }
        row.bindDn = bindDn;
        row.failedAttempts++;
        row.lastFailedAt = now;
        row.lockedUntil = row.failedAttempts >= maximum ? now.plusSeconds(lockSeconds) : null;
    }

    @Transactional
    public void clear(String normalizedDn) {
        LdapAccountLock.deleteById(normalizedDn);
    }

    @Transactional
    public List<LdapBindSecurityService.LockView> list() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return LdapAccountLock.<LdapAccountLock>listAll().stream()
                .map(row -> new LdapBindSecurityService.LockView(
                        row.bindDn,
                        row.failedAttempts,
                        instant(row.lastFailedAt),
                        instant(row.lockedUntil),
                        row.lockedUntil != null && row.lockedUntil.isAfter(now)))
                .sorted(java.util.Comparator.comparing(LdapBindSecurityService.LockView::bindDn))
                .toList();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
