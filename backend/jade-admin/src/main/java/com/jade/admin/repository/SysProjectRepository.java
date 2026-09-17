package com.jade.admin.repository;

import com.jade.admin.entity.SysProject;
import com.jade.security.context.TenantContext;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class SysProjectRepository implements PanacheRepository<SysProject> {

    /**
     * 自动按当前租户过滤
     */
    public List<SysProject> listByCurrentTenant() {
        Long tenantId = TenantContext.require();
        return list("tenantId = ?1", tenantId);
    }
}
