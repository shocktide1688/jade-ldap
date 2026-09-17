package com.jade.ldap.server;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "jade.ldap.server")
public interface LdapServerConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("0.0.0.0")
    String listenAddress();

    @WithDefault("1389")
    int port();

    @WithDefault("dc=jade,dc=local")
    String baseDn();

    @WithDefault("cn=admin,dc=jade,dc=local")
    String adminDn();

    String adminPassword();

    @WithDefault("jade-ldap/data/directory.ldif")
    String dataFile();

    @WithDefault("500")
    int maxConnections();

    TlsConfig tls();

    SecurityConfig security();

    StorageConfig storage();

    interface TlsConfig {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("1636")
        int port();

        Optional<String> keyStorePath();

        Optional<String> keyStorePassword();

        @WithDefault("false")
        boolean selfSigned();

        @WithDefault("false")
        boolean startTlsEnabled();

        @WithDefault("30")
        long certificateReloadSeconds();
    }

    interface SecurityConfig {
        @WithDefault("5")
        int maxFailedBinds();

        @WithDefault("300")
        long lockSeconds();

        @WithDefault("12")
        int passwordMinLength();

        @WithDefault("300")
        long failureWindowSeconds();

        @WithDefault("90")
        int auditRetentionDays();
    }

    interface StorageConfig {
        @WithDefault("1000")
        long syncIntervalMillis();
    }
}
