package com.jade.ldap.api;

import com.jade.common.api.R;
import com.jade.ldap.service.DirectoryGroupService;
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

@Path("/api/v1/directory/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "LDAP 目录用户组")
@RolesAllowed("admin")
public class DirectoryGroupController {

    @Inject
    DirectoryGroupService groups;

    @GET
    @Operation(summary = "列出 LDAP 用户组")
    public R<List<DirectoryGroupView>> list() throws LDAPException {
        return R.ok(groups.list());
    }

    @GET
    @Path("/{name}")
    @Operation(summary = "查询 LDAP 用户组")
    public R<DirectoryGroupView> get(@PathParam("name") String name) {
        try {
            return R.ok(groups.get(name));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户组不存在");
        }
    }

    @POST
    @Operation(summary = "创建 LDAP 用户组")
    public R<DirectoryGroupView> create(@Valid DirectoryGroupRequest request) {
        try {
            return R.ok(groups.create(request));
        } catch (LDAPException e) {
            if (ResultCode.ENTRY_ALREADY_EXISTS.equals(e.getResultCode())) {
                return R.fail(409, "LDAP 用户组已存在");
            }
            return R.fail(400, "LDAP 用户组创建失败: " + e.getResultCode().getName());
        }
    }

    @PUT
    @Path("/{name}")
    @Operation(summary = "修改 LDAP 用户组")
    public R<DirectoryGroupView> update(@PathParam("name") String name,
                                        @Valid DirectoryGroupUpdateRequest request) {
        try {
            return R.ok(groups.update(name, request));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户组不存在");
        }
    }

    @PUT
    @Path("/{name}/members/{uid}")
    @Operation(summary = "添加用户组成员")
    public R<Void> addMember(@PathParam("name") String name, @PathParam("uid") String uid) {
        try {
            groups.addMember(name, uid);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户或用户组不存在");
        }
    }

    @DELETE
    @Path("/{name}/members/{uid}")
    @Operation(summary = "移除用户组成员")
    public R<Void> removeMember(@PathParam("name") String name, @PathParam("uid") String uid) {
        try {
            groups.removeMember(name, uid);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户组不存在");
        }
    }

    @DELETE
    @Path("/{name}")
    @Operation(summary = "删除 LDAP 用户组")
    public R<Void> delete(@PathParam("name") String name) {
        try {
            groups.delete(name);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        } catch (LDAPException e) {
            return R.fail(404, "LDAP 用户组不存在");
        }
    }
}
