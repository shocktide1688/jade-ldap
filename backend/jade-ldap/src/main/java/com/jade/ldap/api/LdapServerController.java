package com.jade.ldap.api;

import com.jade.common.api.R;
import com.jade.ldap.server.LdapDirectoryServer;
import com.jade.ldap.server.LdapServerConfig;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/directory/server")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "LDAP Server")
@RolesAllowed("admin")
public class LdapServerController {

    @Inject
    LdapDirectoryServer server;

    @Inject
    LdapServerConfig config;

    @GET
    @Operation(summary = "LDAP Server 运行状态")
    public R<ServerStatus> status() {
        return R.ok(new ServerStatus(
                server.isRunning(),
                server.listenPort(),
                server.ldapsPort(),
                config.tls().enabled(),
                config.tls().startTlsEnabled(),
                config.baseDn(),
                server.directory().countEntries(),
                server.persistenceFailure() == null));
    }

    @POST
    @Path("/tls/reload")
    @Operation(summary = "重新加载 LDAP TLS 证书")
    public R<Void> reloadTlsCertificate() {
        server.reloadCertificate();
        return R.ok();
    }

    public record ServerStatus(
            boolean running,
            int ldapPort,
            int ldapsPort,
            boolean tlsEnabled,
            boolean startTlsEnabled,
            String baseDn,
            int entries,
            boolean persistenceHealthy
    ) {
    }
}
