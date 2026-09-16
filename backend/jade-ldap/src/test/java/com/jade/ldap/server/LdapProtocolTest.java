package com.jade.ldap.server;

import com.jade.ldap.api.DirectoryUserRequest;
import com.jade.ldap.api.DirectoryGroupRequest;
import com.jade.ldap.service.DirectoryGroupService;
import com.jade.ldap.service.DirectoryTreeService;
import com.jade.ldap.service.DirectoryUserService;
import com.jade.ldap.security.LdapAccountLock;
import com.jade.ldap.audit.LdapBindAudit;
import com.jade.ldap.audit.LdapBindAuditService;
import com.jade.ldap.directory.LdapDirectoryEntry;
import com.jade.ldap.directory.LdapDirectoryStore;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(LdapProtocolTest.Profile.class)
class LdapProtocolTest {

    @Inject
    LdapDirectoryServer server;

    @Inject
    DirectoryUserService users;

    @Inject
    DirectoryGroupService groups;

    @Inject
    DirectoryTreeService trees;

    @Inject
    LdapServerConfig config;

    @Test
    void servesBindAndSearchWhileRejectingProtocolWrites() throws Exception {
        String uid = "protocol-test";
        String peopleDn = "ou=people," + config.baseDn();
        String userDn = "uid=" + uid + "," + peopleDn;
        try {
            users.create(new DirectoryUserRequest(
                    uid, "Protocol Test", "Test", "protocol-test@jade.test", "TestPassword123!"));

            try (LDAPConnection connection = new LDAPConnection("127.0.0.1", server.listenPort())) {
                connection.bind(userDn, "TestPassword123!");
                Entry entry = connection.searchForEntry(
                        peopleDn, SearchScope.ONE, "(uid=" + uid + ")", "uid", "mail");
                assertEquals(uid, entry.getAttributeValue("uid"));
                assertEquals("protocol-test@jade.test", entry.getAttributeValue("mail"));

                LDAPException denied = assertThrows(LDAPException.class,
                        () -> connection.add("dn: uid=forbidden," + peopleDn,
                                "objectClass: inetOrgPerson", "uid: forbidden", "cn: Forbidden", "sn: Forbidden"));
                assertTrue(ResultCode.UNWILLING_TO_PERFORM.equals(denied.getResultCode())
                        || ResultCode.INSUFFICIENT_ACCESS_RIGHTS.equals(denied.getResultCode()));
            }
        } finally {
            try {
                users.delete(uid);
            } catch (LDAPException ignored) {
                // The test cleanup is intentionally idempotent.
            }
        }
    }

    @Test
    void managesGroupsMembershipAndDirectoryTree() throws Exception {
        String uid = "group-test-user";
        String groupName = "engineering-test";
        try {
            users.create(new DirectoryUserRequest(
                    uid, "Group Test", "Test", "group-test@jade.test", "TestPassword123!"));
            groups.create(new DirectoryGroupRequest(groupName, "Engineering test group"));
            groups.addMember(groupName, uid);

            assertEquals(List.of(uid), groups.get(groupName).members());
            assertTrue(trees.tree().children().stream()
                    .filter(node -> "groups".equals(node.name()))
                    .flatMap(node -> node.children().stream())
                    .anyMatch(node -> groupName.equals(node.name())));

            try (LDAPConnection connection = new LDAPConnection("127.0.0.1", server.listenPort())) {
                connection.bind(config.adminDn(), config.adminPassword());
                Entry entry = connection.searchForEntry(
                        "ou=groups," + config.baseDn(), SearchScope.ONE,
                        "(cn=" + groupName + ")", "cn", "roleOccupant");
                assertEquals(users.userDn(uid), entry.getAttributeValue("roleOccupant"));
            }

            users.delete(uid);
            assertTrue(groups.get(groupName).members().isEmpty());
        } finally {
            try {
                groups.delete(groupName);
            } catch (LDAPException ignored) {
                // Cleanup is idempotent.
            }
            try {
                users.delete(uid);
            } catch (LDAPException ignored) {
                // Cleanup is idempotent.
            }
        }
    }

