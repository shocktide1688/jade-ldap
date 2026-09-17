package com.jade.admin.controller;

import com.jade.admin.aspect.Log;
import com.jade.admin.entity.SysProject;
import com.jade.admin.repository.SysProjectRepository;
import com.jade.common.api.R;
import com.jade.security.context.TenantContext;

import com.jade.web.repository.SysTenantRepository;
import com.jade.web.entity.SysTenant;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * 租户 + 项目管理
 *
 * 权限：
 *   - 租户管理（/tenants）：仅 admin
 *   - 项目管理（/projects）：任何登录用户，只能操作自己租户
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "租户/项目")
@Authenticated
public class TenantController {

    @Inject
    SysTenantRepository tenantRepository;

    @Inject
    SysProjectRepository projectRepository;

    @Inject
    SecurityIdentity identity;

    // ============ 租户管理（仅 admin）==========

    @GET
    @Path("/tenants")
    @RolesAllowed("admin")
    @Operation(summary = "查询所有租户（仅管理员）")
    public R<List<SysTenant>> listTenants() {
        return R.ok(tenantRepository.listAll());
    }

    @GET
    @Path("/tenants/{id}")
    @RolesAllowed("admin")
    @Operation(summary = "租户详情")
    public R<SysTenant> getTenant(@PathParam("id") Long id) {
        SysTenant t = tenantRepository.findById(id);
        if (t == null) return R.fail(404, "租户不存在");
        return R.ok(t);
    }

    @POST
    @Path("/tenants")
    @RolesAllowed("admin")
    @Transactional
    @Log(title = "租户管理", businessType = 1)
    @Operation(summary = "创建租户（仅管理员）")
    public R<SysTenant> createTenant(SysTenant tenant) {
        tenant.id = null;
        if (tenant.status == null) tenant.status = 1;
        tenantRepository.persist(tenant);
        return R.ok(tenant);
    }

    @PUT
    @Path("/tenants/{id}")
    @RolesAllowed("admin")
    @Transactional
    @Log(title = "租户管理", businessType = 2)
    @Operation(summary = "更新租户")
    public R<SysTenant> updateTenant(@PathParam("id") Long id, SysTenant req) {
        SysTenant t = tenantRepository.findById(id);
        if (t == null) return R.fail(404, "租户不存在");
        if (req.code != null) t.code = req.code;
        if (req.name != null) t.name = req.name;
        if (req.status != null) t.status = req.status;
        tenantRepository.persist(t);
        return R.ok(t);
    }

    @DELETE
    @Path("/tenants/{id}")
    @RolesAllowed("admin")
    @Transactional
    @Log(title = "租户管理", businessType = 3)
    @Operation(summary = "删除租户")
    public R<Void> deleteTenant(@PathParam("id") Long id) {
        SysTenant t = tenantRepository.findById(id);
        if (t == null) return R.fail(404, "租户不存在");
        tenantRepository.delete(t);
        return R.ok();
    }

    // ============ 项目（登录用户，只能看自己租户的）==========

    @GET
    @Path("/projects")
    @Operation(summary = "查询当前租户的项目")
    public R<List<SysProject>> listProjects() {
        Long tenantId = TenantContext.require();
        return R.ok(projectRepository.list("tenantId = ?1 and deleted = false", tenantId));
    }

    @POST
    @Path("/projects")
    @Transactional
    @Log(title = "项目管理", businessType = 1)
    @Operation(summary = "创建项目（自动绑当前用户租户）")
    public R<SysProject> createProject(SysProject project) {
        Long tenantId = TenantContext.require();
        project.id = null;
        project.tenantId = tenantId;
        if (project.deleted == null) project.deleted = false;
        projectRepository.persist(project);
        return R.ok(project);
    }

    @DELETE
    @Path("/projects/{id}")
    @Transactional
    @Log(title = "项目管理", businessType = 3)
    @Operation(summary = "删除项目")
    public R<Void> deleteProject(@PathParam("id") Long id) {
        Long tenantId = TenantContext.require();
        SysProject p = projectRepository.findById(id);
        if (p == null) return R.fail(404, "项目不存在");
        if (!tenantId.equals(p.tenantId)) return R.fail(403, "无权操作其他租户的项目");
        p.deleted = true;
        projectRepository.persist(p);
        return R.ok();
    }
}
