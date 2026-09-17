package com.jade.admin.controller;

import com.jade.admin.aspect.Log;
import com.jade.admin.entity.SysMenu;
import com.jade.admin.repository.SysMenuRepository;
import com.jade.common.api.R;
import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/v1/menus")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "菜单管理")
@RolesAllowed("admin")
public class MenuController {

    @Inject
    SysMenuRepository menuRepository;

    @Inject
    SysUserRepository userRepository;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/tree")
    @Operation(summary = "菜单树（全部，用于管理）")
    public R<List<Map<String, Object>>> tree() {
        List<SysMenu> all = menuRepository.list("deleted = false", io.quarkus.panache.common.Sort.by("sortOrder"));
        return R.ok(buildTree(all, 0L));
    }

    @GET
    @Path("/router")
    @Operation(summary = "当前用户的菜单树（前端动态路由）")
    public R<List<Map<String, Object>>> router() {
        if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
            return R.ok(List.of());
        }
        String username = identity.getPrincipal().getName();
        SysUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return R.ok(List.of());

        List<SysMenu> menus = menuRepository.listByUserId(user.id);
        return R.ok(buildRouter(menus, 0L));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "查询菜单详情")
    public R<SysMenu> getById(@PathParam("id") Long id) {
        SysMenu menu = menuRepository.findById(id);
        if (menu == null) return R.fail(404, "菜单不存在");
        return R.ok(menu);
    }

    @POST
    @Transactional
    @Log(title = "菜单管理", businessType = 1)
    @Operation(summary = "创建菜单")
    public R<SysMenu> create(SysMenu menu) {
        menu.id = null;
        if (menu.parentId == null) menu.parentId = 0L;
        if (menu.status == null) menu.status = 1;
        if (menu.visible == null) menu.visible = 1;
        if (menu.sortOrder == null) menu.sortOrder = 0;
        menuRepository.persist(menu);
        return R.ok(menu);
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Log(title = "菜单管理", businessType = 2)
    @Operation(summary = "更新菜单")
    public R<SysMenu> update(@PathParam("id") Long id, SysMenu req) {
        SysMenu menu = menuRepository.findById(id);
        if (menu == null) return R.fail(404, "菜单不存在");
        if (req.parentId != null) menu.parentId = req.parentId;
        if (req.menuName != null) menu.menuName = req.menuName;
        if (req.menuType != null) menu.menuType = req.menuType;
        if (req.path != null) menu.path = req.path;
        if (req.component != null) menu.component = req.component;
        if (req.icon != null) menu.icon = req.icon;
        if (req.perms != null) menu.perms = req.perms;
        if (req.sortOrder != null) menu.sortOrder = req.sortOrder;
        if (req.visible != null) menu.visible = req.visible;
        if (req.status != null) menu.status = req.status;
        if (req.isCache != null) menu.isCache = req.isCache;
        menuRepository.persist(menu);
        return R.ok(menu);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Log(title = "菜单管理", businessType = 3)
    @Operation(summary = "删除菜单（软删）")
    public R<Void> delete(@PathParam("id") Long id) {
        SysMenu menu = menuRepository.findById(id);
        if (menu == null) return R.fail(404, "菜单不存在");
        menu.deleted = true;
        menuRepository.persist(menu);
        return R.ok();
    }

    // ---- helpers ----

    private List<Map<String, Object>> buildTree(List<SysMenu> all, Long parentId) {
        return all.stream()
                .filter(m -> parentId.equals(m.parentId))
                .sorted(Comparator.comparing(m -> m.sortOrder == null ? 0 : m.sortOrder))
                .map(m -> {
                    Map<String, Object> node = new java.util.LinkedHashMap<>();
                    node.put("id", m.id);
                    node.put("parentId", m.parentId);
                    node.put("menuName", m.menuName);
                    node.put("menuType", m.menuType);
                    node.put("path", m.path);
                    node.put("component", m.component);
                    node.put("icon", m.icon);
                    node.put("perms", m.perms);
                    node.put("sortOrder", m.sortOrder);
                    node.put("visible", m.visible);
                    node.put("status", m.status);
                    node.put("children", buildTree(all, m.id));
                    return node;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRouter(List<SysMenu> all, Long parentId) {
        // 路由只显示 C 类型（菜单），按钮 F 和目录 M 不进路由
        return all.stream()
                .filter(m -> parentId.equals(m.parentId) && "M".equals(m.menuType) || parentId.equals(m.parentId) && "C".equals(m.menuType))
                .sorted(Comparator.comparing(m -> m.sortOrder == null ? 0 : m.sortOrder))
                .map(m -> {
                    Map<String, Object> node = new java.util.LinkedHashMap<>();
                    node.put("id", m.id);
                    node.put("parentId", m.parentId);
                    node.put("name", m.menuName);
                    node.put("path", m.path);
                    node.put("component", m.component);
                    node.put("icon", m.icon);
                    node.put("hidden", m.visible != null && m.visible == 0);
                    node.put("keepAlive", m.isCache != null && m.isCache == 1);
                    List<Map<String, Object>> children = buildRouter(all, m.id);
                    if (!children.isEmpty()) {
                        node.put("children", children);
                    }
                    if ("M".equals(m.menuType)) {
                        node.put("redirect", "noRedirect");
                    }
                    return node;
                })
                .collect(Collectors.toList());
    }
}
