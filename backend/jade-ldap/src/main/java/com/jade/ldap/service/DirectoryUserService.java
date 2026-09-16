package com.jade.ldap.service;

import com.jade.ldap.api.DirectoryUserRequest;
import com.jade.ldap.api.DirectoryUserView;
import com.jade.ldap.server.LdapDirectoryServer;
import com.jade.ldap.server.LdapServerConfig;
import com.jade.ldap.directory.DirectoryWriteCoordinator;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.RDN;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class DirectoryUserService {

    @Inject
    LdapDirectoryServer ldapServer;

    @Inject
    LdapServerConfig config;

    @Inject
    DirectoryWriteCoordinator writes;

    public List<DirectoryUserView> list() throws LDAPException {
        return ldapServer.directory()
                .search(peopleDn(), SearchScope.ONE, Filter.createEqualityFilter("objectClass", "inetOrgPerson"))
                .getSearchEntries()
                .stream()
                .map(this::toView)
                .sorted(Comparator.comparing(DirectoryUserView::uid))
                .toList();
    }

    public DirectoryUserView get(String uid) throws LDAPException {
        Entry entry = ldapServer.directory().getEntry(userDn(uid));
        if (entry == null) {
            throw new LDAPException(com.unboundid.ldap.sdk.ResultCode.NO_SUCH_OBJECT);
        }
        return toView(entry);
    }

    public DirectoryUserView create(DirectoryUserRequest request) throws LDAPException {
        validatePassword(request.password());
        Entry entry = new Entry(userDn(request.uid()));
        entry.addAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson");
        entry.addAttribute("uid", request.uid());
        entry.addAttribute("cn", request.commonName());
        entry.addAttribute("sn", request.surname());
        if (request.email() != null && !request.email().isBlank()) {
            entry.addAttribute("mail", request.email());
        }
        entry.addAttribute("userPassword", request.password());
        return writes.write(() -> {
            ldapServer.directory().add(entry);
            return toView(ldapServer.directory().getEntry(entry.getDN()));
        });
    }

    public void changePassword(String uid, String password) throws LDAPException {
        validatePassword(password);
        writes.write(() -> {
            ldapServer.directory().modify(userDn(uid),
                    new Modification(ModificationType.REPLACE, "userPassword", password));
            return null;
        });
    }

    public DirectoryUserView update(String uid, com.jade.ldap.api.DirectoryUserUpdateRequest request)
            throws LDAPException {
        String dn = userDn(uid);
        return writes.write(() -> {
            ldapServer.directory().modify(dn,
                    new Modification(ModificationType.REPLACE, "cn", request.commonName()),
                    new Modification(ModificationType.REPLACE, "sn", request.surname()),
                    request.email() == null || request.email().isBlank()
                            ? new Modification(ModificationType.REPLACE, "mail")
                            : new Modification(ModificationType.REPLACE, "mail", request.email()));
            return toView(ldapServer.directory().getEntry(dn));
        });
    }

    public void delete(String uid) throws LDAPException {
        writes.write(() -> {
            for (SearchResultEntry group : ldapServer.directory()
                    .search("ou=groups," + config.baseDn(), SearchScope.ONE,
                            Filter.createEqualityFilter("roleOccupant", userDn(uid)))
                    .getSearchEntries()) {
                ldapServer.directory().modify(group.getDN(),
                        new Modification(ModificationType.DELETE, "roleOccupant", userDn(uid)));
            }
            ldapServer.directory().delete(userDn(uid));
            return null;
        });
    }

    private DirectoryUserView toView(SearchResultEntry entry) {
        return new DirectoryUserView(
                entry.getDN(),
                entry.getAttributeValue("uid"),
                entry.getAttributeValue("cn"),
                entry.getAttributeValue("sn"),
                entry.getAttributeValue("mail"));
    }

    private DirectoryUserView toView(Entry entry) {
        return new DirectoryUserView(
                entry.getDN(),
                entry.getAttributeValue("uid"),
                entry.getAttributeValue("cn"),
                entry.getAttributeValue("sn"),
                entry.getAttributeValue("mail"));
    }

    private String peopleDn() {
        return "ou=people," + config.baseDn();
    }

    public String userDn(String uid) throws LDAPException {
        validateUid(uid);
        return new RDN("uid", uid).toString() + "," + peopleDn();
    }

    private void validateUid(String uid) {
        if (uid == null || !uid.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("uid 格式不合法");
        }
    }

    private void validatePassword(String password) {
        int minLength = config.security().passwordMinLength();
        if (password == null || password.length() < minLength
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("密码至少 " + minLength + " 位，且必须包含大小写字母、数字和特殊字符");
        }
    }
}
