package com.jade.ldap.service;

import com.jade.ldap.api.DirectoryTreeNode;
import com.jade.ldap.server.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class DirectoryTreeService {

    @Inject
    LdapServerConfig config;

    @Inject
    DirectoryUserService users;

    @Inject
    DirectoryGroupService groups;

    public DirectoryTreeNode tree() throws LDAPException {
        String peopleDn = "ou=people," + config.baseDn();
        String groupsDn = "ou=groups," + config.baseDn();
        List<DirectoryTreeNode> people = users.list().stream()
                .map(user -> new DirectoryTreeNode("USER", user.dn(), user.uid(), List.of()))
                .toList();
        List<DirectoryTreeNode> groupNodes = groups.list().stream()
                .map(group -> new DirectoryTreeNode("GROUP", group.dn(), group.name(), List.of()))
                .toList();
        return new DirectoryTreeNode("DOMAIN", config.baseDn(), config.baseDn(), List.of(
                new DirectoryTreeNode("OU", peopleDn, "people", people),
                new DirectoryTreeNode("OU", groupsDn, "groups", groupNodes)));
    }
}
