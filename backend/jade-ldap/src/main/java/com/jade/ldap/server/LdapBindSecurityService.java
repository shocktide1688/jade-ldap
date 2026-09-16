package com.jade.ldap.server;

import com.jade.ldap.audit.LdapBindAuditService;
import com.jade.ldap.security.LdapAccountLockStore;
import com.unboundid.ldap.sdk.DN;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class LdapBindSecurityService {

    @Inject
    LdapServerConfig config;

    @Inject
    LdapBindAuditService audit;

    @Inject
    LdapAccountLockStore locks;

    public boolean isLocked(String bindDn) {
        return locks.isLocked(key(bindDn));
    }

    public void record(String bindDn, String address, int resultCode, boolean success, String detail) {
        audit.record(bindDn, address, success, resultCode, detail);
        if (success) {
            locks.clear(key(bindDn));
        } else if (eligibleForLockout(bindDn)) {
            locks.recordFailure(key(bindDn), bindDn, config.security().maxFailedBinds(),
                    config.security().lockSeconds(), config.security().failureWindowSeconds());
        }
    }

    public void recordLockedAttempt(String bindDn, String address, int resultCode) {
        audit.record(bindDn, address, false, resultCode, "account_locked");
    }

    public List<LockView> locks() {
        return locks.list();
    }

    public boolean unlock(String bindDn) {
        boolean existed = locks.list().stream().anyMatch(lock -> sameDn(lock.bindDn(), bindDn));
        locks.clear(key(bindDn));
        return existed;
    }

    private boolean eligibleForLockout(String bindDn) {
        return bindDn != null && !bindDn.isBlank() && !sameDn(bindDn, config.adminDn());
    }

    private boolean sameDn(String first, String second) {
        try {
            return new DN(first).equals(new DN(second));
        } catch (Exception ignored) {
            return first.equalsIgnoreCase(second);
        }
    }

    private String key(String bindDn) {
        try {
            return new DN(bindDn).toNormalizedString();
        } catch (Exception ignored) {
            return bindDn == null ? "" : bindDn.toLowerCase(Locale.ROOT);
        }
    }

    public record LockView(String bindDn, int failedAttempts, Instant lastFailedAt,
                           Instant lockedUntil, boolean locked) {}
}
