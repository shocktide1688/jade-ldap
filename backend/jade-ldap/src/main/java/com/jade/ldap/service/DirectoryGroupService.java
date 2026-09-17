package com.jade.ldap.service;

import com.jade.ldap.api.DirectoryGroupRequest;
import com.jade.ldap.api.DirectoryGroupUpdateRequest;
import com.jade.ldap.api.DirectoryGroupView;
import com.jade.ldap.server.LdapDirectoryServer;
import com.jade.ldap.server.LdapServerConfig;
import com.jade.ldap.directory.DirectoryWriteCoordinator;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.RDN;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class DirectoryGroupService {

    @Inject
    LdapDirectoryServer ldapServer;

    @Inject
    LdapServerConfig config;

    @Inject
    DirectoryUserService users;

    @Inject
    DirectoryWriteCoordinator writes;

    public List<DirectoryGroupView> list() throws LDAPException {
        return ldapServer.directory()
                .search(groupsDn(), SearchScope.ONE, Filter.createEqualityFilter("objectClass", "organizationalRole"))
                .getSearchEntries().stream()
                .map(this::toView)
                .sorted(Comparator.comparing(DirectoryGroupView::name))
                .toList();
    }

    public DirectoryGroupView get(String name) throws LDAPException {
        Entry entry = requireGroup(name);
        return toView(entry);
    }

    public DirectoryGroupView create(DirectoryGroupRequest request) throws LDAPException {
        Entry entry = new Entry(groupDn(request.name()));
        entry.addAttribute("objectClass", "top", "organizationalRole");
        entry.addAttribute("cn", request.name());
        if (request.description() != null && !request.description().isBlank()) {
            entry.addAttribute("description", request.description());
        }
        return writes.write(() -> {
            ldapServer.directory().add(entry);
            return toView(ldapServer.directory().getEntry(entry.getDN()));
        });
    }

    public DirectoryGroupView update(String name, DirectoryGroupUpdateRequest request) throws LDAPException {
        String dn = groupDn(name);
        return writes.write(() -> {
            requireGroup(name);
            Modification description = request.description() == null || request.description().isBlank()
                    ? new Modification(ModificationType.REPLACE, "description")
                    : new Modification(ModificationType.REPLACE, "description", request.description());
            ldapServer.directory().modify(dn, description);
            return toView(ldapServer.directory().getEntry(dn));
        });
    }

    public void addMember(String name, String uid) throws LDAPException {
        writes.write(() -> {
            requireGroup(name);
            users.get(uid);
            Entry group = ldapServer.directory().getEntry(groupDn(name));
            String userDn = users.userDn(uid);
            if (!group.hasAttributeValue("roleOccupant", userDn)) {
                ldapServer.directory().modify(group.getDN(),
                        new Modification(ModificationType.ADD, "roleOccupant", userDn));
            }
            return null;
        });
    }

    public void removeMember(String name, String uid) throws LDAPException {
        writes.write(() -> {
            Entry group = requireGroup(name);
            String userDn = users.userDn(uid);
            if (group.hasAttributeValue("roleOccupant", userDn)) {
                ldapServer.directory().modify(group.getDN(),
                        new Modification(ModificationType.DELETE, "roleOccupant", userDn));
            }
            return null;
        });
    }

    public void delete(String name) throws LDAPException {
        writes.write(() -> {
            ldapServer.directory().delete(groupDn(name));
            return null;
        });
    }

    private Entry requireGroup(String name) throws LDAPException {
        Entry entry = ldapServer.directory().getEntry(groupDn(name));
        if (entry == null) {
            throw new LDAPException(ResultCode.NO_SUCH_OBJECT);
        }
        return entry;
    }

    private DirectoryGroupView toView(SearchResultEntry entry) {
        return view(entry);
    }

    private DirectoryGroupView toView(Entry entry) {
        return view(entry);
    }

    private DirectoryGroupView view(Entry entry) {
        String[] values = entry.getAttributeValues("roleOccupant");
        List<String> members = values == null ? List.of() : java.util.Arrays.stream(values)
                .map(this::uidFromDn).sorted().toList();
        return new DirectoryGroupView(entry.getDN(), entry.getAttributeValue("cn"),
                entry.getAttributeValue("description"), members);
    }

    private String groupsDn() {
        return "ou=groups," + config.baseDn();
    }

    private String groupDn(String name) throws LDAPException {
        validateName(name);
        return new RDN("cn", name) + "," + groupsDn();
    }

    private void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("用户组名称格式不合法");
        }
    }

    private String uidFromDn(String dn) {
        try {
            return new com.unboundid.ldap.sdk.DN(dn).getRDN().getAttributeValues()[0];
        } catch (LDAPException e) {
            return dn;
        }
    }
}
