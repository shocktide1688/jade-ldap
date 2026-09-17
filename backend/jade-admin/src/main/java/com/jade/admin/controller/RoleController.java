package com.jade.admin.controller;

import com.jade.admin.aspect.Log;
import com.jade.admin.entity.SysRole;
import com.jade.admin.repository.SysRoleMenuRepository;
import com.jade.admin.repository.SysRoleRepository;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import com.jade.common.exception.BizException;
import com.jade.common.constant.ResultCode;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "角色管理")
@RolesAllowed("admin")
public class RoleController {

    @Inject
    SysRoleRepository roleRepository;

    @Inject
    SysRoleMenuRepository roleMenuRepository;

    @GET
    @Path("/page")
    @Operation(summary = "分页查询角色")
    public R<PageResult<SysRole>> page(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("keyword") String keyword) {
        String query = (keyword != null && !keyword.isBlank())
                ? "deleted = false and (roleName like ?1 or roleCode like ?1)"
                : "deleted = false";
        Object[] params = (keyword != null && !keyword.isBlank())
                ? new Object[]{"%" + keyword + "%"}
                : new Object[]{};
        PanacheQuery<SysRole> q = roleRepository.find(query, Sort.by("roleSort").ascending(), params);
        long total = q.count();
        List<SysRole> records = q.page(Page.of(page - 1, size)).list();
        return R.ok(PageResult.of(records, total, page, size));
    }

    @GET
    @Path("/all")
    @Operation(summary = "查询所有启用的角色（下拉用）")
    public R<List<SysRole>> all() {
        return R.ok(roleRepository.list("deleted = false and status = 1"));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "查询角色详情")
    public R<SysRole> getById(@PathParam("id") Long id) {
        SysRole role = roleRepository.findById(id);
        if (role == null) return R.fail(404, "角色不存在");
        return R.ok(role);
    }

    @POST
    @Transactional
    @Log(title = "角色管理", businessType = 1)
    @Operation(summary = "创建角色")
    public R<SysRole> create(SysRole role) {
        if (roleRepository.findByCode(role.roleCode).isPresent()) {
            throw new BizException(ResultCode.BAD_REQUEST, "角色代码已存在");
        }
        role.id = null;
        if (role.dataScope == null) role.dataScope = "ALL";
        if (role.status == null) role.status = 1;
        roleRepository.persist(role);
        return R.ok(role);
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Log(title = "角色管理", businessType = 2)
    @Operation(summary = "更新角色")
    public R<SysRole> update(@PathParam("id") Long id, SysRole req) {
        SysRole role = roleRepository.findById(id);
        if (role == null) return R.fail(404, "角色不存在");
        if (req.roleName != null) role.roleName = req.roleName;
        if (req.roleSort != null) role.roleSort = req.roleSort;
        if (req.dataScope != null) role.dataScope = req.dataScope;
        if (req.status != null) role.status = req.status;
        if (req.remark != null) role.remark = req.remark;
        roleRepository.persist(role);
        return R.ok(role);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Log(title = "角色管理", businessType = 3)
    @Operation(summary = "删除角色")
    public R<Void> delete(@PathParam("id") Long id) {
        SysRole role = roleRepository.findById(id);
        if (role == null) return R.fail(404, "角色不存在");
        if ("admin".equals(role.roleCode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除超级管理员角色");
        }
        role.deleted = true;
        roleRepository.persist(role);
        roleMenuRepository.deleteByRoleId(id);
        return R.ok();
    }

    @GET
    @Path("/{id}/menus")
    @Operation(summary = "查询角色绑定的菜单 ID 列表")
    public R<List<Long>> getMenuIds(@PathParam("id") Long id) {
        return R.ok(roleMenuRepository.listMenuIdsByRoleId(id));
    }

    @PUT
    @Path("/{id}/menus")
    @Transactional
    @Log(title = "角色管理", businessType = 2)
    @Operation(summary = "分配角色菜单权限")
    public R<Void> assignMenus(@PathParam("id") Long id, MenuIdsRequest req) {
        SysRole role = roleRepository.findById(id);
        if (role == null) return R.fail(404, "角色不存在");
        roleMenuRepository.deleteByRoleId(id);
        if (req.menuIds != null) {
            for (Long menuId : req.menuIds) {
                roleMenuRepository.insert(id, menuId);
            }
        }
        return R.ok();
    }

    @Data
    public static class MenuIdsRequest {
        public List<Long> menuIds;
    }
}
