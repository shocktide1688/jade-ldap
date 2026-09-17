package com.jade.admin.controller;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.jade.admin.aspect.Log;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户管理（CRUD）
 *
 * 权限：admin 角色可改，user 角色只能看自己
 */
@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "用户管理")
public class UserController {

    @Inject
    SysUserRepository userRepository;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/page")
    @Operation(summary = "分页查询用户 (按当前用户 data-scope 过滤)")
    @Transactional
    public R<PageResult<SysUser>> page(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("keyword") String keyword) {

        // 1) 拿当前登录用户 (用于 dataScope 决定)
        SysUser currentUser = identity == null || identity.isAnonymous()
                ? null
                : userRepository.findByUsername(identity.getPrincipal().getName()).orElse(null);
        String dataScope = userRepository.resolveCurrentUserDataScope(currentUser);

        // 2) 基础查询条件 (deleted + keyword), dataScope 在 findByDataScope 里追加
        String base = (keyword != null && !keyword.isBlank())
                ? "deleted = false and (username like ?1 or nickname like ?1 or email like ?1)"
                : "deleted = false";
        Object[] baseParams = (keyword != null && !keyword.isBlank())
                ? new Object[]{"%" + keyword + "%"}
                : new Object[]{};

        // 3) 拿带 data-scope 过滤的 query
        PanacheQuery<SysUser> q = currentUser == null
                ? userRepository.findByDataScope(new SysUser(), "ALL", base, baseParams) // 没登录, 用匿名 (不会真到这)
                : userRepository.findByDataScope(currentUser, dataScope, base, baseParams);

        long total = q.count();
        List<SysUser> records = q.page(Page.of(page - 1, size)).list();

        // 隐藏 password (先 flush 再 set null, 避免写回 DB)
        if (!records.isEmpty()) {
            userRepository.getEntityManager().flush();
            for (SysUser u : records) {
                u.password = null;
            }
        }

        return R.ok(PageResult.of(records, total, page, size));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "根据 ID 查询用户")
    @Transactional
    public R<SysUser> getById(@PathParam("id") Long id) {
        SysUser user = userRepository.findById(id);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        // flush 后清 password, 避免写回 DB
        userRepository.getEntityManager().flush();
        user.password = null;
        return R.ok(user);
    }

    @POST
    @Transactional
    @Log(title = "用户管理", businessType = 1)
    @Operation(summary = "创建用户")
    public R<SysUser> create(@Valid UserRequest req) {
        if (req == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求体为空");
        }
        if (req.username == null || req.username.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        if (req.password == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码不能为空");
        }
        if (userRepository.findByUsername(req.username).isPresent()) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.username = req.username;
        user.password = BCrypt.withDefaults().hashToString(10, req.password.toCharArray());
        user.nickname = req.nickname;
        user.email = req.email;
        user.phone = req.phone;
        user.status = req.status != null ? req.status : 1;
        user.tenantId = req.tenantId;
        userRepository.persist(user);
        userRepository.getEntityManager().flush();
        user.password = null;
        return R.ok(user);
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Log(title = "用户管理", businessType = 2)
    @Operation(summary = "更新用户")
    public R<SysUser> update(@PathParam("id") Long id, @Valid UserRequest req) {
        SysUser user = userRepository.findById(id);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        if (req.nickname != null) user.nickname = req.nickname;
        if (req.email != null) user.email = req.email;
        if (req.phone != null) user.phone = req.phone;
        if (req.status != null) user.status = req.status;
        if (req.tenantId != null) user.tenantId = req.tenantId;
        // 强制 flush UPDATE 写盘, 再清 password 避免写回
        userRepository.getEntityManager().flush();
        user.password = null;
        return R.ok(user);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Log(title = "用户管理", businessType = 3)
    @Operation(summary = "删除用户（软删）")
    public R<Void> delete(@PathParam("id") Long id) {
        SysUser user = userRepository.findById(id);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        if ("admin".equals(user.username)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能删除超级管理员");
        }
        user.deleted = true;
        userRepository.persist(user);
        return R.ok();
    }

    @PUT
    @Path("/{id}/reset-password")
    @Transactional
    @Log(title = "用户管理", businessType = 2)
    @Operation(summary = "重置密码")
    public R<Void> resetPassword(@PathParam("id") Long id, @QueryParam("newPassword") @NotBlank String newPassword) {
        SysUser user = userRepository.findById(id);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        String passwordHash = BCrypt.withDefaults().hashToString(10, newPassword.toCharArray());
        if (userRepository.updatePassword(user.id, passwordHash) != 1) {
            return R.fail(500, "密码更新失败");
        }
        return R.ok();
    }

    @PUT
    @Path("/{id}/status")
    @Transactional
    @Log(title = "用户管理", businessType = 2)
    @Operation(summary = "切换用户状态（启用/禁用）")
    public R<Void> changeStatus(@PathParam("id") Long id, @QueryParam("status") Short status) {
        SysUser user = userRepository.findById(id);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        if ("admin".equals(user.username)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能禁用超级管理员");
        }
        user.status = status;
        userRepository.persist(user);
        return R.ok();
    }

    @Data
    public static class UserRequest {
        @NotBlank(message = "用户名不能为空")
        public String username;
        public String password;  // 创建时必填
        public String nickname;
        public String email;
        public String phone;
        public Short status;
        public Long tenantId;
    }
}