    @Test
    void servesLdapsWithConfiguredCertificate() throws Exception {
        assertTrue(server.ldapsPort() > 0);
        SSLUtil ssl = new SSLUtil(new TrustAllTrustManager());
        try (LDAPConnection connection = new LDAPConnection(
                ssl.createSSLSocketFactory(), "127.0.0.1", server.ldapsPort())) {
            connection.bind(config.adminDn(), config.adminPassword());
            Entry root = connection.getEntry(config.baseDn(), "objectClass");
            assertTrue(root.hasObjectClass("domain"));
        }
    }

    @Test
    void servesStartTlsAndPagedSearch() throws Exception {
        SSLUtil ssl = new SSLUtil(new TrustAllTrustManager());
        try (LDAPConnection connection = new LDAPConnection("127.0.0.1", server.listenPort())) {
            connection.processExtendedOperation(new StartTLSExtendedRequest(ssl.createSSLSocketFactory()));
            connection.bind(config.adminDn(), config.adminPassword());
            SearchRequest firstRequest = new SearchRequest(config.baseDn(), SearchScope.SUB, "(objectClass=*)", "dn");
            firstRequest.addControl(new SimplePagedResultsControl(1));
            SearchResult first = connection.search(firstRequest);
            SimplePagedResultsControl response = SimplePagedResultsControl.get(first);
            assertEquals(1, first.getEntryCount());
            assertTrue(response != null && response.moreResultsToReturn());
            SearchRequest remainingRequest = new SearchRequest(config.baseDn(), SearchScope.SUB, "(objectClass=*)", "dn");
            remainingRequest.addControl(new SimplePagedResultsControl(10, response.getCookie()));
            SearchResult remaining = connection.search(remainingRequest);
            assertTrue(remaining.getEntryCount() >= 2);
        }
    }

    @Test
    void restoresDirectoryFromPostgresqlSourceOfTruth() throws Exception {
        String uid = "postgres-source-test";
        try {
            users.create(new DirectoryUserRequest(uid, "Postgres Source", "Test", null, "SourcePassword123!"));
            String dn = users.userDn(uid);
            server.directory().delete(dn);
            assertTrue(server.directory().getEntry(dn) == null);
            server.reloadFromDatabase();
            assertEquals(uid, server.directory().getEntry(dn).getAttributeValue("uid"));
        } finally {
            try { users.delete(uid); } catch (LDAPException ignored) { }
        }
    }

    @Test
    void locksRepeatedFailedBindsAndSupportsAdministrativeUnlock() throws Exception {
        String uid = "lock-test-user";
        String dn = "uid=" + uid + ",ou=people," + config.baseDn();
        try {
            users.create(new DirectoryUserRequest(uid, "Lock Test", "Test", null, "CorrectPassword123!"));
            for (int i = 0; i < 3; i++) {
                try (LDAPConnection connection = new LDAPConnection("127.0.0.1", server.listenPort())) {
                    assertThrows(LDAPException.class, () -> connection.bind(dn, "WrongPassword123!"));
                }
            }
            assertTrue(bindSecurity.isLocked(dn));
            assertEquals(1, LdapAccountLock.count("bindDn", dn));
            Instant lockedUntil = bindSecurity.locks().stream()
                    .filter(lock -> dn.equals(lock.bindDn()))
                    .findFirst().orElseThrow().lockedUntil();
            try (LDAPConnection connection = new LDAPConnection("127.0.0.1", server.listenPort())) {
                assertThrows(LDAPException.class, () -> connection.bind(dn, "CorrectPassword123!"));
            }
            assertEquals(lockedUntil, bindSecurity.locks().stream()
                            .filter(lock -> dn.equals(lock.bindDn()))
                            .findFirst().orElseThrow().lockedUntil(),
                    "locked attempts must not extend the lock window");
            assertTrue(bindSecurity.unlock(dn));
            assertEquals(0, LdapAccountLock.count("bindDn", dn));
            try (LDAPConnection connection = new LDAPConnection("127.0.0.1", server.listenPort())) {
                connection.bind(dn, "CorrectPassword123!");
            }
        } finally {
            bindSecurity.unlock(dn);
            try { users.delete(uid); } catch (LDAPException ignored) { }
        }
    }

