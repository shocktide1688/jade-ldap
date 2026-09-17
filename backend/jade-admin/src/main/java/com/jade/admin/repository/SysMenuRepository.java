package com.jade.admin.repository;

import com.jade.admin.entity.SysMenu;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class SysMenuRepository implements PanacheRepository<SysMenu> {

    @Inject
    EntityManager em;

    /** 根据用户 ID 查出所有菜单（含按钮），按 sortOrder 排序 */
    public List<SysMenu> listByUserId(Long userId) {
        // 1. 取用户所有 role_id
        @SuppressWarnings("unchecked")
        List<Long> roleIds = em.createNativeQuery(
                "SELECT role_id FROM sys_user_role WHERE user_id = ?1")
                .setParameter(1, userId)
                .getResultList();
        if (roleIds.isEmpty()) return List.of();

        // 2. 从 role_id 取 menu_id
        @SuppressWarnings("unchecked")
        List<Long> menuIds = em.createNativeQuery(
                "SELECT DISTINCT menu_id FROM sys_role_menu WHERE role_id IN (?1)")
                .setParameter(1, roleIds)
                .getResultList();
        if (menuIds.isEmpty()) return List.of();

        // 3. 从 menu_id 查菜单
        return list("id in ?1 and deleted = false and status = 1 order by parentId, sortOrder", menuIds);
    }

    /** 角色绑定的所有 menu_id */
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        @SuppressWarnings("unchecked")
        List<Long> ids = em.createNativeQuery(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ?1")
                .setParameter(1, roleId)
                .getResultList();
        return ids;
    }
}
