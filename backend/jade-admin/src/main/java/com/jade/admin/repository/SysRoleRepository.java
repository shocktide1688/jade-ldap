package com.jade.admin.repository;

import com.jade.admin.entity.SysRole;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SysRoleRepository implements PanacheRepository<SysRole> {

    @Inject
    EntityManager em;

    public List<SysRole> listByUserId(Long userId) {
        // 用 native SQL，因为 sys_user_role 是 join 表没 entity
        @SuppressWarnings("unchecked")
        List<Long> roleIds = em.createNativeQuery(
                "SELECT role_id FROM sys_user_role WHERE user_id = ?1")
                .setParameter(1, userId)
                .getResultList();
        if (roleIds.isEmpty()) return List.of();
        return list("id in ?1 and deleted = false", roleIds);
    }

    public Optional<SysRole> findByCode(String roleCode) {
        return find("roleCode = ?1 and deleted = false", roleCode).firstResultOptional();
    }
}
