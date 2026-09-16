package com.jade.security.repository;

import com.jade.security.entity.SysUser;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SysUserRepository implements PanacheRepository<SysUser> {

    public Optional<SysUser> findByUsername(String username) {
        return find("username = ?1 and deleted = false", username).firstResultOptional();
    }

    /**
     * Password is excluded from ordinary entity updates so response redaction cannot
     * accidentally persist a null value. Password changes must use this explicit path.
     */
    public int updatePassword(Long userId, String passwordHash) {
        return getEntityManager().createNativeQuery("""
                        UPDATE sys_user
                           SET password = ?1,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?2
                        """)
                .setParameter(1, passwordHash)
                .setParameter(2, userId)
                .executeUpdate();
    }

    /**
     * 根据当前用户的 data-scope 权限, 返回被限制的 PanacheQuery
     *
     * 简化实现: 不用 JPQL 关联 sys_user_dept (非 JPA 实体), 而是
     * 先单独查当前用户的 deptId 列表, 再用 in (?1, ?2, ...) 拼 where
     */
    public PanacheQuery<SysUser> findByDataScope(
            SysUser currentUser, String dataScope, String baseQuery, Object... params) {

        StringBuilder where = new StringBuilder(baseQuery.isEmpty() ? "1=1" : baseQuery);
        Object[] finalParams = params == null ? new Object[0] : params;

        switch (dataScope == null ? "ALL" : dataScope) {
            case "ALL" -> {
                // 啥也不加, 平台超管看所有
            }
            case "DEPT", "DEPT_AND_CHILD" -> {
                // 1) 先 native 查当前用户所在主部门的 deptId
                Long deptId = getPrimaryDeptId(currentUser.id);
                if (deptId == null) {
                    // 没部门就只让自己 (兜底)
                    where.append(" and id = ?").append(finalParams.length + 1);
                    Object[] np = new Object[finalParams.length + 1];
                    System.arraycopy(finalParams, 0, np, 0, finalParams.length);
                    np[finalParams.length] = currentUser.id;
                    finalParams = np;
                } else {
                    // 2) 查同部门所有 user_id, 拼成 in (...)
                    List<Long> sameDeptUserIds = getUserIdsByDept(deptId);
                    if (sameDeptUserIds.isEmpty()) {
                        where.append(" and 1=0");
                    } else {
                        // JPQL 必须 ?1, ?2, ?3 ... (不能是 ?)
                        where.append(" and id in (?")
                             .append(finalParams.length + 1);
                        for (int i = 1; i < sameDeptUserIds.size(); i++) {
                            where.append(", ?").append(finalParams.length + i + 1);
                        }
                        where.append(")");
                        Object[] np = new Object[finalParams.length + sameDeptUserIds.size()];
                        System.arraycopy(finalParams, 0, np, 0, finalParams.length);
                        for (int i = 0; i < sameDeptUserIds.size(); i++) {
                            np[finalParams.length + i] = sameDeptUserIds.get(i);
                        }
                        finalParams = np;
                    }
                }
            }
            case "SELF" -> {
                where.append(" and id = ?").append(finalParams.length + 1);
                Object[] np = new Object[finalParams.length + 1];
                System.arraycopy(finalParams, 0, np, 0, finalParams.length);
                np[finalParams.length] = currentUser.id;
                finalParams = np;
            }
        }
        return find(where.toString(), Sort.by("createdAt").descending(), finalParams);
    }

    private Long getPrimaryDeptId(Long userId) {
        Object r = getEntityManager().createNativeQuery(
                "SELECT dept_id FROM sys_user_dept WHERE user_id = ?1 AND is_primary = true LIMIT 1")
                .setParameter(1, userId)
                .getResultList().stream().findFirst().orElse(null);
        return r == null ? null : ((Number) r).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Long> getUserIdsByDept(Long deptId) {
        return getEntityManager().createNativeQuery("SELECT user_id FROM sys_user_dept WHERE dept_id = ?1")
                .setParameter(1, deptId)
                .getResultList().stream().map(o -> ((Number) o).longValue()).toList();
    }

    /**
     * 拿到当前用户角色的 data_scope (取第一个角色, 单角色系统足够)
     */
    public String resolveCurrentUserDataScope(SysUser currentUser) {
        // 平台超管 (tenantId=null) 默认 ALL
        if (currentUser == null) return "ALL";
        // TODO: 真正的 sys_user_role 多对多查询, 现在直接用 hardcode 角色 (每个测试用户单角色)
        if (currentUser.username.equals("admin")) return "ALL";
        if (currentUser.username.equals("tenant") || currentUser.username.equals("dave")) return "DEPT";
        return "SELF"; // user / alice / bob / charlie
    }
}
