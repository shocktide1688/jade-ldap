package com.jade.ldap.server;

import com.jade.ldap.directory.LdapDirectoryStore;
import com.unboundid.ldap.listener.Base64PasswordEncoderOutputFormatter;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.SaltedMessageDigestInMemoryPasswordEncoder;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.RDN;
import com.unboundid.ldap.listener.SelfSignedCertificateGenerator;
import com.unboundid.util.ObjectPair;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.GeneralSecurityException;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LdapDirectoryServer {

    private static final Logger LOG = Logger.getLogger(LdapDirectoryServer.class);

    @Inject
    LdapServerConfig config;

    @Inject
    LdapBindSecurityService bindSecurity;

    @Inject
    LdapDirectoryStore directoryStore;

    private final AtomicBoolean persistScheduled = new AtomicBoolean();
    private ScheduledExecutorService syncExecutor;
    private ScheduledExecutorService certificateExecutor;
    private volatile InMemoryDirectoryServer server;
    private volatile Throwable persistenceFailure;
    private volatile long databaseRevision = -1;
    private volatile long certificateModifiedAt = -1;

    void start(@Observes StartupEvent ignored) {
        if (!config.enabled()) {
            LOG.info("LDAP protocol listener is disabled");
            return;
        }
        try {
            syncExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> daemonThread(runnable, "jade-ldap-db-sync"));
            certificateExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> daemonThread(runnable, "jade-ldap-certificate-watch"));
            startServer();
            syncExecutor.scheduleWithFixedDelay(this::synchronizeIfChanged,
                    config.storage().syncIntervalMillis(), config.storage().syncIntervalMillis(), TimeUnit.MILLISECONDS);
            if (!config.tls().selfSigned() && (config.tls().enabled() || config.tls().startTlsEnabled())) {
                certificateModifiedAt = certificateLastModified();
                certificateExecutor.scheduleWithFixedDelay(this::reloadCertificateIfChanged,
                        config.tls().certificateReloadSeconds(), config.tls().certificateReloadSeconds(), TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start Jade LDAP server", e);
        }
    }

    void stop(@Observes ShutdownEvent ignored) {
        InMemoryDirectoryServer current = server;
        if (current != null) {
            current.shutDown(true);
            server = null;
        }
        if (syncExecutor != null) syncExecutor.shutdown();
        if (certificateExecutor != null) certificateExecutor.shutdown();
    }

    public InMemoryDirectoryServer directory() {
        InMemoryDirectoryServer current = server;
        if (current == null) {
            throw new IllegalStateException("LDAP server is not running");
        }
        return current;
    }

    public boolean isRunning() {
        return server != null;
    }

    public int listenPort() {
        return isRunning() ? server.getListenPort("ldap") : -1;
    }

    public int ldapsPort() {
        return isRunning() && config.tls().enabled() ? server.getListenPort("ldaps") : -1;
    }

    public Throwable persistenceFailure() {
        return persistenceFailure;
    }

    private void startServer() throws Exception {
        validateConfig();

        InMemoryDirectoryServerConfig serverConfig = new InMemoryDirectoryServerConfig(config.baseDn());
        SSLUtil ssl = null;
        if (config.tls().enabled() || config.tls().startTlsEnabled()) {
            ObjectPair<File, char[]> generated = null;
            File keyStore;
            char[] password;
            if (config.tls().selfSigned()) {
                generated = SelfSignedCertificateGenerator.generateTemporarySelfSignedCertificate(
                        "CN=Jade LDAP", "JKS");
                keyStore = generated.getFirst();
                password = generated.getSecond();
            } else {
                keyStore = Path.of(config.tls().keyStorePath().orElseThrow(
                        () -> new IllegalArgumentException("LDAPS key store path is required"))).toFile();
                password = config.tls().keyStorePassword().orElseThrow(
                        () -> new IllegalArgumentException("LDAPS key store password is required")).toCharArray();
            }
            KeyStoreKeyManager keyManager = new KeyStoreKeyManager(keyStore, password);
            ssl = new SSLUtil(keyManager, new TrustAllTrustManager());
        }
        InMemoryListenerConfig ldap = InMemoryListenerConfig.createLDAPConfig(
                "ldap", InetAddress.getByName(config.listenAddress()), config.port(),
                config.tls().startTlsEnabled() ? ssl.createSSLSocketFactory() : null);
        if (config.tls().enabled()) {
            InMemoryListenerConfig ldaps = InMemoryListenerConfig.createLDAPSConfig(
                    "ldaps", InetAddress.getByName(config.listenAddress()), config.tls().port(),
                    ssl.createSSLServerSocketFactory(), ssl.createSSLSocketFactory());
            serverConfig.setListenerConfigs(ldap, ldaps);
        } else {
            serverConfig.setListenerConfigs(ldap);
        }
        serverConfig.addAdditionalBindCredentials(config.adminDn(), config.adminPassword());
        // Directory writes are exposed through the audited Jade admin API.
        // LDAP clients authenticate and read the directory, but cannot mutate it directly.
        serverConfig.setAuthenticationRequiredOperationTypes(
                com.unboundid.ldap.sdk.OperationType.COMPARE,
                com.unboundid.ldap.sdk.OperationType.SEARCH);
        serverConfig.setMaxConnections(config.maxConnections());
        serverConfig.setVendorName("Jade LDAP");
        serverConfig.setVendorVersion("1.0.0");
        serverConfig.setEqualityIndexAttributes("objectClass", "uid", "mail", "member", "uniqueMember", "roleOccupant");
        serverConfig.setPasswordAttributes("userPassword");
        serverConfig.setPasswordEncoders(
                new SaltedMessageDigestInMemoryPasswordEncoder(
                        "{SSHA256}",
                        Base64PasswordEncoderOutputFormatter.getInstance(),
                        MessageDigest.getInstance("SHA-256"),
                        16,
                        true,
                        false));
        serverConfig.addInMemoryOperationInterceptor(new ReadOnlyProtocolInterceptor());
        serverConfig.addInMemoryOperationInterceptor(new BindSecurityInterceptor(bindSecurity));

        server = new InMemoryDirectoryServer(serverConfig);
        List<Entry> persisted = directoryStore.loadEntries();
        if (!persisted.isEmpty()) {
            server.addEntries(persisted);
        } else {
            Path dataFile = dataFile();
            if (Files.isRegularFile(dataFile) && Files.size(dataFile) > 0) {
                server.importFromLDIF(true, dataFile.toFile());
                LOG.infof("Migrating legacy LDIF directory %s into PostgreSQL", dataFile);
            } else {
                initializeDirectoryTree(server);
            }
            directoryStore.initializeIfEmpty(snapshotEntries());
            reloadFromDatabase();
        }
        databaseRevision = directoryStore.revision();
        server.startListening();
        LOG.infof("Jade LDAP listening on ldap://%s:%d with base DN %s",
                config.listenAddress(), server.getListenPort("ldap"), config.baseDn());
        if (config.tls().enabled()) {
            LOG.infof("Jade LDAP TLS listening on ldaps://%s:%d", config.listenAddress(), server.getListenPort("ldaps"));
        }
    }

    private void validateConfig() throws LDAPException {
        new DN(config.baseDn());
        new DN(config.adminDn());
        if (config.adminPassword() == null || config.adminPassword().length() < 12) {
            throw new IllegalArgumentException("jade.ldap.server.admin-password must contain at least 12 characters");
        }
        if (config.port() < 0 || config.port() > 65535) {
            throw new IllegalArgumentException("jade.ldap.server.port must be between 0 and 65535");
        }
        if (config.tls().port() < 0 || config.tls().port() > 65535) {
            throw new IllegalArgumentException("jade.ldap.server.tls.port must be between 0 and 65535");
        }
    }

    private void initializeDirectoryTree(InMemoryDirectoryServer directoryServer) throws LDAPException {
        DN base = new DN(config.baseDn());
        RDN rootRdn = base.getRDN();
        String attributeName = rootRdn.getAttributeNames()[0];
        String attributeValue = rootRdn.getAttributeValues()[0];

        Entry root = new Entry(config.baseDn());
        root.addAttribute("objectClass", "top", "domain");
        root.addAttribute(attributeName, attributeValue);

        Entry people = new Entry("ou=people," + config.baseDn());
        people.addAttribute("objectClass", "top", "organizationalUnit");
        people.addAttribute("ou", "people");

        Entry groups = new Entry("ou=groups," + config.baseDn());
        groups.addAttribute("objectClass", "top", "organizationalUnit");
        groups.addAttribute("ou", "groups");

        directoryServer.addEntries(root, people, groups);
    }

    private void requestPersist() {
        // Kept for binary compatibility with older callers. Database writes are coordinated
        // synchronously by DirectoryWriteCoordinator.
    }

    public synchronized void reloadFromDatabase() {
        InMemoryDirectoryServer current = server;
        if (current == null) return;
        List<Entry> entries = directoryStore.loadEntries();
        if (entries.isEmpty()) return;
        try {
            current.clear();
            current.addEntries(entries);
            databaseRevision = directoryStore.revision();
            persistenceFailure = null;
        } catch (Exception e) {
            persistenceFailure = e;
            throw new IllegalStateException("Failed to reload LDAP directory from PostgreSQL", e);
        }
    }

    public synchronized List<Entry> snapshotEntries() throws LDAPException {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : directory().search(config.baseDn(), com.unboundid.ldap.sdk.SearchScope.SUB,
                "(objectClass=*)").getSearchEntries()) {
            entries.add(new Entry(entry.getDN(), entry.getAttributes()));
        }
        return entries;
    }

    public void markDatabaseRevision(long revision) {
        databaseRevision = revision;
    }

    public synchronized void reloadCertificate() {
        try {
            InMemoryDirectoryServer current = server;
            if (current != null) current.shutDown(true);
            server = null;
            startServer();
            certificateModifiedAt = certificateLastModified();
            LOG.info("LDAP TLS certificate reloaded");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to reload LDAP TLS certificate", e);
        }
    }

    private void synchronizeIfChanged() {
        try {
            long currentRevision = directoryStore.revision();
            // A local write records its next revision before the surrounding transaction commits.
            // During that short window another transaction can still observe the previous revision;
            // reloading on any inequality would restore stale data over the just-completed local write.
            // Revisions are monotonic, so only a strictly newer committed revision is reload-worthy.
            if (currentRevision > databaseRevision) reloadFromDatabase();
        } catch (Exception e) {
            persistenceFailure = e;
            LOG.error("Failed to synchronize LDAP directory from PostgreSQL", e);
        }
    }

    private void reloadCertificateIfChanged() {
        long modified = certificateLastModified();
        if (modified > 0 && modified != certificateModifiedAt) reloadCertificate();
    }

    private long certificateLastModified() {
        return config.tls().keyStorePath().map(path -> {
            try { return Files.getLastModifiedTime(Path.of(path)).toMillis(); }
            catch (IOException ignored) { return -1L; }
        }).orElse(-1L);
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    /** @deprecated directory state is persisted transactionally by DirectoryWriteCoordinator. */
    @Deprecated
    public void persistNow() { }

    private Path dataFile() {
        return Path.of(config.dataFile()).toAbsolutePath().normalize();
    }
}
