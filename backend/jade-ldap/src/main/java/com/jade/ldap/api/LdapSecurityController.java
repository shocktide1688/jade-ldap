package com.jade.ldap.api;

import com.jade.common.api.R;
import com.jade.common.api.PageResult;
import com.jade.ldap.audit.LdapBindAudit;
import com.jade.ldap.audit.LdapBindAuditService;
import com.jade.ldap.server.LdapBindSecurityService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.time.LocalDateTime;

@Path("/api/v1/directory/security")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "LDAP 安全与审计")
@RolesAllowed("admin")
public class LdapSecurityController {

    @Inject
    LdapBindAuditService audits;

    @Inject
    LdapBindSecurityService security;

    @GET
    @Path("/bind-audits")
    @Operation(summary = "分页查询 LDAP Bind 审计")
    public R<PageResult<LdapBindAudit>> audits(@QueryParam("page") Integer page,
                                                @QueryParam("size") Integer size,
                                                @QueryParam("bindDn") String bindDn,
                                                @QueryParam("success") Boolean success,
                                                @QueryParam("from") LocalDateTime from,
                                                @QueryParam("to") LocalDateTime to) {
        return R.ok(audits.search(page == null ? 0 : page, size == null ? 50 : size,
                bindDn, success, from, to));
    }

    @GET
    @Path("/locks")
    @Operation(summary = "查询 LDAP Bind 失败与锁定状态")
    public R<List<LdapBindSecurityService.LockView>> locks() {
        return R.ok(security.locks());
    }

    @DELETE
    @Path("/locks")
    @Operation(summary = "解除 LDAP 账号锁定")
    public R<Boolean> unlock(@QueryParam("bindDn") String bindDn) {
        if (bindDn == null || bindDn.isBlank()) return R.fail(400, "bindDn 必填");
        return R.ok(security.unlock(bindDn));
    }
}
