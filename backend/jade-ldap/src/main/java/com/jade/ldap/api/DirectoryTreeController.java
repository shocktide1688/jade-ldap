package com.jade.ldap.api;

import com.jade.common.api.R;
import com.jade.ldap.service.DirectoryTreeService;
import com.unboundid.ldap.sdk.LDAPException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/directory/tree")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "LDAP 目录树")
@RolesAllowed("admin")
public class DirectoryTreeController {

    @Inject
    DirectoryTreeService trees;

    @GET
    @Operation(summary = "查询 LDAP 目录树")
    public R<DirectoryTreeNode> tree() throws LDAPException {
        return R.ok(trees.tree());
    }
}
