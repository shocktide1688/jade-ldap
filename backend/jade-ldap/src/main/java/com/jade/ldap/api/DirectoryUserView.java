package com.jade.ldap.api;

public record DirectoryUserView(
        String dn,
        String uid,
        String commonName,
        String surname,
        String email
) {
}
