package com.jade.ldap.api;

import com.jade.common.api.R;
import com.jade.ldap.service.DirectoryUserService;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/directory/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "LDAP 目录用户")
@RolesAllowed("admin")
public class DirectoryUserController {

    @Inject
    DirectoryUserService userService;

    @GET
    @Operation(summary = "列出 LDAP 用户")
    public R<List<DirectoryUserView>> list() throws LDAPException {
        return R.ok(userService.list());
    }

    @GET
    @Path("/{uid}")
    @Operation(summary = "查询 LDAP 用户")
    public R<DirectoryUserView> get(@PathParam("uid") String uid) {
        try {
            return R.ok(userService.get(uid));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户不存在");
        }
    }

    @POST
    @Operation(summary = "创建 LDAP 用户")
    public R<DirectoryUserView> create(@Valid DirectoryUserRequest request) {
        try {
            return R.ok(userService.create(request));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            if (ResultCode.ENTRY_ALREADY_EXISTS.equals(e.getResultCode())) {
                return R.fail(409, "LDAP 用户已存在");
            }
            return R.fail(400, "LDAP 用户创建失败: " + e.getResultCode().getName());
        }
    }

    @PUT
    @Path("/{uid}")
    @Operation(summary = "修改 LDAP 用户资料")
    public R<DirectoryUserView> update(@PathParam("uid") String uid,
                                       @Valid DirectoryUserUpdateRequest request) {
        try {
            return R.ok(userService.update(uid, request));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户不存在");
        }
    }

    @PUT
    @Path("/{uid}/password")
    @Operation(summary = "修改 LDAP 用户密码")
    public R<Void> changePassword(@PathParam("uid") String uid, @Valid PasswordChangeRequest request) {
        try {
            userService.changePassword(uid, request.password());
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户不存在");
        }
    }

    @DELETE
    @Path("/{uid}")
    @Operation(summary = "删除 LDAP 用户")
    public R<Void> delete(@PathParam("uid") String uid) {
        try {
            userService.delete(uid);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户不存在");
        }
    }
}
