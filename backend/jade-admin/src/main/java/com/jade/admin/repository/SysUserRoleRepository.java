package com.jade.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

@ApplicationScoped
public class SysUserRoleRepository implements PanacheRepository<Object> {

    @Inject
    EntityManager em;

    public void deleteByUserId(Long userId) {
        em.createNativeQuery("DELETE FROM sys_user_role WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
    }

    public void insert(Long userId, Long roleId) {
        em.createNativeQuery("INSERT INTO sys_user_role (user_id, role_id) VALUES (?1, ?2) ON CONFLICT DO NOTHING")
                .setParameter(1, userId)
                .setParameter(2, roleId)
                .executeUpdate();
    }
}