    @Inject
    LdapBindSecurityService bindSecurity;

    @Inject
    LdapBindAuditService auditService;

    @Inject
    LdapDirectoryStore directoryStore;

    @Test
    void isolatesPersistedDirectoriesByConfiguredBaseDn() throws Exception {
        String foreignDn = "dc=foreign,dc=example";
        String normalizedForeignDn = new DN(foreignDn).toNormalizedString();
        String uid = "base-scope-test";
        QuarkusTransaction.requiringNew().run(() -> {
            LdapDirectoryEntry row = new LdapDirectoryEntry();
            row.normalizedDn = normalizedForeignDn;
            row.dn = foreignDn;
            row.entryLdif = "dn: " + foreignDn + "\nobjectClass: top\nobjectClass: domain\ndc: foreign\n";
            row.updatedAt = OffsetDateTime.now();
            row.persist();
        });
        try {
            assertTrue(directoryStore.loadEntries().stream()
                    .noneMatch(entry -> foreignDn.equalsIgnoreCase(entry.getDN())));
            users.create(new DirectoryUserRequest(uid, "Base Scope", "Test", null, "ScopePassword123!"));
            long foreignRows = QuarkusTransaction.requiringNew().call(
                    () -> LdapDirectoryEntry.count("normalizedDn", normalizedForeignDn));
            assertEquals(1, foreignRows, "writes for one Base DN must preserve other directory trees");
        } finally {
            try { users.delete(uid); } catch (LDAPException ignored) { }
            QuarkusTransaction.requiringNew().run(
                    () -> LdapDirectoryEntry.delete("normalizedDn", normalizedForeignDn));
        }
    }

    @Test
    void pagesAndFiltersBindAudits() {
        String marker = "audit-filter-test-" + System.nanoTime();
        String dn = "uid=" + marker + ",ou=people," + config.baseDn();
        auditService.record(dn, "127.0.0.1", true, 0, "success");
        auditService.record(dn, "127.0.0.1", false, 49, "invalid_credentials");
        var page = auditService.search(0, 1, marker, false, null, null);
        assertEquals(1, page.getRecords().size());
        assertEquals(1, page.getTotal());
        assertEquals("invalid_credentials", page.getRecords().get(0).detail);
    }

    @Test
    void removesBindAuditsOlderThanRetentionWindow() {
        String marker = "audit-retention-test-" + System.nanoTime();
        auditService.record(marker, "127.0.0.1", false, 49, "expired");
        QuarkusTransaction.requiringNew().run(() -> {
            LdapBindAudit audit = LdapBindAudit.find("bindDn", marker).firstResult();
            audit.createdAt = LocalDateTime.now().minusDays(config.security().auditRetentionDays() + 1L);
        });
        auditService.deleteExpired();
        assertEquals(0, auditService.search(0, 10, marker, null, null, null).getTotal());
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("jade.ldap.server.listen-address", "127.0.0.1"),
                    Map.entry("jade.ldap.server.port", "0"),
                    Map.entry("jade.ldap.server.tls.enabled", "true"),
                    Map.entry("jade.ldap.server.tls.port", "0"),
                    Map.entry("jade.ldap.server.tls.self-signed", "true"),
                    Map.entry("jade.ldap.server.tls.start-tls-enabled", "true"),
                    Map.entry("jade.ldap.server.security.max-failed-binds", "3"),
                    Map.entry("jade.ldap.server.security.lock-seconds", "60"),
                    Map.entry("jade.ldap.server.base-dn", "dc=jade,dc=test"),
                    Map.entry("jade.ldap.server.admin-dn", "cn=admin,dc=jade,dc=test"),
                    Map.entry("jade.ldap.server.admin-password", "test-admin-password"),
                    Map.entry("jade.ldap.server.data-file", "target/test-directory.ldif"));
        }
    }
}
