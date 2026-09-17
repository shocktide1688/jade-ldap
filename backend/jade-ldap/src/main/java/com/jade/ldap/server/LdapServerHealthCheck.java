package com.jade.ldap.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class LdapServerHealthCheck implements HealthCheck {

    @Inject
    LdapDirectoryServer server;

    @Override
    public HealthCheckResponse call() {
        if (!server.isRunning()) {
            return HealthCheckResponse.down("Jade LDAP protocol listener");
        }
        if (server.persistenceFailure() != null) {
            return HealthCheckResponse.named("Jade LDAP protocol listener")
                    .down()
                    .withData("persistence", "failed")
                    .build();
        }
        return HealthCheckResponse.named("Jade LDAP protocol listener")
                .up()
                .withData("port", server.listenPort())
                .withData("ldapsPort", server.ldapsPort())
                .build();
    }
}
