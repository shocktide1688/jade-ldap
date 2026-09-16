package com.jade.ldap.audit;

import com.jade.common.api.PageResult;
import com.jade.ldap.server.LdapServerConfig;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LdapBindAuditService {

    @Inject
    EntityManager entityManager;

    @Inject
    LdapServerConfig config;

    @Transactional
    public void record(String bindDn, String clientAddress, boolean success, int resultCode, String detail) {
        LdapBindAudit audit = new LdapBindAudit();
        audit.bindDn = bindDn == null || bindDn.isBlank() ? "<anonymous>" : bindDn;
        audit.clientAddress = clientAddress;
        audit.success = success;
        audit.resultCode = resultCode;
        audit.detail = detail;
        audit.createdAt = LocalDateTime.now();
        audit.persist();
    }

    public List<LdapBindAudit> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return LdapBindAudit.findAll(Sort.descending("createdAt")).page(Page.ofSize(safeLimit)).list();
    }

    public PageResult<LdapBindAudit> search(int page, int size, String bindDn, Boolean success,
                                            LocalDateTime from, LocalDateTime to) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        StringBuilder where = new StringBuilder(" where 1=1");
        Map<String, Object> parameters = new HashMap<>();
        if (bindDn != null && !bindDn.isBlank()) {
            where.append(" and lower(a.bindDn) like :bindDn");
            parameters.put("bindDn", "%" + bindDn.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (success != null) {
            where.append(" and a.success = :success");
            parameters.put("success", success);
        }
        if (from != null) {
            where.append(" and a.createdAt >= :from");
            parameters.put("from", from);
        }
        if (to != null) {
            where.append(" and a.createdAt <= :to");
            parameters.put("to", to);
        }
        TypedQuery<LdapBindAudit> data = entityManager.createQuery(
                "select a from LdapBindAudit a" + where + " order by a.createdAt desc", LdapBindAudit.class);
        TypedQuery<Long> count = entityManager.createQuery(
                "select count(a) from LdapBindAudit a" + where, Long.class);
        parameters.forEach((name, value) -> {
            data.setParameter(name, value);
            count.setParameter(name, value);
        });
        List<LdapBindAudit> records = data.setFirstResult(safePage * safeSize)
                .setMaxResults(safeSize).getResultList();
        return PageResult.of(records, count.getSingleResult(), safePage, safeSize);
    }

    @Scheduled(every = "24h", delayed = "10m")
    @Transactional
    public void deleteExpired() {
        int days = config.security().auditRetentionDays();
        if (days > 0) {
            LdapBindAudit.delete("createdAt < ?1", LocalDateTime.now().minusDays(days));
        }
    }
}
