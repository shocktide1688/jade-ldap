package com.jade.ldap.directory;

import com.jade.ldap.server.LdapDirectoryServer;
import com.unboundid.ldap.sdk.LDAPException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DirectoryWriteCoordinator {

    @Inject
    LdapDirectoryStore store;

    @Inject
    LdapDirectoryServer server;

    @Transactional
    public <T> T write(LdapWrite<T> operation) throws LDAPException {
        store.acquireWriteLock();
        server.reloadFromDatabase();
        T result = operation.run();
        server.markDatabaseRevision(store.replaceAll(server.snapshotEntries()));
        return result;
    }

    @FunctionalInterface
    public interface LdapWrite<T> {
        T run() throws LDAPException;
    }
}
